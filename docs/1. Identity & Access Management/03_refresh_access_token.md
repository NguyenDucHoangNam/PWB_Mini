# 03. Gia hạn Phiên Đăng nhập (Refresh Access Token)

Tài liệu đặc tả A-Z tính năng Gia hạn Phiên đăng nhập (Silent Refresh) cho hệ thống PWB MiNi, sử dụng cơ chế quay vòng mã xác thực (Refresh Token Rotation - RTR) cùng khoảng thời gian ân hạn (Grace Period) để tối ưu hóa trải nghiệm người dùng và ngăn chặn tấn công chiếm đoạt phiên (Session Hijacking).

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Người dùng đã đăng nhập (sở hữu Access Token đã hết hạn và Refresh Token còn hiệu lực trong Secure Cookie).
*   **Quy trình tóm tắt**:
    1.  Access Token (JWT) có thời hạn sống ngắn (15 phút) hết hiệu lực, khiến các API request gửi đi bị từ chối với mã HTTP `401 Unauthorized`.
    2.  Ứng dụng Frontend âm thầm gửi yêu cầu gia hạn phiên (`POST /api/v1/auth/refresh`) đính kèm Refresh Token lấy từ Cookie bảo mật.
    3.  Backend kiểm tra tính hợp lệ của Refresh Token, sinh cặp mã mới, thu hồi mã cũ (Rotation) và phản hồi lại cho Client.
    4.  Frontend nhận Access Token mới cập nhật vào bộ nhớ Zustand, tiếp tục thực hiện lại các API request bị gián đoạn trước đó mà người dùng không nhận biết bất kỳ sự ngắt quãng nào (Silent Refresh).

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Quay vòng mã xác thực (Refresh Token Rotation - RTR)
Để giảm thiểu rủi ro khi Refresh Token bị lộ lọt, hệ thống áp dụng cơ chế RTR:
*   Mỗi khi người dùng sử dụng Refresh Token để lấy một Access Token mới, **Refresh Token cũ sẽ lập tức bị hủy bỏ và vô hiệu hóa**.
*   Backend sinh một Refresh Token mới thay thế và gửi lại cho Client thông qua header `Set-Cookie`.

#### B. Cơ chế Ân hạn (Grace Period - Shadow Key)
Trên các mạng di động không ổn định hoặc khi client gọi nhiều API song song lúc token vừa hết hạn, có thể xảy ra tình trạng nhiều request Refresh đồng thời (Race Condition):
*   Khi nhận được yêu cầu xoay vòng Refresh Token hợp lệ đầu tiên, Backend thực hiện:
    *   Sinh cặp Token mới.
    *   Chuyển Refresh Token cũ sang trạng thái **Shadow Key** lưu trên Redis key `session:refresh_token:shadow:{oldToken}` với giá trị là token mới (`newToken`) và thời gian sống rất ngắn (**TTL 10 giây**).
*   Trong vòng 10 giây ân hạn này, nếu các request tiếp theo gửi lên sử dụng chính Refresh Token cũ đó, Backend tìm thấy Shadow Key và sẽ **trả về cùng kết quả của cặp Token mới đã sinh ở request đầu tiên** thay vì chặn lỗi hoặc kích hoạt cảnh báo trộm Token.

