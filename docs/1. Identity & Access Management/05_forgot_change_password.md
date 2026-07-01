# 05. Quên Mật khẩu & Đổi Mật khẩu (Forgot & Change Password)

Tài liệu đặc tả A-Z tính năng Quên mật khẩu (dành cho người dùng ngoài hệ thống) và Đổi mật khẩu (dành cho người dùng đã đăng nhập) của hệ thống PWB MiNi, tích hợp các cơ chế bảo mật chống dò quét tài khoản (User Enumeration Defense) và cưỡng chế thoát phiên (Session Revocation) bảo mật.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: 
    *   *Quên mật khẩu*: Người dùng không nhớ mật khẩu, cần khôi phục qua Email đã đăng ký.
    *   *Đổi mật khẩu*: Người dùng đang đăng nhập (ACTIVE), muốn thay đổi mật khẩu định kỳ để nâng cao bảo mật.
*   **Quy trình tóm tắt**:
    *   *Quên mật khẩu*: Người dùng điền Email -> Hệ thống sinh Reset Token gửi qua Email -> Người dùng click link điền mật khẩu mới -> Xác thực và đổi mật khẩu thành công -> Hủy toàn bộ phiên đang chạy.
    *   *Đổi mật khẩu*: Người dùng điền Mật khẩu cũ + Mật khẩu mới + Xác nhận mật khẩu mới -> Hệ thống đối khớp mật khẩu cũ, băm mật khẩu mới -> Đổi thành công -> Hủy toàn bộ phiên trên thiết bị khác (giữ lại phiên hiện tại).

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Phòng chống dò quét tài khoản (User Enumeration Defense)
*   Tại API yêu cầu khôi phục mật khẩu (`POST /api/v1/auth/forgot-password`), hệ thống **luôn trả về một phản hồi giống nhau** (HTTP 200 OK với thông điệp *"Nếu email tồn tại trên hệ thống, chúng tôi đã gửi liên kết khôi phục..."*) bất kể email đó có thực sự tồn tại trong Database hay không.
*   Quy tắc này chặn đứng việc hacker sử dụng API này để quét và thử dò tìm danh sách email người dùng đã đăng ký trên hệ thống.

#### B. Cơ chế khôi phục mật khẩu qua Email (Forgot Password)
*   **Sinh mã Token**: Khi email hợp lệ và hoạt động (`ACTIVE`), hệ thống sinh mã Reset Token ngẫu nhiên (UUID), lưu vào Redis dạng `password_reset_token:{token}` với giá trị là `email`, TTL **10 phút**.
*   **Xử lý Outbox & Gửi mail**: Sự kiện khôi phục mật khẩu được lưu vào bảng `outbox_events` (`aggregate_type='IAM'`, `event_type='PASSWORD_RESET'`). Sau khi commit transaction, hệ thống đẩy sự kiện sang Kafka để Mail Worker soạn và gửi mail chứa link khôi phục dạng: `https://pwbmini.com/reset-password?token={token}`.
*   **Xác nhận và đặt lại**: Khi người dùng click link, nhập mật khẩu mới:
    *   Mật khẩu mới phải vượt qua các Validation rules (độ dài, ký tự đặc biệt).
    *   Backend băm mật khẩu bằng BCrypt (strength = 10) và cập nhật DB.
    *   Xóa key reset token khỏi Redis.
    *   **Dọn dẹp khóa Lockout**: Đồng thời xóa bỏ các key `login_lockout` và `login_attempts` của người dùng này trên Redis để cho phép họ đăng nhập lại ngay lập tức mà không bị kẹt bởi hàng rào lockout trước đó.
    *   **Cưỡng chế thoát phiên (Session Revocation)**: Xóa toàn bộ các token phiên trong ZSet `user:sessions:{userId}` và tất cả khóa `session:refresh_token:{token}` liên quan trên Redis để buộc tất cả thiết bị đang đăng nhập bằng tài khoản này phải đăng xuất lập tức.

