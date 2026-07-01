# 04. Đăng xuất (User Logout & Token Invalidation)

Tài liệu đặc tả A-Z tính năng Đăng xuất cho hệ thống PWB MiNi, sử dụng cơ chế vô hiệu hóa đồng thời cả Access Token (JWT Blacklisting) và Refresh Token trên Redis để đảm bảo an toàn tuyệt đối cho người dùng sau khi thoát khỏi hệ thống.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Người dùng đã đăng nhập (sở hữu Access Token và Refresh Token hợp lệ).
*   **Quy trình tóm tắt**:
    1.  Người dùng click nút "Đăng xuất" trên giao diện.
    2.  Frontend gửi yêu cầu `POST /api/v1/auth/logout` đính kèm Access Token trong header `Authorization` và Refresh Token trong Cookie.
    3.  Backend vô hiệu hóa Refresh Token trên Redis, đồng thời đưa Access Token vào danh sách đen (Blacklist) trên Redis với thời gian sống bằng thời gian còn lại của token đó.
    4.  Backend phản hồi chỉ thị xóa Cookie cho trình duyệt.
    5.  Frontend xóa sạch thông tin phiên trong Zustand Store và điều hướng người dùng quay lại trang Đăng nhập `/login`.

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Vô hiệu hóa Refresh Token
*   Backend lập tức xóa khóa `session:refresh_token:{token}` khỏi Redis Cache để chặn đứng mọi hành vi sử dụng token này để gọi API `/refresh` gia hạn phiên.
*   Hệ thống cũng đồng thời xóa định danh của Refresh Token này ra khỏi danh sách ZSet quản lý phiên của người dùng `user:sessions:{userId}`.

#### B. Vô hiệu hóa Access Token (JWT Blacklisting)
*   Vì Access Token dạng JWT là phi trạng thái (Stateless), nó vẫn có hiệu lực cho đến khi hết hạn (Expired) ngay cả khi người dùng đã nhấn đăng xuất.
*   **Giải pháp**: Backend trích xuất mã chữ ký số (Signature) hoặc ID của JWT (`jti`) và ghi nhận vào Redis dạng `session:blacklist_token:{signature}` với giá trị `"true"`.
*   **TTL Động (Dynamic TTL)**: Thời gian tồn tại của key blacklist này được tính toán động bằng: **`Thời gian hết hạn của JWT - Thời điểm đăng xuất hiện tại`**.
*   **Xử lý ở Security Filter**: Mọi API request đính kèm Access Token có signature nằm trong danh sách blacklist sẽ bị bộ lọc bảo mật từ chối truy cập ngay ở Gateway/Filter với mã lỗi HTTP `401 Unauthorized` mà không cần xử lý tiếp ở tầng Controller.

#### C. Xóa Cookie ở Trình duyệt
*   Backend trả về header `Set-Cookie` để xóa cookie `refreshToken`:
    `Set-Cookie: refreshToken=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT`
*   Đặt `Max-Age=0` và ngày hết hạn trong quá khứ để ép buộc trình duyệt của client hủy bỏ cookie này lập tức.

#### D. Quy tắc Buộc Đăng xuất phía Client (Force Local Logout)
*   Trong trường hợp người dùng gặp sự cố mạng (Mất kết nối) hoặc API Đăng xuất của Backend trả về lỗi, Frontend **BẮT BUỘC** phải thực hiện cơ chế **Đăng xuất cục bộ cưỡng chế**:
    *   Xóa sạch token lưu trong bộ nhớ Zustand Store.
    *   Xóa toàn bộ cache và trạng thái phiên trong trình duyệt.
    *   Điều hướng người dùng về màn hình `/login`.
*   *Lý do*: Không để xảy ra tình trạng người dùng bị kẹt lại màn hình Dashboard do mất mạng không gọi được API Logout, đảm bảo tính riêng tư của thiết bị dùng chung.

#### E. Đăng xuất Tất cả Thiết bị (Logout All Devices — Future Enhancement)
*   Tính năng "Đăng xuất khỏi tất cả các thiết bị" (xóa toàn bộ ZSet `user:sessions:{userId}` và tất cả Refresh Token liên quan trên Redis) sẽ được đặc tả chi tiết trong bài riêng hoặc bổ sung vào các giai đoạn phát triển tiếp theo.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

