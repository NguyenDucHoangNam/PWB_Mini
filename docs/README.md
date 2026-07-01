# 📚 Tài liệu Thiết kế & Đặc tả Tính năng (PWB MiNi Project Specifications)

Thư mục này chứa toàn bộ tài liệu phân tích nghiệp vụ, thiết kế hệ thống, kiến trúc cơ sở dữ liệu và đặc tả chi tiết API cho dự án **PWB MiNi**.

---

## 🗺️ Sơ đồ Cấu trúc Tài liệu (Directory Tree)

Tài liệu được cấu trúc như sau:

```text
docs/
├── 00_introduction.md                  # Giới thiệu tổng quan dự án, nỗi đau & kiến trúc hệ thống
├── 1. Identity & Access Management/    # Quản lý định danh, tài khoản và phiên làm việc
├── 2. Live Room/                       # Quản lý phòng phát âm thanh trực tuyến & WebRTC
└── 3. Secure Audio Streaming/          # Phân phối, truyền tải & bảo vệ bản quyền âm thanh
```

---

## 📂 Danh mục Tài liệu Chi tiết (Detailed Module Directory)

### [Tổng quan & Giới thiệu](./00_introduction.md)
*   [00_introduction.md](./00_introduction.md)
    *   **Nội dung**: Giới thiệu dự án, các nỗi đau của thị trường (đánh cắp bản quyền demo, âm thanh live room kém chất lượng/lệch pha), sơ đồ kiến trúc hệ thống tổng thể, stack công nghệ chi tiết và đối tượng sử dụng (Host/Listener).

### [1. Identity & Access Management (IAM)](./1.%20Identity%20&%20Access%20Management)
Phân hệ chịu trách nhiệm xác thực, phân quyền, quản lý tài khoản người dùng và bảo mật phiên truy cập.

*   [01_register_otp_verification.md](./1.%20Identity%20&%20Access%20Management/01_register_otp_verification.md)
    *   **Nội dung**: Quy trình đăng ký tài khoản mới cho Producer, gửi mã OTP kích hoạt qua Email sử dụng Outbox Pattern kết hợp Apache Kafka, quản lý thời gian sống và cooldown OTP trên Redis, và dọn dẹp tài khoản rác tự động.
*   [02_user_login.md](./1.%20Identity%20&%20Access%20Management/02_user_login.md)
    *   **Nội dung**: Đăng nhập bằng tài khoản và mật khẩu, xác thực hai lớp, cơ chế cấp phát cặp JWT (Access & Refresh Token), cơ chế chống brute-force và ghi nhật ký truy cập.
*   [03_refresh_access_token.md](./1.%20Identity%20&%20Access%20Management/03_refresh_access_token.md)
    *   **Nội dung**: Quy trình làm mới Access Token từ phía Client khi hết hạn mà không yêu cầu người dùng đăng nhập lại, kiểm tra tính hợp lệ và thu hồi Refresh Token bị rò rỉ.
*   [04_user_logout.md](./1.%20Identity%20&%20Access%20Management/04_user_logout.md)
    *   **Nội dung**: Đăng xuất tài khoản, thu hồi/cho vào danh sách đen (Blacklist) các token đang hoạt động trên Redis để ngăn chặn tấn công Replay Attack.
*   [05_forgot_change_password.md](./1.%20Identity%20&%20Access%20Management/05_forgot_change_password.md)
    *   **Nội dung**: Yêu cầu khôi phục mật khẩu thông qua mã xác thực OTP gửi qua email và quy trình thay đổi mật khẩu trực tiếp từ trang thông tin cá nhân.
*   [06_user_profile_deletion.md](./1.%20Identity%20&%20Access%20Management/06_user_profile_deletion.md)
    *   **Nội dung**: Quy trình xóa tài khoản của người dùng (Hard Delete / Soft Delete) theo yêu cầu, xử lý ràng buộc toàn vẹn dữ liệu liên quan đến các phòng live và file nhạc đã đăng tải.
*   [07_session_management.md](./1.%20Identity%20&%20Access%20Management/07_session_management.md)
    *   **Nội dung**: Quản lý và theo dõi các phiên đăng nhập đang hoạt động của người dùng, giới hạn số lượng thiết bị đăng nhập đồng thời và cho phép đăng xuất từ xa.