#### C. Quy tắc Đổi mật khẩu khi đang đăng nhập (Change Password)
*   Yêu cầu Access Token hợp lệ đính kèm trong header `Authorization`.
*   **Tài khoản OAuth-only** (những user đăng ký hoàn toàn qua Google OAuth2, `password = null`): **Không được phép** truy cập tính năng Đổi mật khẩu. Backend trả lỗi HTTP 400 (`OAUTH_ONLY_ACCOUNT`). Frontend ẩn menu "Đổi mật khẩu" nếu user không có Local credentials (`oauth_provider != null` và `password == null`).
*   Người dùng phải nhập đúng Mật khẩu cũ (đối khớp bằng BCrypt).
*   **Mật khẩu mới** phải khác mật khẩu cũ (chống đổi mật khẩu cũ trùng lặp).
*   **Hủy phiên thiết bị khác**: Sau khi đổi thành công, hệ thống thực hiện quét ZSet `user:sessions:{userId}` và **xóa toàn bộ Refresh Token của các thiết bị khác**, chỉ giữ duy nhất Refresh Token hiện tại (lấy từ cookie) hoạt động bình thường để tránh làm phiền người dùng hiện tại phải đăng nhập lại.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

| API Request | Trường dữ liệu | Ràng buộc | Annotation (Backend) | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `ForgotPasswordRequest` | `email` | Bắt buộc, đúng định dạng, tối đa 100 ký tự | `@NotBlank`, `@Email`, `@Size(max=100)` | Email nhận mã khôi phục |
| `ResetPasswordRequest` | `token` | Bắt buộc | `@NotBlank` | Token khôi phục lấy từ link email |
| | `newPassword` | Bắt buộc, tối thiểu 8 ký tự, 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt, tối đa 100 ký tự | `@NotBlank`, `@Pattern`, `@Size(max=100)` | Mật khẩu mới đặt lại |
| | `confirmPassword` | Bắt buộc, trùng khớp với `newPassword` | `@NotBlank` | Xác nhận mật khẩu mới |
| `ChangePasswordRequest` | `oldPassword` | Bắt buộc | `@NotBlank` | Mật khẩu hiện tại |
| | `newPassword` | Bắt buộc, tối thiểu 8 ký tự, 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt, tối đa 100 ký tự, khác `oldPassword` | `@NotBlank`, `@Pattern`, `@Size(max=100)` | Mật khẩu mới muốn thay đổi |
| | `confirmPassword` | Bắt buộc, trùng khớp với `newPassword` | `@NotBlank` | Xác nhận mật khẩu mới |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/auth/forgot-password` | **3 requests / phút / IP** | Ngăn spam gửi email khôi phục liên tục làm nghẽn dịch vụ gửi mail |
| `POST /api/v1/auth/reset-password` | **5 requests / phút / IP** | Hạn chế spam thử sai Reset Token |
| `POST /api/v1/auth/change-password` | **5 requests / phút / IP** | Hạn chế spam băm BCrypt làm nghẽn CPU |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Quên Mật khẩu (Forgot Password Flow)

Luồng khôi phục mật khẩu gồm 2 giai đoạn: Yêu cầu khôi phục gửi mail và Xác nhận đặt lại mật khẩu mới.

```mermaid
sequenceDiagram
    autonumber
    actor User as Client User
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL
    participant Kafka as Apache Kafka
    participant Worker as Mail Worker Service

    Note over User, FE: --- Giai đoạn 1: Gửi yêu cầu khôi phục ---
    User->>FE: Nhập Email & bấm gửi yêu cầu
    FE->>BE: POST /api/v1/auth/forgot-password (ForgotPasswordRequest)
    BE->>BE: Validate email đầu vào
    
    BE->>DB: Tìm kiếm User theo email
    alt User không tồn tại, không ACTIVE (PENDING_VERIFICATION/BANNED/PENDING_DELETION), hoặc OAuth-only (password=null)
        BE-->>FE: HTTP 200 OK (Thông báo giả lập thành công - Enumeration Defense)
    else User hợp lệ
        BE->>BE: Sinh Reset Token ngẫu nhiên (UUID)
        
        Note over BE, DB: Bắt đầu Transaction
        BE->>DB: Lưu OutboxEvent (event_type='PASSWORD_RESET', payload chứa email & token)
        Note over BE, DB: Commit Transaction
        
        par Trả phản hồi nhanh
            BE-->>FE: HTTP 200 OK ("Nếu email tồn tại, liên kết khôi phục đã được gửi...")
            FE-->>User: Hiển thị thông báo kiểm tra hòm thư
        and Xử lý gửi Mail bất đồng bộ
            BE->>BE: @TransactionalEventListener (After Commit) bắt sự kiện
            BE->>Redis: SET 'password_reset_token:{token}' = email (TTL: 10 phút)
            BE->>Kafka: Phát sự kiện 'PASSWORD_RESET' tới topic 'notification-events'
            BE->>DB: Cập nhật outbox_event -> 'PROCESSED'
            
            Kafka->>Worker: Consume sự kiện
            Worker->>User: Gửi Email chứa link khôi phục (kèm Token)
        end
    end

    Note over User, FE: --- Giai đoạn 2: Đặt lại mật khẩu mới ---
    User->>FE: Click link email, nhập mật khẩu mới & nhấn Đặt lại
    FE->>BE: POST /api/v1/auth/reset-password (ResetPasswordRequest)
    BE->>BE: Validate định dạng mật khẩu & đối khớp Confirm Password
    
    BE->>Redis: Tìm kiếm email liên kết từ key 'password_reset_token:{token}'
    alt Token không tồn tại hoặc hết hạn (null)
        BE-->>FE: HTTP 400 Bad Request (INVALID_RESET_TOKEN)
    else Token hợp lệ
        BE->>Redis: Lấy email từ Redis và xóa key 'password_reset_token:{token}'
        BE->>DB: Truy vấn User theo email
        
        alt Trạng thái User là BANNED
            BE-->>FE: HTTP 400 Bad Request (ACCOUNT_BANNED)
        else Trạng thái hợp lệ (ACTIVE)
            BE->>BE: Băm mật khẩu mới bằng BCrypt (strength = 10)
            
            Note over BE, DB: Bắt đầu Transaction
            BE->>DB: Cập nhật password mới của User
            Note over BE, DB: Commit Transaction
            
            Note over BE, Redis: Thu hồi toàn bộ phiên đăng nhập (Redis Pipeline)
            BE->>Redis: Lấy danh sách token từ ZSet 'user:sessions:{userId}'
            loop Duyệt qua toàn bộ token của User
                BE->>Redis: DEL 'session:refresh_token:{token}'
            end
            BE->>Redis: DEL 'user:sessions:{userId}'
            BE->>Redis: DEL 'login_lockout:{email}' & 'login_attempts:{email}' (Dọn dẹp lockout)
            Note over BE, Redis: Kết thúc Pipeline
            
            BE-->>FE: HTTP 200 OK (Đặt lại mật khẩu thành công)
            FE-->>User: Điều hướng về màn hình /login kèm thông báo
        end
    end