#### C. Phát hiện Chiếm đoạt mã (Token Theft Detection) & Danh sách đen (Revoked Token Blacklist)
Để giải quyết bài toán "Bóng ma UUID" (khi khóa shadow hết TTL 10 giây và tự động bị Redis xóa, hệ thống sẽ mất dấu vết của UUID cũ và không thể truy vết được userId để hủy phiên khi bị tấn công phát lại), hệ thống áp dụng cơ chế **Danh sách đen lưu vết**:
*   Khi thời gian ân hạn 10 giây kết thúc (hoặc ngay khi thực hiện xoay vòng), token cũ sẽ được chuyển sang key lưu vết **`session:refresh_token:revoked:{oldToken}`** trên Redis với giá trị là `userId` của chủ sở hữu và thiết lập TTL bằng thời gian sống còn lại của token gốc (ví dụ: 7 ngày).
*   **Thứ tự ưu tiên kiểm tra (Order of Operations)**: Khi một Refresh Token gửi lên không nằm trong danh sách Active, Backend **bắt buộc** phải tra cứu key `session:refresh_token:shadow:{oldToken}` trước. Chỉ khi key Shadow này không tồn tại (Cache Miss), hệ thống mới được phép chuyển sang kiểm tra key `session:refresh_token:revoked:{oldToken}` để kích hoạt luồng Token Theft. Điều này ngăn ngừa việc vô tình kích hoạt thu hồi khẩn cấp cho người dùng hợp lệ đang gặp Race Condition mạng.
*   Nếu hệ thống nhận được yêu cầu sử dụng một Refresh Token không tồn tại trong danh sách hoạt động, và sau khi kiểm tra phát hiện cache miss đối với Shadow Key, Backend sẽ kiểm tra danh sách revoked:
    *   Nếu tìm thấy khóa **`session:refresh_token:revoked:{oldToken}`** trên Redis, Backend lấy được ngay `userId` tương ứng.
    *   **Hủy phiên triệt để (Atomic Multi-Delete)**: Hệ thống lập tức kích hoạt cảnh báo **Token Theft**. Để bảo vệ tài khoản, Backend phải:
        1. Đọc toàn bộ danh sách các token phiên (UUID) đang lưu trong ZSet `user:sessions:{userId}` của người dùng ra bộ nhớ.
        2. Thực hiện lệnh xóa hàng loạt (Multi-Key Delete) hoặc đóng gói vào một Redis Transaction/Lua Script để xóa sạch tất cả các String key `session:refresh_token:{token}` tương ứng của các thiết bị khác.
        3. Xóa chính ZSet `user:sessions:{userId}` để thu hồi phiên hoạt động hoàn toàn trên toàn bộ hệ thống.

---

### 1.3. Quy tắc Xác thực Dữ liệu & Chống CSRF (Validation & CSRF Protection)
*   **Bằng chứng kép (Dual-Token Validation)**: Để loại bỏ triệt để nguy cơ tấn công giả mạo yêu cầu chéo trang (CSRF) đối với API `/refresh` dựa trên Cookie HttpOnly, hệ thống yêu cầu Frontend phải gửi kèm **Access Token cũ (đã hết hạn)** trong HTTP Header `Authorization: Bearer <expired_accessToken>`.
*   **Xác minh kép & Bảo mật Chữ ký (Signature Validation)**: Backend giải mã Access Token cũ để trích xuất `userId` và so khớp với `userId` liên kết với `refreshToken` trong Redis.
    *   *⚠️ Ràng buộc xác thực JWT*: Hệ thống **chỉ bỏ qua duy nhất ngoại lệ `ExpiredJwtException`** (hết hạn thời gian sống của token). Tất cả các quy trình xác thực mật mã học khác của JWT — bao gồm **Chữ ký số (Signature Verification)**, cấu trúc Header, thuật toán mã hóa (chặn thuật toán `none` và các khóa đối xứng giả mạo) — **bắt buộc phải được xác thực hợp lệ tuyệt đối** so với Secret Key của hệ thống nhằm loại bỏ hoàn toàn nguy cơ tấn công giả mạo token (Fake Token Bypass). Nếu thông tin không đồng bộ hoặc thiếu Header Authorization, API lập tức trả về lỗi HTTP 401 Unauthorized.