*   API `/logout` nhận các mã xác thực từ Request Header và Cookie:
    *   `Authorization: Bearer <accessToken>` (Request Header).
    *   `Cookie: refreshToken=<token>` (Cookies).
*   Không yêu cầu bất kỳ tham số hay JSON body nào truyền lên.

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/auth/logout` | **20 requests / phút / IP** | Ngăn chặn hành vi spam liên tục request logout làm tràn danh sách blacklist trên Redis |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Đăng xuất và Vô hiệu hóa Phiên (Logout Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Client User
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache

    User->>FE: Click nút "Đăng xuất" (Logout)
    FE->>BE: POST /api/v1/auth/logout (Authorization: Bearer JWT, Cookie: refreshToken)
    
    BE->>BE: Giải mã JWT (cho phép cả expired JWT), lấy userId và thời gian hết hạn
    BE->>BE: Tính toán thời gian sống còn lại của JWT (Dynamic TTL)
    
    Note over BE, Redis: Bắt đầu xử lý nguyên tử trên Cache (Redis Pipeline)
    BE->>Redis: SET 'session:blacklist_token:{jwtSignature}' = true (TTL: Dynamic TTL)
    
    alt Cookie refreshToken tồn tại và hợp lệ
        BE->>Redis: DEL 'session:refresh_token:{refreshToken}'
        BE->>Redis: Xóa refreshToken khỏi ZSet 'user:sessions:{userId}'
    else Cookie refreshToken thiếu hoặc đã bị xóa trước đó
        Note over BE: Bỏ qua, chỉ blacklist JWT (Idempotent Logout)
    end
    
    Note over BE, Redis: Kết thúc Pipeline
    
    BE-->>FE: HTTP 200 OK (Set-Cookie: refreshToken=; Max-Age=0)
    
    Note over FE: Frontend nhận tín hiệu và dọn dẹp
    FE->>FE: Xóa Access Token trong Zustand Store (Set Token = null)
    FE->>FE: Reset toàn bộ Client-Side State
    FE->>FE: Broadcast event 'logout' qua BroadcastChannel (Multi-tab sync)
    FE-->>User: Điều hướng về màn hình /login kèm Toast thông báo thành công
```

##### 📝 Mô tả chi tiết các bước xử lý:
1.  **Thao tác người dùng**: Người dùng nhấn nút "Đăng xuất" trên thanh điều hướng hoặc menu cá nhân.
2.  **Gửi Request**: Frontend lập tức gửi request HTTP `POST /api/v1/auth/logout` kèm Access Token hiện tại trong header `Authorization` và Refresh Token trong cookie.
3.  **Xử lý tại Backend**:
    *   Backend giải mã Access Token (**cho phép cả JWT đã expired** — chỉ lấy thông tin, không reject), trích xuất thời điểm hết hạn (`exp`) và ID người dùng (`userId`).
    *   Tính toán thời gian sống còn lại (ví dụ: JWT hết hạn sau 7 phút 12 giây, TTL của key blacklist sẽ được set là 432 giây). Nếu JWT đã expired thì TTL = 0 (không cần blacklist).
    *   Sử dụng **Redis Pipeline** thực hiện các hành động:
        1.  Thêm Signature của JWT hiện tại vào danh sách Blacklist với TTL động vừa tính (bỏ qua nếu JWT đã expired).
        2.  Xóa khóa Refresh Token tương ứng để vô hiệu hóa tính năng gia hạn (bỏ qua nếu cookie không tồn tại — **Idempotent Logout**).
        3.  Cập nhật ZSet `user:sessions:{userId}` của người dùng để giảm số phiên đang hoạt động.
4.  **Hủy Cookie**: Backend phản hồi HTTP 200 OK kèm header `Set-Cookie` đặt `Max-Age=0` để trình duyệt xóa cookie `refreshToken`.
5.  **Dọn dẹp Client**: Frontend xóa sạch dữ liệu token trong bộ nhớ Zustand, giải phóng bộ nhớ, hủy các bộ theo dõi trạng thái, broadcast sự kiện logout qua `BroadcastChannel` (đồng bộ các tab khác) và đưa người dùng về trang đăng nhập `/login`.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu)