```

##### 📝 Mô tả chi tiết các bước xử lý (Quên mật khẩu):
1.  **Yêu cầu gửi mail**: Người dùng nhập Email, Frontend gửi `POST /forgot-password`. Nếu email không tồn tại, tài khoản ở trạng thái không phải `ACTIVE` (`PENDING_VERIFICATION`, `BANNED`, `PENDING_DELETION`), hoặc là tài khoản OAuth-only (`password = null`), Backend vẫn trả về HTTP 200 OK giả lập thành công để bảo mật (Enumeration Defense).
2.  **Lưu Outbox & Gửi mail**: Nếu hợp lệ, hệ thống tạo Reset Token (UUID), ghi vào bảng Outbox, gửi event sang Kafka. Đồng thời lưu key `password_reset_token:{token}` vào Redis với TTL 10 phút. Mail Worker gửi link khôi phục tới hòm thư của người dùng.
3.  **Điền mật khẩu mới**: Người dùng click link mở ra màn hình `/reset-password?token={token}` trên Frontend, nhập mật khẩu mới và xác nhận.
4.  **Đặt lại mật khẩu**: Frontend gửi `POST /reset-password` kèm mật khẩu mới và token. Backend đối soát token trên Redis:
    *   *Nếu không hợp lệ*: Trả lỗi HTTP 400 (`INVALID_RESET_TOKEN`).
    *   *Nếu hợp lệ*: Lấy email, xóa token trên Redis, băm mật khẩu mới bằng BCrypt và cập nhật xuống Postgres.
5.  **Dọn phiên & Lockout**: Backend sử dụng **Redis Pipeline** xóa toàn bộ Refresh Token của user trên Redis và xóa ZSet quản lý phiên để cưỡng chế logout các thiết bị đang online. Hệ thống cũng xóa bỏ các bộ đếm sai và lockout của user này để họ đăng nhập bình thường. Nếu user bị `BANNED` trong khoảng thời gian reset token còn hiệu lực, Backend từ chối và trả lỗi `ACCOUNT_BANNED`.

---

### 2.2. Luồng Đổi Mật khẩu khi đang Đăng nhập (Change Password Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Client User
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL

    User->>FE: Nhập mật khẩu cũ, mật khẩu mới & xác nhận
    FE->>BE: POST /api/v1/auth/change-password (Kèm JWT & Cookie refreshToken)
    
    BE->>BE: Xác thực Access Token hợp lệ, lấy userId
    
    alt Tài khoản OAuth-only (password = null)
        BE-->>FE: HTTP 400 Bad Request (OAUTH_ONLY_ACCOUNT)
    else Tài khoản Local (có mật khẩu)
        BE->>BE: Validate định dạng đầu vào & so sánh mật khẩu mới với mật khẩu cũ
        
        BE->>DB: Truy vấn mật khẩu hiện tại của User trong Postgres
        BE->>BE: Đối khớp mật khẩu cũ bằng BCrypt
        
        alt Sai mật khẩu cũ
            BE-->>FE: HTTP 400 Bad Request (INVALID_OLD_PASSWORD)
        else Hợp lệ (Mật khẩu cũ khớp)
            BE->>BE: Băm mật khẩu mới bằng BCrypt (strength = 10)
            
            Note over BE, DB: Bắt đầu Transaction
            BE->>DB: Cập nhật mật khẩu mới trong DB
            Note over BE, DB: Commit Transaction
            
            Note over BE, Redis: Thu hồi phiên của các THIẾT BỊ KHÁC (Redis Pipeline)
            BE->>Redis: Đọc toàn bộ các phiên từ ZSet 'user:sessions:{userId}'
            loop Duyệt các token
                alt token không khớp với currentRefreshToken (lấy từ cookie)
                    BE->>Redis: DEL 'session:refresh_token:{token}'
                    BE->>Redis: Xóa token khỏi ZSet 'user:sessions:{userId}'
                end
            end
            Note over BE, Redis: Kết thúc Pipeline
            
            BE-->>FE: HTTP 200 OK (Đổi mật khẩu thành công)
            FE-->>User: Thông báo đổi mật khẩu thành công (giữ nguyên đăng nhập hiện tại)
        end
    end
```

