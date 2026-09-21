# Câu 1: Cơ Chế Quản Lý Phiên Đăng Nhập & Mô Hình Dual-Token (Access Token vs Refresh Token)

### ❓ Câu hỏi:
> *"Hệ thống của em quản lý phiên đăng nhập như thế nào? Tại sao em lại chọn mô hình Access Token ngắn hạn kết hợp Refresh Token dài hạn thay vì dùng Session Cookie truyền thống?"*

---

### 💡 Câu trả lời:

#### 1. Mô Hình Quản Lý Phiên Trong PWB_MiNi (Dual-Token Hybrid Model)

Hệ thống `PWB_MiNi` sử dụng **Mô hình Token Kép (Dual-Token)**, kết hợp giữa tính linh hoạt của JWT (Stateless) và tính an toàn của Opaque Token lưu trên Redis (Stateful):

```
                       ┌────────────────────────────────────────────────────────┐
                       │          BỘ ĐÔI QUẢN LÝ PHIÊN (DUAL-TOKEN)             │
                       └────────────────────────────────────────────────────────┘
                                    │                               │
            ┌───────────────────────┴──────┐                ┌───────┴───────────────────────┐
            │   ACCESS TOKEN (Ngắn hạn)    │                │    REFRESH TOKEN (Dài hạn)    │
            ├──────────────────────────────┤                ├───────────────────────────────┤
            │ • Thời hạn: 15 phút (900s)   │                │ • Thời hạn: 14 ngày           │
            │ • Định dạng: JWT (HMAC-SHA512│                │ • Định dạng: 48 bytes ngẫu    │
            │ • Tự chứa: userId, role, jti │                │   nhiên (Opaque String)       │
            │ • Vị trí: Header             │                │ • Vị trí: Cookie HttpOnly,    │
            │   Authorization: Bearer ...  │                │   Secure, SameSite=Lax        │
            │ • Lưu ở client: Memory       │                │ • Lưu ở server: Redis         │
            │   (Zustand store)            │                │   (chỉ lưu mã băm SHA-256)    │
            │ • Kiểm tra: Offline          │                │ • Mục đích: Chỉ dùng để xin   │
            │   (không chạm DB / Redis)    │                │   Access Token mới            │
            └──────────────────────────────┘                └───────────────────────────────┘
```

- **Access Token (15 phút)**:
  - Là chuỗi JWT tự chứa đầy đủ ngữ cảnh (`userId`, `email`, `role`, `status`, `jti`), được ký bằng thuật toán bảo mật **HMAC-SHA512**.
  - Truyền qua HTTP Header `Authorization: Bearer <token>`.
  - **Ưu điểm tối thượng**: Server kiểm tra tính hợp lệ bằng cách giải mã chữ ký số hoàn toàn **Offline**, **không tốn bất kỳ I/O nào truy vấn vào Database hay Redis**. Nhờ đó, tốc độ xử lý các API nghiệp vụ hằng ngày đạt mức mili-giây.
- **Refresh Token (14 ngày)**:
  - Là một chuỗi ngẫu nhiên 48-byte an toàn mật mã học (cryptographically secure), lưu trữ trên server Redis dưới dạng **Mã băm SHA-256** (`iam:refresh:token:<sha256> -> userId`).
  - Được gửi về client qua **Cookie `HttpOnly`, `Secure`, `SameSite=Lax`**. Client JavaScript hoàn toàn không thể chạm tới cookie này, miễn nhiễm 100% trước tấn công đánh cắp token qua lỗ hổng XSS (Cross-Site Scripting).
  - Chỉ dùng cho một nhiệm vụ duy nhất: Khi Access Token hết hạn sau 15 phút, client gọi `POST /api/v1/auth/refresh` để đổi lấy một cặp token mới.

---

#### 2. Tại Sao Chọn Dual-Token Thay Vì Session Cookie Truyền Thống?

Mô hình Session Cookie truyền thống (Stateful Session) từng rất phổ biến, nhưng khi hệ thống mở rộng quy mô (Scale-up) và phục vụ kiến trúc hiện đại, nó bộc lộ 4 nhược điểm lớn so với mô hình Dual-Token:

| Tiêu Chí So Sánh | Session Cookie Truyền Thống (Stateful Session) | Mô Hình Dual-Token (PWB_MiNi Lựa Chọn) | Lợi Ích Cốt Lõi Cho Hệ Thống |
| :--- | :--- | :--- | :--- |
| **Khả năng mở rộng (Scalability & I/O Load)** | **Kém (Nút cổ chai I/O)**.<br>Mỗi HTTP request gửi lên đều bắt buộc server phải truy vấn Redis hoặc Database để đọc Session ID. Khi có 100,000 người dùng online, cụm Session Store sẽ bị nghẽn mạng và cạn I/O. | **Cực cao (Zero DB/Redis I/O)**.<br>99% request sử dụng Access Token (JWT) được server xác thực offline chỉ bằng CPU trong vài micro-giây. Chỉ 1% request gọi refresh token mỗi 15 phút mới chạm tới Redis. | **Giảm 99% áp lực tải I/O** cho cơ sở dữ liệu và cụm cache phân tán. Sẵn sàng mở rộng hàng chục instance backend mà không cần Sticky Session. |
| **Bán kính thiệt hại khi bị lộ (Blast Radius)** | **Nguy hiểm dài hạn**.<br>Nếu Session ID bị lộ qua log mạng hoặc MITM, hacker có toàn quyền kiểm soát tài khoản trong suốt 7 đến 30 ngày cho đến khi session hết hạn. | **Tối thiểu (Tự hủy sau 15 phút)**.<br>Access Token chỉ sống 15 phút. Nếu hacker bắt được token trong bộ nhớ tạm, thời gian khai thác tối đa chỉ là 15 phút là token tự động vô giá trị. | Giảm thiểu tối đa thiệt hại an ninh khi xảy ra sự cố rò rỉ token phía client. |
| **Nguy cơ Tấn công CSRF (Cross-Site Request Forgery)** | **Rủi ro rất cao**.<br>Trình duyệt tự động đính kèm Cookie trong mọi request liên trang. Bắt buộc phải cấu hình và quản lý CSRF Token phức tạp trên mọi form. | **Miễn nhiễm CSRF cho API**.<br>Mọi thao tác nghiệp vụ gửi qua header `Authorization: Bearer`. Trình duyệt không bao giờ tự ý đính kèm header này khi người dùng bị lừa click vào link độc hại từ trang khác. | Cho phép **tắt CSRF an toàn** (`csrf.disable()`) trên Spring Security, giảm tải độ phức tạp của code. |
| **Tương thích Đa nền tảng (Multi-Platform / Mobile)** | **Cồng kềnh**.<br>Cookie chỉ hoạt động mượt mà trên trình duyệt Web. Khi phát triển Mobile App (React Native, Flutter, iOS/Android) hoặc API tích hợp cho bên thứ ba, việc quản trị Cookie Jar rất phức tạp. | **Chuẩn hóa công nghiệp**.<br>Header `Authorization: Bearer` (chuẩn RFC 6750) được hỗ trợ nguyên bản trên Web, Mobile App, Postman và giao tiếp Microservices. | Sẵn sàng mở rộng hệ sinh thái sang ứng dụng di động trong tương lai mà không cần viết lại cơ chế xác thực. |

---

#### 3. Cơ Chế Hoạt Động Tự Động & Mượt Mà Phía Client

Người dùng không bao giờ bị gián đoạn hay phải đăng nhập lại mỗi 15 phút nhờ sự phối hợp giữa **Backend API** và **Axios Interceptor phía Frontend**:

```
[Trình duyệt / Frontend]                                  [Spring Boot Backend]
      │                                                             │
      ├──── 1. Gọi API: GET /api/v1/songs ─────────────────────────>│
      │        Header: Authorization: Bearer <AccessToken>          │ (Hết hạn 15 phút)
      │                                                             │
      │<─── 2. Phản hồi: HTTP 401 Unauthorized ─────────────────────┤
      │                                                             │
      │ ─── 3. [Axios Interceptor tự động bắt mã 401]               │
      │        Tạm dừng request gốc, gọi ngầm:                      │
      ├────    POST /api/v1/auth/refresh ──────────────────────────>│
      │        Cookie: pwb_refresh_token (Tự động gửi)              │ (Redis: Xoay vòng RTR)
      │                                                             │
      │<─── 4. Cấp Access Token mới (trong Body JSON) ──────────────┤
      │                                                             │
      │ ─── 5. Lưu Access Token mới vào Zustand Store               │
      │        Tự động Retry lại request ban đầu:                   │
      ├────    GET /api/v1/songs ──────────────────────────────────>│ (Hợp lệ: 200 OK)
      │        Header: Authorization: Bearer <AccessToken MỚI>      │
      │                                                             │
```

Nhờ cơ chế này, người dùng có thể nghe nhạc hoặc thao tác liên tục trong suốt 14 ngày mà không bao giờ bị văng ra màn hình đăng nhập, trong khi tính bảo mật của từng request vẫn được bảo vệ bởi Access Token ngắn hạn 15 phút.

---

#### 4. Đánh Đổi Kỹ Thuật (Trade-Offs) & Giải Pháp Bù Đắp Trong PWB_MiNi

- **Hạn chế của JWT**: Vì Access Token là Stateless (server không lưu trạng thái), nên về mặt lý thuyết, **không thể thu hồi (revoke) token trước hạn 15 phút**. Nếu một nhân viên bị đuổi việc hoặc người dùng bấm "Đăng xuất" ở phút thứ 2, token đó vẫn còn hợp lệ trong 13 phút tiếp theo.
- **Giải pháp PWB_MiNi khắc phục triệt để**:
  - Khi người dùng gọi API `POST /api/v1/auth/logout`: Server trích xuất mã định danh duy nhất `jti` (JWT ID) từ token và lưu vào Redis Blacklist (`iam:jwt:blacklist:<jti>`) với thời gian sống TTL đúng bằng thời gian còn lại của token.
  - `JwtAuthenticationFilter` kiểm tra nhanh trên Redis: nếu token nằm trong Blacklist, nó bị hủy quyền ngay lập tức.
  - Sau 15 phút, key trên Redis tự động bốc hơi, không gây phình to bộ nhớ của Redis.
