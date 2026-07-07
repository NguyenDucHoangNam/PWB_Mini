# Đặc tả Luồng màn hình & Điều hướng tổng quan (UI Flow & Navigation)

Tài liệu này đặc tả luồng tương tác tổng quan, cấu trúc trang (Site Map), các quy tắc chuyển hướng bảo mật (Redirect Matrix), và quy trình đăng nhập OAuth2 trên các thiết bị Mobile, Tablet, và Desktop.

---

## 1. Sơ đồ luồng trạng thái Người dùng (User State Flow)

Quy trình trạng thái của người dùng từ khi chưa đăng ký cho đến khi tài khoản được kích hoạt, đăng nhập và xóa.

```mermaid
stateDiagram-v2
    [*] --> Anonymous : Truy cập ứng dụng
    Anonymous --> PendingVerification : Đăng ký (Register)
    PendingVerification --> Active : Xác thực OTP thành công
    Active --> Active : Đăng nhập / Hoạt động bình thường
    Active --> PendingDeletion : Yêu cầu xóa tài khoản (Delete)
    PendingDeletion --> Active : Đăng nhập lại trong vòng 30 ngày (Khôi phục)
    PendingDeletion --> Anonymized : Hết hạn 30 ngày (Cron Job chạy)
    Anonymized --> [*] : Dữ liệu bị xóa/ẩn danh hoàn toàn
```

---

## 2. Ma trận tự động điều hướng (Redirect Matrix)

Để đảm bảo trải nghiệm người dùng tối ưu và an toàn, hệ thống tự động kiểm tra trạng thái token và chuyển hướng thích hợp:

| Trạng thái hiện tại | Trang đang cố truy cập | Điều hướng đến | Lý do / UX |
| :--- | :--- | :--- | :--- |
| **Chưa đăng nhập** | `/dashboard`, `/profile`, `/settings` | `/login` | Yêu cầu xác thực. Hiển thị Toast cảnh báo: *"Vui lòng đăng nhập để tiếp tục"* |
| **Đã đăng nhập (Active)** | `/login`, `/register`, `/forgot-password` | `/dashboard` | Đã xác thực, không cần đăng nhập lại. Chuyển hướng âm thầm. |
| **Đang chờ OTP (Pending)** | Đăng nhập bằng pass cũ | `/verify-otp` | Tài khoản chưa kích hoạt. Tự động chuyển hướng đến trang OTP và điền sẵn email. |
| **Phát hiện Trộm Token (Token Theft)** | Bất kỳ trang nào | `/login` | Hệ thống tự động thu hồi tất cả phiên. Chuyển hướng ngay lập tức và hiển thị Toast: *"Phiên đăng nhập hết hạn hoặc bị nghi ngờ, vui lòng đăng nhập lại"* |
| **Chờ xóa (Pending Deletion)** | Thực hiện Đăng nhập | `/account-recovery` | Đăng nhập thành công → ép điều hướng sang trang khôi phục tài khoản. Chỉ cho phép 2 hành động: "Hủy yêu cầu xóa" hoặc "Đăng xuất". |
| **Token hết hạn + Refresh thất bại** | Bất kỳ trang nào | `/login` (trang 401) | Xóa token cũ, hiển thị trang 401 với nút "Đăng nhập lại". |
| **Truy cập bị từ chối (Role)** | Trang yêu cầu role cao hơn | Trang 403 | Hiển thị thông báo không đủ quyền hạn + nút "Quay về Dashboard". |
| **URL không tồn tại** | Đường dẫn bất kỳ không khớp | Trang 404 | Hiển thị thông báo trang không tồn tại + nút "Quay về Trang chủ". |

---

## 3. Quy trình Đăng nhập Google OAuth2 (Sequence Diagram)

