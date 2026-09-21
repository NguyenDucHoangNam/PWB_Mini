# Bộ Câu Hỏi Phỏng Vấn: Giai Đoạn 2 — Module IAM (Xác Thực & Quản Lý Phiên)

> Thư mục này tổng hợp các câu hỏi phỏng vấn thực chiến chuyên sâu cấp độ Senior Backend / Tech Lead dành cho **Giai đoạn 2: Module IAM**.  
> Tài liệu gốc đối chiếu: [`docs/interview-notes/02-giai-doan-2-module-iam.md`](../../interview-notes/02-giai-doan-2-module-iam.md) và [`docs/technical/02-lat-cat-doc-dang-nhap.md`](../../technical/02-lat-cat-doc-dang-nhap.md)

---

## 📋 Danh Sách Câu Hỏi Ôn Luyện

| STT | Câu Hỏi Trọng Tâm | Trạng Thái | File Chi Tiết |
| :---: | :--- | :---: | :--- |
| **01** | Hệ thống của em quản lý phiên đăng nhập như thế nào? Tại sao em lại chọn mô hình Access Token ngắn hạn kết hợp Refresh Token dài hạn thay vì dùng Session Cookie truyền thống? | ✅ Đã soạn | [👉 `cau-01-quan-ly-phien-va-access-refresh-token.md`](./cau-01-quan-ly-phien-va-access-refresh-token.md) |
| **02** | Bản chất JWT là Stateless, vậy hệ thống của bạn xử lý bài toán Đăng xuất (Logout) và Thu hồi token như thế nào? Cơ chế Refresh Token Rotation (RTR) kết hợp bẫy phát hiện token bị đánh cắp hoạt động ra sao? | ✅ Đã soạn | [👉 `cau-02-logout-blacklist-va-refresh-token-rotation.md`](./cau-02-logout-blacklist-va-refresh-token-rotation.md) |
| **03** | Trong dự án của bạn, bạn đã phân tách Authentication và Authorization như thế nào? Tại sao RBAC là chưa đủ, và bạn đã giải quyết bài toán Resource Ownership để ngăn chặn lỗ hổng IDOR ra sao? Hệ thống phân biệt rạch ròi giữa mã lỗi HTTP 401 và HTTP 403 như thế nào? | ✅ Đã soạn | [👉 `cau-03-authentication-vs-authorization.md`](./cau-03-authentication-vs-authorization.md) |
| **04** | Trong chuỗi Spring Security Filter Chain của dự án PWB_MiNi, quá trình Authentication và Authorization diễn ra theo thứ tự nào? Tại sao khi JwtAuthenticationFilter phát hiện token hết hạn hoặc không hợp lệ, bộ lọc này không ném lỗi HTTP 401 ngay lập tức mà vẫn gọi chain.doFilter cho request đi tiếp? | ✅ Đã soạn | [👉 `cau-04-thu-tu-filter-chain-va-co-che-fail-safe-jwt.md`](./cau-04-thu-tu-filter-chain-va-co-che-fail-safe-jwt.md) |
| **05** | Ở các luồng Đăng nhập và Quên mật khẩu, em đã làm gì để ngăn chặn kẻ xấu dò quét danh sách email (User Enumeration) hoặc tấn công dò mật khẩu (Brute-force)? | ⏳ Chờ soạn | `cau-05-anti-enumeration-va-brute-force.md` |
| **06** | Em lưu trữ mật khẩu người dùng trong cơ sở dữ liệu như thế nào? Tại sao lại dùng BCrypt mà không dùng các thuật toán như MD5 hay SHA-256? | ⏳ Chờ soạn | `cau-06-luu-tru-mat-khau-bcrypt-va-salt.md` |
| **07** | Khi tích hợp đăng nhập bằng Google OAuth 2.0, điều gì sẽ xảy ra nếu người dùng đăng nhập bằng một email đã được đăng ký bằng mật khẩu từ trước? Xử lý liên kết tài khoản an toàn ra sao? | ⏳ Chờ soạn | `cau-07-google-oauth-va-account-linking.md` |
| **08** | Khi xử lý một API vừa phải gọi dịch vụ bên ngoài mất vài giây (như upload ảnh lên S3 hoặc gửi email) vừa phải ghi Database, em quản lý Transaction như thế nào để không làm nghẽn Connection Pool? | ⏳ Chờ soạn | `cau-08-quan-ly-transaction-va-connection-pool.md` |