*   [08_account_anonymization_job.md](./1.%20Identity%20&%20Access%20Management/08_account_anonymization_job.md)
    *   **Nội dung**: Thiết kế Scheduled Job tự động chạy định kỳ để ẩn danh hóa (Anonymization) thông tin cá nhân của các tài khoản đã bị đánh dấu xóa nhằm tuân thủ quy định bảo mật GDPR.

---

### [2. Live Room](./2.%20Live%20Room)
Phân hệ hỗ trợ kết nối, tương tác thời gian thực và truyền tải âm thanh trực tiếp giữa Host (Producer) và các Listener.

*   [01_create_room.md](./2.%20Live%20Room/01_create_room.md)
    *   **Nội dung**: Quy trình Producer khởi tạo phòng Live Room mới, thiết lập cấu hình phòng (chế độ bảo mật, chất lượng stream, mật khẩu) và lưu thông tin vào CSDL.
*   [02_join_waiting_room.md](./2.%20Live%20Room/02_join_waiting_room.md)
    *   **Nội dung**: Luồng duyệt người tham gia phòng Live, cơ chế hàng đợi phòng chờ (Waiting Room) và phân quyền của Listener.
*   [03_select_source.md](./2.%20Live%20Room/03_select_source.md)
    *   **Nội dung**: Lựa chọn và chuyển đổi nguồn phát âm thanh đầu vào (Microphone, Audio File, Audio Loopback) từ giao diện Host.
*   [04_playback_sync.md](./2.%20Live%20Room/04_playback_sync.md)
    *   **Nội dung**: Đồng bộ hóa thời gian phát (timestamps), trạng thái play/pause/seek của luồng âm thanh giữa Host và tất cả Listener trong phòng trực tuyến.
*   [05_control_delegation.md](./2.%20Live%20Room/05_control_delegation.md)
    *   **Nội dung**: Cơ chế ủy quyền và chuyển giao quyền kiểm soát (Host/Co-host) hoặc cho phép Listener phát biểu trong phòng trực tiếp.
*   [06_webrtc_communication.md](./2.%20Live%20Room/06_webrtc_communication.md)
    *   **Nội dung**: Thiết kế hạ tầng WebRTC truyền phát âm thanh chất lượng cao (Opus codec), quản lý máy chủ Signaling, cơ chế đàm phán SDP và kết nối ICE Candidates.
*   [07_chat_reactions.md](./2.%20Live%20Room/07_chat_reactions.md)
    *   **Nội dung**: Giao tiếp văn bản và tương tác cảm xúc (Reactions) thời gian thực thông qua kết nối WebSocket/WebRTC Data Channel.
*   [08_room_lifecycle_cleanup.md](./2.%20Live%20Room/08_room_lifecycle_cleanup.md)
    *   **Nội dung**: Quản lý vòng đời phòng Live, tự động đóng phòng và dọn dẹp tài nguyên mạng, ngắt kết nối WebSocket/WebRTC khi Host rời phòng hoặc mất kết nối.

---

### [3. Secure Audio Streaming](./3.%20Secure%20Audio%20Streaming)
Phân hệ quản lý việc đăng tải sản phẩm demo, bảo vệ bản quyền âm thanh bằng watermark và phân phối stream an toàn.

*   [01_upload_process_demo.md](./3.%20Secure%20Audio%20Streaming/01_upload_process_demo.md)
    *   **Nội dung**: Luồng tải file nhạc demo gốc định dạng chất lượng cao (WAV/FLAC), xử lý nén/chuyển mã tự động sang định dạng tối ưu để stream (MP3/AAC).
*   [02_generate_voice_tag.md](./3.%20Secure%20Audio%20Streaming/02_generate_voice_tag.md)
    *   **Nội dung**: Quy trình tự động trộn/chèn thẻ giọng nói (Voice Tag) trước khi cho phép stream demo để bảo vệ bản quyền.
*   [03_distribute_share_demo.md](./3.%20Secure%20Audio%20Streaming/03_distribute_share_demo.md)
    *   **Nội dung**: Tạo các liên kết chia sẻ demo (Share Link) đi kèm hạn sử dụng (expiration), số lần nghe tối đa hoặc giới hạn IP truy cập.
*   [04_stream_secure_audio.md](./3.%20Secure%20Audio%20Streaming/04_stream_secure_audio.md)
    *   **Nội dung**: Công nghệ streaming bảo mật (ví dụ: HLS với mã hóa AES-128, dùng Signed URLs/Cookies) để ngăn cản việc tải trực tiếp source nhạc từ trình duyệt.