Quy trình đăng nhập bằng Google trên đa thiết bị:

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Client as Web App (Chrome/Safari)
    participant Google as Google Identity Services
    participant BE as Backend Server (Spring Boot)
    participant Redis as Redis Cache

    User->>Client: Nhấn nút "Đăng nhập bằng Google"
    Client->>Google: Bật cửa sổ Popup (Desktop) hoặc Chuyển trang (Mobile)
    Google->>User: Yêu cầu xác thực tài khoản Google
    User->>Google: Chọn tài khoản và xác nhận
    Google->>Client: Trả về ID Token (JWT do Google ký)
    Client->>BE: Gửi POST /api/v1/auth/oauth2/google { idToken }
    Note over BE: Kiểm tra chữ ký & Client ID của Google
    BE->>BE: Tìm kiếm email liên kết trong DB
    alt Email chưa tồn tại
        BE->>BE: Tự động sinh Username dạng: email_prefix + 8 ký tự UUID ngẫu nhiên
        BE->>BE: Tạo mới thực thể User với trạng thái ACTIVE
    else Email đã tồn tại
        BE->>BE: Cập nhật AvatarUrl & FullName mới từ Google
    end
    BE->>Redis: Đăng ký Session UUID vào ZSet và lưu Metadata (IP, OS, Browser, Location)
    BE->>Client: Trả về Access Token (JSON) và Cookie `refreshToken` (HttpOnly, Secure, SameSite=Strict)
    Client->>User: Chuyển hướng vào trang Dashboard
```

---

## 4. Tương thích Đa thiết bị (Multi-Device Navigation)

* **Menu Điều hướng (Navbar)**:
  - **Desktop / Tablet ngang**: Hiển thị menu dạng ngang cố định ở phía trên đầu trang.
  - **Mobile / Tablet dọc**: Menu ẩn vào nút **Hamburger Icon** (góc trên bên phải). Khi chạm vào sẽ mở ra một **Drawer** (trượt từ phải sang trái) chiếm 80% chiều rộng màn hình, đảm bảo ngón cái dễ dàng chạm tới tất cả các liên kết.
  - *Chi tiết wireframe*: Xem [03_shell_layout.md — Mục 2. Header / Navbar](./03_shell_layout.md).
* **Xử lý Quay lại (Back Button)**:
  - Các trang con như cập nhật Profile, đổi mật khẩu luôn có nút `< Quay lại` rõ ràng ở góc trên bên trái với kích thước vùng chạm tối thiểu `44x44px`.

---

## 5. Sơ đồ Cấu trúc Trang (Site Map)

Tổng quan tất cả các route thuộc module IAM:

```mermaid
graph TD
    ROOT["/"] --> LANDING["Landing Page"]
    ROOT --> AUTH_GROUP["Auth Pages (Guest)"]
    ROOT --> PROTECTED_GROUP["Protected Pages (Logged-in)"]
    ROOT --> ERROR_GROUP["Error Pages"]

    AUTH_GROUP --> LOGIN["/login"]
    AUTH_GROUP --> REGISTER["/register"]
    AUTH_GROUP --> VERIFY_OTP["/verify-otp"]
    AUTH_GROUP --> FORGOT_PW["/forgot-password"]
    AUTH_GROUP --> RESET_PW["/reset-password?token=..."]

    PROTECTED_GROUP --> DASHBOARD["/dashboard"]
    PROTECTED_GROUP --> PROFILE["/profile"]
    PROTECTED_GROUP --> SESSIONS["/sessions"]
    PROTECTED_GROUP --> RECOVERY["/account-recovery"]

    ERROR_GROUP --> E401["/401 - Phiên hết hạn"]
    ERROR_GROUP --> E403["/403 - Truy cập bị từ chối"]
    ERROR_GROUP --> E404["/404 - Không tìm thấy"]
    ERROR_GROUP --> E500["/500 - Lỗi hệ thống"]
```

### Ghi chú điều hướng:
* **Landing Page** (`/`): Nếu đã đăng nhập → redirect `/dashboard`.
* **Auth Pages**: Nếu đã đăng nhập → redirect `/dashboard`.
* **Protected Pages**: Nếu chưa đăng nhập → redirect `/login`.
* **Account Recovery** (`/account-recovery`): Chỉ hiển thị cho user `PENDING_DELETION`.
* **Profile** (`/profile`): Bao gồm cả form đổi mật khẩu (panel phải Desktop).