##### 📝 Mô tả chi tiết các bước xử lý (Đổi mật khẩu):
1.  **Nhập liệu**: Người dùng nhập mật khẩu cũ, mật khẩu mới, xác nhận mật khẩu mới trên trang cá nhân và nhấn Đổi mật khẩu.
2.  **Gửi yêu cầu**: Frontend gửi `POST /change-password` đính kèm JWT trên Header và cookie `refreshToken`.
3.  **Kiểm tra OAuth-only**: Backend kiểm tra nếu tài khoản là OAuth-only (`password = null`) thì từ chối với lỗi `OAUTH_ONLY_ACCOUNT`.
4.  **Xác thực mật khẩu cũ**: Backend lấy thông tin User từ Context bảo mật, truy vấn Postgres để lấy mật khẩu hiện tại, sau đó dùng BCrypt đối khớp. Nếu sai, trả lỗi HTTP 400 (`INVALID_OLD_PASSWORD`).
5.  **Kiểm tra trùng lặp**: Backend kiểm tra mật khẩu mới xem có trùng mật khẩu cũ không, nếu trùng trả lỗi HTTP 400 (`PASSWORD_REUSE_BLOCKED`).
6.  **Cập nhật & Hủy phiên thiết bị khác**: Backend băm mật khẩu mới trong Transaction, cập nhật Postgres. Hệ thống sử dụng **Redis Pipeline** quét danh sách các phiên đăng nhập từ ZSet của user. Nếu token nào khác với Refresh Token hiện tại (đọc từ request cookie) thì xóa khỏi Redis và ZSet. Phiên đăng nhập hiện tại được giữ lại.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Cấu trúc dữ liệu Redis (Cache & Limits)