*   **Request Body**: API `/refresh` **không yêu cầu** bất kỳ dữ liệu nào trong JSON request body (body là `{}`).

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/auth/refresh` | **20 requests / phút / IP** | Ngăn chặn tấn công DDoS làm nghẽn hoặc cạn kiệt tài nguyên tạo JWT |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Gia hạn Phiên thông thường (Silent Refresh & Rotation)

```mermaid
sequenceDiagram
    autonumber
    actor User as Client User
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL

    User->>FE: Thực hiện thao tác yêu cầu gọi API bảo mật
    FE->>BE: GET /api/v1/users/me (Kèm Access Token hết hạn)
    BE->>BE: Xác thực Access Token thất bại (Expired JWT)
    BE-->>FE: HTTP 401 Unauthorized (JWT_EXPIRED)
    
    Note over FE: Axios Interceptor tạm dừng các API requests khác
    FE->>BE: POST /api/v1/auth/refresh (Header Bearer expired_token, Cookie refreshToken)
    
    BE->>BE: Giải mã Bearer Token hết hạn để lấy userId
    BE->>Redis: Kiểm tra key lockout 'login_lockout:{userId}' (nếu đang bị khóa)
    BE->>Redis: Kiểm tra sự tồn tại của key 'session:refresh_token:{token}'
    
    alt Token hợp lệ, tồn tại và khớp với userId từ Access Token
        BE->>Redis: Lấy userId liên kết với token
        BE->>DB: Kiểm tra trạng thái User (ACTIVE hoặc PENDING_DELETION)
        
        alt Trạng thái User là BANNED
            BE-->>FE: HTTP 400 Bad Request (ACCOUNT_BANNED)
        else Trạng thái hợp lệ
            BE->>BE: Sinh Access Token (JWT) & Refresh Token (UUID) mới
            
            BE->>Redis: Lưu token cũ làm Shadow Key 'session:refresh_token:shadow:{oldToken}' = newToken (TTL: 10s)
            BE->>Redis: Lưu token cũ vào Blacklist 'session:refresh_token:revoked:{oldToken}' = userId (TTL: 7 ngày)
            BE->>Redis: Xóa key cũ 'session:refresh_token:{oldToken}'
            
            BE->>Redis: Lưu Refresh Token mới 'session:refresh_token:{newToken}' (TTL: 7 ngày)
            BE->>Redis: Cập nhật ZSet 'user:sessions:{userId}' (Xóa oldToken, thêm newToken)
            
            BE-->>FE: HTTP 200 OK (Access Token mới trong body, Refresh Token mới qua Set-Cookie HttpOnly)
            
            Note over FE: Cập nhật Access Token mới vào Zustand Store
            FE->>BE: Thử lại request bị lỗi: GET /api/v1/users/me (Access Token mới)
            BE-->>FE: HTTP 200 OK (Trả về dữ liệu profile)
            FE-->>User: Hiển thị giao diện mượt mà không bị gián đoạn
        end
    end