*   [05_download_original_audio.md](./3.%20Secure%20Audio%20Streaming/05_download_original_audio.md)
    *   **Nội dung**: Quy trình kiểm tra quyền sở hữu hoặc giao dịch mua bán trước khi cho phép tải xuống file âm thanh chất lượng gốc (WAV/FLAC) ban đầu.
*   [06_revoke_shared_link.md](./3.%20Secure%20Audio%20Streaming/06_revoke_shared_link.md)
    *   **Nội dung**: Thu hồi và vô hiệu hóa tức thì liên kết chia sẻ demo trước thời hạn khi Producer muốn hủy bỏ quyền nghe.

---

## 🛠️ Quy chuẩn Cấu trúc Tài liệu Đặc tả (Documentation Structure Guide)

Mỗi file đặc tả tính năng trong dự án cần tuân thủ cấu trúc chuẩn hóa gồm **6 phần chính** sau để đảm bảo tính nhất quán giữa các nhà phát triển:

1.  **💼 1. Business & Requirements (Nghiệp vụ & Yêu cầu)**
    *   Mô tả Nghiệp vụ (User Story / Use Case)
    *   Quy tắc Nghiệp vụ (Business Rules)
    *   Quy tắc Xác thực Dữ liệu (Validation Rules)
    *   Giới hạn Tần suất Truy cập API (IP Rate Limiting)
2.  **🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)**
    *   Sơ đồ tuần tự Mermaid (Sequence Diagram) thể hiện luồng đi của dữ liệu giữa Client, Server, Database, Cache (Redis), Queue/Message Broker (Kafka), Storage (S3), v.v.
    *   Mô tả chi tiết các bước xử lý nghiệp vụ theo từng sơ đồ.
3.  **💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu)**
    *   Cấu trúc các bảng quan hệ (PostgreSQL DDL/ERD) hoặc tài liệu MongoDB.
    *   Bảng định nghĩa cấu trúc dữ liệu lưu trên Redis Cache (Redis Key, Type, Value, TTL, Purpose).
4.  **🔌 4. API Specifications (Đặc tả API)**
    *   Cấu hình chung (Base Path, Headers, ApiResponse JSON Format).
    *   Chi tiết đặc tả từng API endpoint (HTTP Method, Path, Auth Level, Request Payload, Response Payload).
    *   Phụ lục mã lỗi nghiệp vụ (Error Codes) dạng bảng chi tiết.
5.  **🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)**
    *   Thiết kế giao diện & Trải nghiệm (Grayscale monochrome theme, a11y, inline error validation).
    *   Tối ưu hóa hiệu năng & trải nghiệm lập trình viên (Double Submit Prevention, Client-Side Validation, Polling, Reconnection).
    *   Sơ đồ luồng di chuyển màn hình (Screen Flow) bằng Mermaid Graph.
6.  **📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)**
    *   Các điểm ghi log (Log Points) theo các level `INFO`, `WARN`, `ERROR` kèm cấu trúc JSON log.
    *   Quy tắc bảo mật thông tin nhạy cảm khi log (masking email/password/OTP/IP).

---

## ✍️ Hướng dẫn Thêm Tài liệu Mới (Contribution Guide)

Khi phát triển tính năng mới cần bổ sung đặc tả thiết kế, vui lòng thực hiện theo các bước sau:

1.  **Xác định phân hệ phù hợp**:
    *   Chọn một trong các thư mục hiện có (ví dụ: `1. Identity & Access Management`).
    *   Nếu là phân hệ hoàn toàn mới, hãy tạo một thư mục mới có đánh số thứ tự (ví dụ: `4. Payment & Billing`).
2.  **Đặt tên file**:
    *   Tên file dạng viết thường, cách nhau bằng dấu gạch dưới, bắt đầu bằng số thứ tự tăng dần gồm 2 chữ số.
    *   *Ví dụ*: `09_mfa_totp_setup.md`.
3.  **Áp dụng Markdown Template**:
    *   Tạo file và xây dựng nội dung dựa theo **6 phần lớn** trong [Quy chuẩn cấu trúc tài liệu](#quy-chuẩn-cấu-trúc-tài-liệu-đặc-tả-documentation-structure-guide).
4.  **Cập nhật lại README**:
    *   Thêm liên kết trỏ tới tài liệu mới vào phần mục lục tương ứng trong file [README.md](./README.md) này kèm theo tóm tắt nội dung 1-2 dòng ngắn gọn.