### 3.1. Cấu trúc dữ liệu Redis (Cache & Limits)

Các khóa Redis phục vụ cho quá trình đăng xuất và vô hiệu hóa token:

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `session:blacklist_token:{jwtSignature}` | `String` | `"true"` | **Động** (Tối đa 15 phút) | Danh sách đen chứa chữ ký số của các Access Token bị hủy trước thời hạn. |
| `session:refresh_token:{token}` | `String` | `userId` | **Bị xóa** | Bị xóa bỏ ngay khi đăng xuất để vô hiệu hóa gia hạn phiên. |
| `user:sessions:{userId}` | `ZSet` | `token` (UUID) | **Bị cập nhật** | Bản ghi tương ứng với Refresh Token bị xóa khỏi ZSet. |

> **⚡ Lưu ý Kỹ thuật: Bảo vệ Blacklist**
>
> Việc lưu trữ Signature của JWT (thay vì toàn bộ chuỗi JWT dài) giúp tối ưu hóa dung lượng bộ nhớ RAM trên Redis rất lớn. Bộ lọc bảo mật `JwtAuthenticationFilter` của Backend sẽ trích xuất signature từ token gửi lên ở mỗi request bảo mật và kiểm tra nhanh trên Redis. Nếu tồn tại key này, yêu cầu bị chặn ngay lập tức.

---

## 🔌 4. API Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1/auth`
*   **Headers**: `Authorization: Bearer <accessToken>` + `Cookie: refreshToken=<token>`
*   **Format Phản Hồi**: Hệ thống đồng nhất sử dụng cấu trúc `ApiResponse<T>` chuẩn hóa theo quy ước dự án:
    *   **Thành công**: Trả về Http Code thích hợp cùng JSON body chứa `success: true`, `message`, `data` (payload kết quả), `errors: null` và `timestamp` (ISO 8601).
    *   **Thất bại**: Trả về Http Code lỗi, JSON body chứa `success: false`, `message`, `data: null`, `errors` (mảng chi tiết lỗi với `code`, `field`, `message`) và `timestamp`.

---

### 4.1. API Đăng xuất (Logout)
*   **Method**: `POST`
*   **Path**: `/api/v1/auth/logout`
*   **Auth Level**: `PermitAll` — Endpoint này cố tình **không yêu cầu JWT hợp lệ** để cho phép người dùng đăng xuất ngay cả khi Access Token đã hết hạn. Backend giải mã JWT nhưng bỏ qua kiểm tra `exp`, chỉ trích xuất `userId` và `signature` để blacklist.
*   **Headers**: 
    *   `Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
    *   `Cookie: refreshToken=8f8b5f36-3a78-43d9-9524-34e803c4f2bb`

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Đăng xuất thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T10:45:00Z"
}
```
*Lưu ý: Header trả về sẽ tự động đính kèm `Set-Cookie: refreshToken=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0` để trình duyệt thực hiện xóa bỏ cookie.*

#### Response Thất bại (Ví dụ: Hệ thống bị lỗi kết nối cache - 500 Internal Server Error):
```json
{
  "success": false,
  "message": "Đã xảy ra lỗi trong quá trình xử lý đăng xuất trên hệ thống",
  "data": null,
  "errors": [
    {
      "code": "INTERNAL_SERVER_ERROR",
      "field": null,
      "message": "Không thể kết nối đến máy chủ quản lý phiên"
    }
  ],
  "timestamp": "2026-07-01T10:45:00Z"
}
```

---

### 4.2. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

Mỗi lỗi nghiệp vụ được định nghĩa trong `ErrorCode` Enum với HTTP Status tương ứng:

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt quá giới hạn tần suất truy cập API | `null` |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | Lỗi hệ thống khi xử lý đăng xuất (Redis/DB) | `null` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện nút Đăng xuất (Grayscale Theme)
*   **Vị trí**: Bố trí nút Đăng xuất trong Menu Dropdown của User Profile ở góc trên bên phải màn hình làm việc hoặc ở thanh Sidebar dưới cùng.
*   **Màu sắc đơn sắc**: Tuân thủ Monochrome. Sử dụng nút chữ xám nhạt `text-neutral-500` hoặc icon dạng outline tối giản. Khi di chuột qua (Hover), chuyển nền xám nhạt `bg-neutral-100` hoặc đổi màu chữ sang màu đen để tạo điểm nhấn phản hồi trực quan.