```

##### 📝 Mô tả chi tiết các bước xử lý (Gia hạn phiên bình thường):
1.  **Nhận lỗi 401**: Frontend gửi request đính kèm Access Token đã hết hạn, Backend trả về mã lỗi HTTP 401 Unauthorized kèm mã lỗi nghiệp vụ `JWT_EXPIRED`.
2.  **Kích hoạt Interceptor**: Axios Interceptor bắt lỗi 401, tạm dừng các request đang chờ xử lý, tự động gửi yêu cầu `POST /api/v1/auth/refresh` (header `Authorization: Bearer <expired_token>`, cookie `refreshToken` đính kèm tự động).
3.  **Xác thực hai lớp (Dual-Token Verification)**: Backend giải mã Access Token hết hạn để lấy `userId`, sau đó tìm kiếm key `session:refresh_token:{oldToken}` trên Redis để đối khớp `userId`. Nếu trùng khớp và token hợp lệ, tiếp tục xử lý.
4.  **Xoay vòng Token (Rotation)**:
    *   Tạo cặp token mới: Access Token và Refresh Token.
    *   Lưu token cũ thành Shadow Key `session:refresh_token:shadow:{oldToken}` (giá trị là `newToken`) với TTL 10 giây.
    *   Lưu token cũ vào danh sách đen `session:refresh_token:revoked:{oldToken}` (giá trị là `userId`) với TTL bằng thời gian sống còn lại của token gốc (ví dụ: 7 ngày).
    *   Xóa token cũ `session:refresh_token:{oldToken}` ra khỏi Redis.
    *   Lưu Refresh Token mới vào Redis và cập nhật ZSet `user:sessions:{userId}` của người dùng (xóa `oldToken` và thêm `newToken`).
5.  **Trả kết quả**: Trả Access Token mới trong JSON body và Refresh Token mới qua Cookie HttpOnly.
6.  **Thử lại request lỗi**: Frontend nhận Access Token mới, cập nhật vào Zustand Store, đính kèm token mới này vào request ban đầu và gửi lại để hoàn tất yêu cầu của người dùng.

---

### 2.2. Luồng Xử lý Race Condition & Cảnh báo trộm Token (Token Theft)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend Client
    participant Attacker as Kẻ tấn công (Có token cũ)
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache

    Note over FE, BE: --- Kịch bản 1: Mạng chập chờn gây Race Condition (Grace Period) ---
    FE->>BE: [Request 1] POST /refresh (RefreshToken_A)
    BE->>BE: Xoay vòng thành công, cấp RefreshToken_B
    BE->>Redis: Set Shadow Key 'shadow:RefreshToken_A' = RefreshToken_B (TTL: 10s)
    BE->>Redis: Set Revoked Key 'revoked:RefreshToken_A' = userId (TTL: 7 ngày)
    
    FE->>BE: [Request 2 gửi song song] POST /refresh (RefreshToken_A)
    BE->>Redis: Tìm key 'shadow:RefreshToken_A' trước (Ưu tiên Shadow)
    BE->>Redis: Thấy 'shadow:RefreshToken_A' -> Cấp lại RefreshToken_B & AccessToken mới
    Note over FE: Không bị đá phiên, hệ thống hoạt động bình thường
    
    Note over Attacker, BE: --- Kịch bản 2: Tấn công phát lại / Trộm Token (Token Theft) ---
    Attacker->>BE: POST /refresh (RefreshToken_A) (Đã hết 10s ân hạn)
    BE->>Redis: Tìm 'session:refresh_token:RefreshToken_A' -> Không có
    BE->>Redis: Tìm 'shadow:RefreshToken_A' trước -> Cache Miss (Đã hết hạn tự hủy)
    BE->>Redis: Tìm 'revoked:RefreshToken_A' sau -> Thấy chứa userId
    
    Note over BE: Phát hiện hành vi sử dụng lại token cũ đã bị thu hồi (Token Theft)
    BE->>Redis: Đọc toàn bộ Refresh Token UUID từ ZSet 'user:sessions:{userId}'
    BE->>Redis: Xóa hàng loạt String key 'session:refresh_token:{token}' tương ứng (Atomic Multi-Delete)
    BE->>Redis: Xóa ZSet 'user:sessions:{userId}'
    
    BE-->>Attacker: HTTP 401 Unauthorized (TOKEN_THEFT_DETECTED)
    Note over FE: Phiên hoạt động của người dùng hợp lệ cũng bị logout ngay lập tức để bảo mật tài khoản
```