Các khóa Redis phục vụ cho tính năng Quên & Đổi mật khẩu:

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `password_reset_token:{token}` | `String` | `email` | **10 phút** | Lưu trữ mã khôi phục mật khẩu gửi qua email người dùng. |
| `session:refresh_token:{token}` | `String` | `userId` | *Bị xóa* | Các phiên của thiết bị khác hoặc toàn bộ phiên bị xóa để thu hồi đăng nhập. |
| `user:sessions:{userId}` | `ZSet` | `token` (UUID) | *Bị cập nhật* | Danh sách phiên hoạt động của người dùng bị dọn dẹp tương ứng. |

---

## 🔌 4. API Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1/auth`
*   **Headers**: `Content-Type: application/json`
*   **Format Phản Hồi**: Hệ thống đồng nhất sử dụng cấu trúc `ApiResponse<T>` chuẩn hóa theo quy ước dự án.

---

### 4.1. API Yêu cầu Khôi phục Mật khẩu (Forgot Password)
*   **Method**: `POST`
*   **Path**: `/api/v1/auth/forgot-password`
*   **Auth Level**: `PermitAll`

#### Request Body (`ForgotPasswordRequest`):
```json
{
  "email": "producer@example.com"
}
```

#### Response Thành công (200 OK - Luôn giống nhau):
```json
{
  "success": true,
  "message": "Nếu email này tồn tại trên hệ thống, chúng tôi đã gửi liên kết khôi phục mật khẩu vào hòm thư của bạn",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T11:00:00Z"
}
```

---

### 4.2. API Đặt lại Mật khẩu Mới (Reset Password)
*   **Method**: `POST`
*   **Path**: `/api/v1/auth/reset-password`
*   **Auth Level**: `PermitAll`

#### Request Body (`ResetPasswordRequest`):
```json
{
  "token": "7a8b9c6d-3a78-43d9-9524-34e803c4f2bb",
  "newPassword": "NewStrongPassword123!",
  "confirmPassword": "NewStrongPassword123!"
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Đặt lại mật khẩu thành công. Các phiên hoạt động khác đã được đăng xuất an toàn",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T11:05:00Z"
}
```

#### Response Lỗi Token Không hợp lệ / Hết hạn (400 Bad Request):
```json
{
  "success": false,
  "message": "Mã token khôi phục mật khẩu không hợp lệ hoặc đã hết hạn",
  "data": null,
  "errors": [
    {
      "code": "INVALID_RESET_TOKEN",
      "field": "token",
      "message": "Token khôi phục không tồn tại hoặc đã hết hiệu lực, vui lòng yêu cầu mã mới"
    }
  ],
  "timestamp": "2026-07-01T11:05:00Z"
}
```

---