---

### 5.2. Đăng xuất Cưỡng chế Cục bộ & Đồng bộ Multi-tab (Force Local Logout & BroadcastChannel)
Frontend phải được thiết kế để xử lý bất kỳ lỗi mạng hoặc lỗi API nào trả về từ Backend mà vẫn đảm bảo người dùng được thoát ra ngoài. Đồng thời, khi người dùng mở nhiều tab cùng lúc, hệ thống sử dụng **`BroadcastChannel API`** để đồng bộ sự kiện logout giữa tất cả các tab — khi một tab thực hiện logout, các tab khác sẽ tự động nhận event và dọn dẹp state:

```typescript
// Pseudo-code minh họa — trong thực tế sử dụng useRouter(), useQueryClient() hooks.
// Đối với logic ngoài React component, dùng useAuthStore.getState().clearSession().
async function handleLogout() {
  try {
    await api.post('/api/v1/auth/logout');
  } catch (error) {
    console.warn("Backend logout failed or offline, executing force local logout", error);
  } finally {
    useAuthStore.getState().clearSession();
    queryClient.clear();

    // Đồng bộ logout giữa các tab đang mở cùng lúc
    const logoutChannel = new BroadcastChannel('auth_channel');
    logoutChannel.postMessage({ type: 'LOGOUT' });
    logoutChannel.close();

    router.push('/login');
    showToast("Đăng xuất thành công");
  }
}

// Đăng ký listener ở App root (layout.tsx) để các tab khác tự động logout
const channel = new BroadcastChannel('auth_channel');
channel.onmessage = (event) => {
  if (event.data.type === 'LOGOUT') {
    useAuthStore.getState().clearSession();
    queryClient.clear();
    router.push('/login');
  }
};
```

---

### 5.3. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng chuyển dịch màn hình khi người dùng thực hiện Đăng xuất:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    DashboardPage["Màn hình Dashboard <br> /dashboard"]:::screen -->|1. Click Đăng xuất và kích hoạt handleLogout| LogoutAction{Gửi API Logout & Xóa State}:::action
    
    LogoutAction -->|2. Thành công hoặc Thất bại/Offline| ClearedState["Xóa sạch Zustand Store <br> & Hủy Cookies"]:::action
    
    ClearedState --> LoginPage["Màn hình Đăng nhập <br> /login"]:::screen
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

Các sự kiện đăng xuất và vô hiệu hóa JWT trên hệ thống cần được lưu lại cấu trúc phục vụ kiểm toán bảo mật:

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Logout request received | `{"event": "LOGOUT_REQUEST_RECEIVED", "userId": "c8b74f51-..."}` |
| `INFO` | Tokens invalidated successfully | `{"event": "TOKENS_INVALIDATED", "userId": "c8b74f51-...", "jwtSignature": "eyJhbGciOi...", "blacklistTTLSeconds": 432}` |
| `WARN` | Logout call with missing credentials | `{"event": "LOGOUT_MISSING_TOKENS", "ip": "1.2.3.4"}` |
| `WARN` | Logout call with already blacklisted JWT | `{"event": "LOGOUT_ALREADY_BLACKLISTED", "jwtSignature": "eyJhbGciOi..."}` |
| `ERROR` | Redis Pipeline failure during logout | `{"event": "LOGOUT_REDIS_ERROR", "userId": "c8b74f51-...", "error": "Redis connection timeout"}` |

### 6.2. Quy tắc Bảo mật Log
*   Không log toàn bộ chuỗi Access Token JWT lên hệ thống log. Chỉ log **Signature** (phần chữ ký số cuối cùng của JWT) hoặc 10 ký tự đầu tiên để định danh.
*   Thông tin User ID được ghi log rõ ràng dưới dạng định dạng UUID chuẩn, không log thông tin nhạy cảm của User-Agent ngoài phiên bản cơ bản.