##### 📝 Mô tả chi tiết xử lý Race Condition & Token Theft:
1.  **Race Condition (Trong vòng 10 giây)**: Khi có 2 request gửi đồng thời dùng chung `RefreshToken_A`. Request 1 xử lý trước, sinh ra `RefreshToken_B` và tạo Shadow Key `shadow:RefreshToken_A` liên kết tới `RefreshToken_B` (TTL 10s). Request 2 đến ngay sau đó, Backend tìm thấy khóa Shadow này sẽ trả lại luôn dữ liệu của `RefreshToken_B` cho Client mà không kích hoạt thu hồi.
2.  **Tấn công phát lại (Sau 10 giây)**: Kẻ tấn công lấy được `RefreshToken_A` và gửi yêu cầu gia hạn sau khi 10 giây ân hạn đã kết thúc. Backend kiểm tra không thấy `RefreshToken_A` ở cả hai bảng khóa hoạt động và khóa Shadow. Hệ thống xác định xảy ra việc lộ lọt token (Token Theft).
3.  **Hủy phiên hàng loạt**: Để bảo vệ tài khoản, Backend lập tức truy tìm User ID của mã bị lộ, xóa toàn bộ các session hoạt động khác của người dùng này trên Redis (ZSet và String keys).
4.  **Từ chối truy cập**: Trả về lỗi HTTP 401 Unauthorized (`TOKEN_THEFT_DETECTED`), buộc kẻ tấn công và cả người dùng thật (trên thiết bị hợp lệ) phải thực hiện đăng nhập lại bằng Username/Password để xác thực lại danh tính.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu)

### 3.1. Cấu trúc dữ liệu Redis (Cache & Limits)

Các khóa Redis phục vụ cho tính năng Silent Refresh & RTR:

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `session:refresh_token:{token}` | `String` | `userId` | **7 ngày** | Quản lý phiên hoạt động của Refresh Token còn hiệu lực. |
| `session:refresh_token:shadow:{oldToken}` | `String` | `newToken` (UUID) | **10 giây** | Shadow Key phục vụ thời gian ân hạn (Grace Period) cho Race Condition. |
| `session:refresh_token:revoked:{oldToken}` | `String` | `userId` | **7 ngày** (hoặc thời gian sống còn lại của token cũ) | Revoked Key (Danh sách đen lưu vết) để lưu thông tin userId của token đã xoay vòng, hỗ trợ phát hiện trộm token (Token Theft) sau thời gian ân hạn. |
| `user:sessions:{userId}` | `ZSet` | `token` (UUID) với Score là `timestamp` | **7 ngày** | Danh sách các token phiên đang hoạt động của người dùng để kiểm soát giới hạn tối đa 3 phiên hoạt động. |

> **⚡ Lưu ý Kỹ thuật: Atomic Redis Operations**
>
> Việc xoay vòng Refresh Token (xóa token cũ, tạo token mới, ghi nhận shadow key, lưu revoked key và cập nhật ZSet phiên) **BẮT BUỘC** phải được đóng gói trong một **Redis Pipeline**, **Redis Transaction (`MULTI/EXEC`)** hoặc **Redis Lua Script** để đảm bảo tính toàn vẹn dữ liệu và an toàn trước mọi race condition.

---

## 🔌 4. API Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1/auth`
*   **Headers**: 
    *   `Authorization: Bearer <expired_token>` (Access Token cũ đã hết hạn để chống CSRF)
    *   `Cookie: refreshToken=<token>` (trình duyệt tự động đính kèm)
*   **Format Phản Hồi**: Hệ thống đồng nhất sử dụng cấu trúc `ApiResponse<T>` chuẩn hóa theo quy ước dự án:
    *   **Thành công**: Trả về Http Code thích hợp cùng JSON body chứa `success: true`, `message`, `data` (payload kết quả), `errors: null` và `timestamp` (ISO 8601).
    *   **Thất bại**: Trả về Http Code lỗi, JSON body chứa `success: false`, `message`, `data: null`, `errors` (mảng chi tiết lỗi với `code`, `field`, `message`) và `timestamp`.

---