### 4.3. API Đổi Mật khẩu khi đang Đăng nhập (Change Password)
*   **Method**: `POST`
*   **Path**: `/api/v1/auth/change-password`
*   **Auth Level**: `Requires Authentication` (Yêu cầu JWT trong Authorization Header)
*   **Headers**: 
    *   `Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
    *   `Cookie: refreshToken=8f8b5f36-3a78-43d9-9524-34e803c4f2bb`

#### Request Body (`ChangePasswordRequest`):
```json
{
  "oldPassword": "CurrentPassword123!",
  "newPassword": "NewStrongPassword123!",
  "confirmPassword": "NewStrongPassword123!"
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Thay đổi mật khẩu thành công. Các thiết bị khác đã được đăng xuất để bảo mật",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T11:10:00Z"
}
```

#### Response Lỗi Sai mật khẩu cũ (400 Bad Request):
```json
{
  "success": false,
  "message": "Mật khẩu hiện tại không chính xác",
  "data": null,
  "errors": [
    {
      "code": "INVALID_OLD_PASSWORD",
      "field": "oldPassword",
      "message": "Mật khẩu cũ nhập vào không khớp với mật khẩu đã lưu"
    }
  ],
  "timestamp": "2026-07-01T11:10:00Z"
}
```

---

#### Response Lỗi Mật khẩu mới trùng mật khẩu cũ (400 Bad Request):
```json
{
  "success": false,
  "message": "Mật khẩu mới không được trùng với mật khẩu cũ",
  "data": null,
  "errors": [
    {
      "code": "PASSWORD_REUSE_BLOCKED",
      "field": "newPassword",
      "message": "Vui lòng chọn một mật khẩu khác hoàn toàn với mật khẩu hiện tại"
    }
  ],
  "timestamp": "2026-07-01T11:10:00Z"
}
```

#### Response Lỗi Tài khoản OAuth-only (400 Bad Request):
```json
{
  "success": false,
  "message": "Tài khoản của bạn đăng nhập qua Google, không có mật khẩu cục bộ",
  "data": null,
  "errors": [
    {
      "code": "OAUTH_ONLY_ACCOUNT",
      "field": null,
      "message": "Tài khoản đăng nhập bằng Google không hỗ trợ đổi mật khẩu"
    }
  ],
  "timestamp": "2026-07-01T11:10:00Z"
}
```

---

### 4.4. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

Mỗi lỗi nghiệp vụ được định nghĩa trong `ErrorCode` Enum với HTTP Status tương ứng:

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | Dữ liệu đầu vào không đúng định dạng (mật khẩu yếu, confirm không khớp) | Tên trường tương ứng |
| `400 Bad Request` | `INVALID_RESET_TOKEN` | Token khôi phục mật khẩu không tồn tại hoặc hết hạn | `token` |
| `400 Bad Request` | `INVALID_OLD_PASSWORD` | Mật khẩu hiện tại nhập vào không chính xác | `oldPassword` |
| `400 Bad Request` | `PASSWORD_REUSE_BLOCKED` | Mật khẩu mới không được trùng mật khẩu cũ | `newPassword` |
| `400 Bad Request` | `OAUTH_ONLY_ACCOUNT` | Tài khoản đăng nhập qua Google, không có mật khẩu cục bộ | `null` |
| `400 Bad Request` | `ACCOUNT_BANNED` | Tài khoản đã bị vô hiệu hóa, không cho phép đặt lại mật khẩu | `null` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt quá giới hạn tần suất gửi yêu cầu | `null` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện Đơn sắc (Grayscale Theme)
*   Form nhập mật khẩu mới hỗ trợ nút **Ẩn/Hiện mật khẩu** và thanh chỉ báo độ mạnh mật khẩu tương tự màn hình Đăng ký để đảm bảo tính đồng bộ UX.
*   Trang khôi phục mật khẩu chứa thông tin hướng dẫn ngắn gọn, nút bấm thiết kế xám/đen phẳng, có loading spinner khi nhấn Đặt lại/Đổi mật khẩu.

---

### 5.2. Chống Đổi mật khẩu Trùng lặp & Quản lý Thông báo
*   **Xác thực tại Client**: Frontend chặn nhấn nút Submit nếu mật khẩu mới trùng khớp hoàn toàn với mật khẩu cũ (trong màn hình Đổi mật khẩu) hoặc `confirmPassword` không trùng khớp.
*   **Xử lý Đổi mật khẩu thành công**: Khi đổi mật khẩu thành công ở màn hình Change Password, do hệ thống giữ nguyên phiên đăng nhập hiện tại, Frontend chỉ cần hiển thị Toast thông báo thành công và giữ người dùng ở lại trang cá nhân, không cần redirect về trang login.

---

### 5.3. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng chuyển dịch màn hình khi khôi phục và thay đổi mật khẩu:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    LoginPage["Màn hình Đăng nhập <br> /login"]:::screen -->|Click Quên mật khẩu| ForgotPage["Màn hình Quên mật khẩu <br> /forgot-password"]:::screen
    
    ForgotPage -->|Nhập email & bấm gửi| RequestReset{Backend xử lý & Gửi email}:::action
    RequestReset -->|Trả kết quả 200| LoginPage
    
    UserEmail[Email của người dùng]:::screen -->|Click Link reset token| ResetPage["Màn hình Đặt lại mật khẩu <br> /reset-password"]:::screen
    ResetPage -->|Nhập mật khẩu mới & Đặt lại| ResetAction{Backend đối soát token & Đổi}:::action
    
    ResetAction -->|Thất bại: Token hết hạn| ResetPage
    ResetAction -->|Thành công: Đổi & Hủy toàn bộ phiên| LoginPage

    DashboardPage["Trang cá nhân/Dashboard <br> (Đã đăng nhập)"]:::screen -->|Click Đổi mật khẩu| ChangePage["Màn hình Đổi mật khẩu <br> /change-password"]:::screen
    ChangePage -->|Nhập old/new pass & Đổi| ChangeAction{Backend đối khớp old & Đổi}:::action
    
    ChangeAction -->|Thất bại: Sai oldPassword| ChangePage
    ChangeAction -->|Thành công: Đổi & Đăng xuất thiết bị khác| DashboardPage
```