### 4.1. API Gia hạn Phiên đăng nhập (Refresh Access Token)
*   **Method**: `POST`
*   **Path**: `/api/v1/auth/refresh`
*   **Auth Level**: `PermitAll` (Xác thực thông qua cookie & header bearer hết hạn)
*   **Headers**: 
    *   `Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...` (Access Token cũ đã hết hạn để chống CSRF)
    *   `Cookie: refreshToken=8f8b5f36-3a78-43d9-9524-34e803c4f2bb`

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Gia hạn token thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJob2Fu...",
    "expiresIn": 900
  },
  "errors": null,
  "timestamp": "2026-07-01T10:30:00Z"
}
```
*Lưu ý: Header trả về sẽ tự động đính kèm `Set-Cookie: refreshToken=9a8b7c6d-...; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=604800` chứa Refresh Token mới đã xoay vòng.*

#### Response Lỗi Phát hiện Trộm Token (401 Unauthorized):
```json
{
  "success": false,
  "message": "Phát hiện hành vi sử dụng mã xác thực không hợp lệ. Phiên đăng nhập của bạn đã bị thu hồi để bảo vệ tài khoản.",
  "data": null,
  "errors": [
    {
      "code": "TOKEN_THEFT_DETECTED",
      "field": null,
      "message": "Mã xác thực đã hết hiệu lực, toàn bộ phiên đăng nhập đã bị hủy bỏ"
    }
  ],
  "timestamp": "2026-07-01T10:30:00Z"
}
```

#### Response Lỗi Refresh Token không hợp lệ / Hết hạn (401 Unauthorized):
```json
{
  "success": false,
  "message": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  "data": null,
  "errors": [
    {
      "code": "INVALID_REFRESH_TOKEN",
      "field": null,
      "message": "Refresh Token không tồn tại hoặc đã hết hạn"
    }
  ],
  "timestamp": "2026-07-01T10:30:00Z"
}
```

---

### 4.2. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

Mỗi lỗi nghiệp vụ được định nghĩa trong `ErrorCode` Enum với HTTP Status tương ứng:

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `401 Unauthorized` | `JWT_EXPIRED` | Access Token (JWT) đã hết hạn sử dụng | `Authorization Header` |
| `401 Unauthorized` | `INVALID_REFRESH_TOKEN` | Refresh Token không tồn tại hoặc đã hết hạn | `null` |
| `401 Unauthorized` | `TOKEN_THEFT_DETECTED` | Phát hiện token đã bị sử dụng lại (Token Theft) | `null` |
| `400 Bad Request` | `ACCOUNT_BANNED` | Tài khoản đã bị vô hiệu hóa, không cho phép gia hạn phiên | `null` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt quá giới hạn tần suất truy cập API | `null` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Trải nghiệm người dùng (Silent Refresh UX)
*   **Trải nghiệm liền mạch**: Silent Refresh phải hoạt động hoàn toàn ở chế độ chạy ngầm. Người dùng không được thấy bất kỳ hiện tượng nháy trang, mất trạng thái form đang nhập hoặc hiển thị dialog loading làm phiền.
*   **Đồng bộ hóa request**: Trong quá trình gọi POST `/refresh` ngầm, các API nghiệp vụ khác đang phát sinh đồng thời sẽ được đưa vào hàng đợi (Queue). Ngay khi có Access Token mới, Frontend sẽ lấy token đó để hoàn tất toàn bộ các API trong hàng đợi.

---

### 5.2. Axios Interceptor Implementation Flow (Luồng Code Mẫu)
Để triển khai cơ chế này chuyên nghiệp, Frontend sử dụng Axios Interceptor xử lý lỗi 401:

1.  **Biến Flag**: Thiết lập biến `isRefreshing = false` để tránh việc gửi nhiều request refresh trùng lặp cùng lúc tại Frontend.
2.  **Mảng Hàng đợi**: Thiết lập mảng `failedQueue = []` để lưu trữ các request bị tạm dừng khi token hết hạn.
3.  **Xử lý Logic**:
    *   Nếu nhận lỗi `401` và mã lỗi nghiệp vụ là `JWT_EXPIRED`:
        *   Kiểm tra nếu request hiện tại đã được đánh dấu thử lại (`config._retry === true`), nghĩa là Access Token vừa refresh xong gửi lên vẫn văng lỗi 401 (nguy cơ do clock-skew hoặc lỗi đồng bộ thời gian). Frontend phải **ngắt ngay lập tức** tiến trình, không gọi `/refresh` lần nữa để tránh **Vòng lặp vô hạn (Infinite Loop)**, tiến hành gọi `logout()`, xóa Zustand store và chuyển hướng người dùng về `/login`.
        *   Nếu `config._retry` chưa được set:
            *   Đánh dấu `config._retry = true` trên request gốc.
            *   Nếu `isRefreshing` là `false`: 
                *   Đặt `isRefreshing = true`.
                *   Thực hiện POST `/auth/refresh` (đính kèm header `Authorization: Bearer <expired_token>` và Cookie `refreshToken`).
                *   Khi thành công (nhận được `accessToken` mới):
                    *   Cập nhật `accessToken` mới vào Zustand Store.
                    *   Duyệt hàng đợi `failedQueue` và **gán (inject) Access Token mới** vào header `Authorization` của từng request (`config.headers.Authorization = "Bearer " + newAccessToken`) trước khi gửi lại request bằng Axios.
                    *   Đặt `isRefreshing = false`.
            *   Nếu `isRefreshing` đang là `true`: Đưa request hiện tại vào `failedQueue` dưới dạng một Promise và đợi token mới.
    *   Nếu API `/refresh` trả về lỗi (mã `TOKEN_THEFT_DETECTED` hoặc `INVALID_REFRESH_TOKEN`): Lập tức gọi hàm xóa sạch Zustand store (`logout()`) và điều hướng người dùng tới `/login` kèm Toast thông báo: *"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!"*.

---

### 5.3. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng hoạt động khi Access Token hết hạn:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    SecurePage["Trang Bảo mật <br> (Dashboard/Profile)"]:::screen -->|1. Gọi API bị lỗi JWT_EXPIRED| TokenRefreshCheck{Axios Interceptor}:::action
    
    TokenRefreshCheck -->|2. Bắt đầu gửi ngầm POST /refresh| RefreshAction{Backend kiểm tra}:::action
    
    RefreshAction -->|2a. Thành công: Trả Access Token mới| SecurePage
    
    RefreshAction -->|2b. Thất bại: Token không hợp lệ / Trộm Token| SessionRevoked:::action
    
    SessionRevoked[3. Xóa sạch store Zustand và Xóa session] --> LoginPage["Màn hình Đăng nhập <br> /login"]:::screen
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

Các sự kiện xoay vòng và ân hạn phiên đăng nhập cần được ghi nhận chi tiết để đội ngũ vận hành theo dõi:

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Token refresh request | `{"event": "TOKEN_REFRESH_REQUEST", "ip": "1.2.3.4"}` |
| `INFO` | Token rotated successfully | `{"event": "TOKEN_ROTATED", "userId": "c8b74f51-...", "oldToken": "8f8b5f36-... (UUID)", "newToken": "9a8b7c6d-..."}` |
| `INFO` | Shadow key / Grace period hit | `{"event": "GRACE_PERIOD_HIT", "userId": "c8b74f51-...", "shadowToken": "8f8b5f36-..."}` |
| `WARN` | Refresh failed (Expired token) | `{"event": "REFRESH_FAILED_EXPIRED", "ip": "1.2.3.4"}` |
| `WARN` | Token theft detected | `{"event": "TOKEN_THEFT_DETECTED", "userId": "c8b74f51-...", "usedToken": "8f8b5f36-..."}` |
| `ERROR` | Redis transaction failed | `{"event": "REDIS_TRANSACTION_FAILED", "operation": "ROTATE_TOKEN", "error": "..."}` |

### 6.2. Quy tắc Bảo mật Log
*   Không ghi nhận thông tin nhạy cảm của người dùng (như mật khẩu, thông tin cá nhân) vào log của tiến trình Refresh.
*   **Masking token**: Không ghi nhận toàn bộ giá trị chuỗi JWT Access Token, chỉ ghi nhận 10 ký tự đầu tiên để phục vụ đối chiếu khi debug, hoặc chỉ log giá trị UUID của Refresh Token.