---

## ✉️ 6. Email Template Khôi phục Mật khẩu (Password Reset Email Template)

Hệ thống sử dụng template HTML responsive để gửi email chứa liên kết khôi phục mật khẩu. Template được biên dịch bởi Mail Worker Service từ Thymeleaf hoặc FreeMarker.

### 6.1. Thông tin Email
| Thuộc tính | Giá trị |
| :--- | :--- |
| **From** | `PWB MiNi Security` &lt;security@pwbmini.com&gt; |
| **Subject** | `[PWB MiNi] Hướng dẫn khôi phục mật khẩu tài khoản` |
| **Template ID** | `email/password-reset` |
| **Format** | HTML responsive (tương thích mobile ≥ 320px) |
| **SMTP Provider** | Local: **Gmail SMTP** (App Password)<br>Production: **Resend SMTP** (smtp.resend.com) hoặc **AWS SES** |

### 6.2. Nội dung Email (Content Structure)
*   **Header**: Logo PWB MiNi + Tiêu đề phụ "Khôi phục mật khẩu của bạn".
*   **Body**:
    *   Lời chào cá nhân hóa: `Xin chào {fullName},`
    *   Nội dung thông báo: "Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản PWB MiNi của bạn. Vui lòng click vào nút bên dưới để tiến hành đặt mật khẩu mới:"
    *   **Nút Hành động**: Nút bấm nổi bật "Đặt lại mật khẩu" liên kết tới đường dẫn khôi phục: `https://pwbmini.com/reset-password?token={token}`.
    *   Cảnh báo bảo mật: "Đường dẫn này có hiệu lực trong vòng **10 phút**. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này, tài khoản của bạn vẫn được an toàn."
*   **Footer**: © 2026 PWB MiNi. Hỗ trợ khẩn cấp: `security@pwbmini.com`.

---

## 📊 7. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

Các sự kiện thay đổi thông tin xác thực nhạy cảm cần được ghi log chính xác theo cấu trúc:

### 7.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Forgot password request received | `{"event": "FORGOT_PASSWORD_REQUEST", "email": "p***@example.com"}` |
| `INFO` | Reset token generated | `{"event": "RESET_TOKEN_GENERATED", "email": "p***@example.com", "tokenUUID": "7a8b9c6d-..."}` |
| `INFO` | Password reset successful | `{"event": "PASSWORD_RESET_SUCCESS", "email": "p***@example.com"}` |
| `INFO` | Password change successful | `{"event": "PASSWORD_CHANGE_SUCCESS", "userId": "c8b74f51-...", "otherSessionsKicked": 2}` |
| `WARN` | Reset failed (Invalid/Expired token) | `{"event": "RESET_FAILED_INVALID_TOKEN", "tokenUUID": "7a8b9c6d-..."}` |
| `WARN` | Change password failed (Wrong current password) | `{"event": "CHANGE_PASSWORD_FAILED_CREDENTIALS", "userId": "c8b74f51-..."}` |
| `WARN` | Password reuse blocked | `{"event": "PASSWORD_REUSE_BLOCKED", "userId": "c8b74f51-..."}` |
| `ERROR` | Database update failure | `{"event": "PASSWORD_DB_UPDATE_FAILED", "userId": "c8b74f51-...", "error": "Optimistic locking failure"}` |

### 7.2. Quy tắc Bảo mật Log
*   **Không bao giờ ghi nhận mật khẩu cũ hoặc mới** dưới dạng text thô hoặc dạng băm vào hệ thống log.
*   **Masking Email**: Che dấu địa chỉ email trong log tương tự quy trình đăng nhập (`p***@example.com`).
*   Không ghi nhận đầy đủ token trong log, chỉ hiển thị dạng UUID rút ngắn để định dạng sự kiện.
