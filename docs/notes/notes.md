# Lộ Trình Đọc & Nắm Bắt Toàn Bộ Mã Nguồn PWB_MiNi

Tài liệu này là **Cẩm nang kiến trúc và kiến thức kỹ thuật chuyên sâu** chuẩn bị cho phỏng vấn Senior Backend / Tech Lead dựa trên mã nguồn thực tế của dự án `PWB_MiNi`. Toàn bộ nội dung chi tiết đã được hệ thống hóa thành 6 giai đoạn độc lập, lưu trữ chi tiết tại thư mục [`docs/interview-notes/`](./interview-notes/).

---

## 🧠 3 Nguyên Lý Cốt Lõi Cần Nhớ

1. **Modular Monolith (1 tiến trình JVM)**: Ranh giới biên dịch phụ thuộc 1 chiều: `liveroom → audio` và `liveroom → iam`. Audio và IAM hoàn toàn độc lập với nhau. Mỗi module chia 4 tầng: `domain` (Java thuần) → `application` (usecase) → `infrastructure` (JPA/Adapter) → `api` (REST/STOMP).
2. **3 Con đường dữ liệu**:
   - **Đường A (REST đồng bộ)**: Client → Controller → UseCase `@Transactional` → Postgres → Response.
   - **Đường B (Bất đồng bộ tin cậy)**: UseCase ghi dữ liệu và ghi bảng `outbox_events` trong cùng một DB transaction → Scheduler quét định kỳ → Kafka → Worker xử lý. Không bao giờ gửi Kafka trực tiếp từ UseCase.
   - **Đường C (Realtime STOMP)**: Chỉ phát frame STOMP đến client sau khi DB transaction đã commit thành công (`TransactionSynchronization.afterCommit`).
3. **Bypass S3 trực tiếp**: File âm thanh/ảnh không đi qua backend. Client upload và phát nhạc trực tiếp từ S3 thông qua Pre-signed URL do backend cấp.

---

## 🗺️ Bản Đồ 6 Giai Đoạn Đọc Mã Nguồn & Ôn Luyện Phỏng Vấn

| Giai Đoạn | Tên Chuyên Đề | Tập Tin Chi Tiết | Số Phần & Điểm Nhấn Kỹ Thuật |
| :---: | :--- | :--- | :--- |
| **01** | **Nền Tảng Dùng Chung**<br>*(Shared Foundation)* | [👉 `01-giai-doan-1-shared-foundation.md`](./interview-notes/01-giai-doan-1-shared-foundation.md)<br>[🎯 *Bộ câu hỏi phỏng vấn Giai đoạn 1*](./interview-questions/giai-doan-1/README.md) | Clean Architecture & DDD, 6 tầng Rate Limiting, S3 Presigned Upload/Download SigV4, Ranged GET 16 Bytes, Flyway vs Hibernate DDL |
| **02** | **Module IAM**<br>*(Xác Thực & User)* | [👉 `02-giai-doan-2-module-iam.md`](./interview-notes/02-giai-doan-2-module-iam.md)<br>[🎯 *Bộ câu hỏi phỏng vấn Giai đoạn 2*](./interview-questions/giai-doan-2/README.md) | Lát cắt dọc chuẩn 4 tầng, Rich Domain Model, Refresh Token Rotation (RTR), Anti-Enumeration, Login Attempt Lock |
| **03** | **Module Audio**<br>*(Bất Đồng Bộ & Xử Lý Nặng)* | [👉 `03-giai-doan-3-module-audio.md`](./interview-notes/03-giai-doan-3-module-audio.md) | **6 Phần Chuyên Sâu**: Bypass S3 & Staging Lifecycle, Transactional Outbox & Polling CDC, Kafka Resiliency, DB Connection Pool Starvation, FFmpeg LUFS & Auto-Ducking, Google TTS & 2-Tier Caching |
| **04** | **Module Live Room**<br>*(Realtime STOMP & WebRTC)* | [👉 `04-giai-doan-4-module-liveroom.md`](./interview-notes/04-giai-doan-4-module-liveroom.md) | **6 Phần Chuyên Sâu**: STOMP Handshake & Security Interceptors, Session Lifecycle & Undo Teardown, Admission Control & Penalties, Anchor Timestamp Sync & Drift Compensation, WebRTC Full-Mesh Signaling & Ephemeral TURN, Resource Reaper & Client Performance (Zustand, Tab Lock, Web Audio API) |
| **05** | **Ops & Security**<br>*(An Toàn, Tối Ưu & Triển Khai)* | [👉 `05-giai-doan-5-ops-security.md`](./interview-notes/05-giai-doan-5-ops-security.md) | **4 Phần Chuyên Sâu**: Redis Lua Script Rate Limiter & Subject Isolation, STOMP Channel Rate Limiting per Destination Bucket, Nginx WebSocket Upgrade & IP Spoofing Defense, Docker Compose VPS 7.6GB RAM & Zero-Downtime Quality Gate |
| **06** | **Tài Liệu Đối Chiếu**<br>*(Technical References)* | [👉 `06-giai-doan-6-tai-lieu-doi-chieu.md`](./interview-notes/06-giai-doan-6-tai-lieu-doi-chieu.md) | Bản đồ hệ thống 66 endpoint & 19 bảng DB, Architecture Overview, Lát cắt dọc đăng nhập, Tài liệu STOMP chi tiết |

---

## 📌 Tóm Tắt Nhanh Các Chuyên Đề Phỏng Vấn Trọng Tâm

### 1. [Giai đoạn 1: Nền Tảng Dùng Chung (Shared Foundation)](./interview-notes/01-giai-doan-1-shared-foundation.md)
- **Clean Architecture & Hexagonal**: Tách 4 tầng độc lập, Ports & Adapters cô lập Domain Java thuần với JPA Database.
- **Rich Domain Model vs Anemic Domain Model**: Constructor private, factory methods tường minh, Value Objects (`EmailAddress`, `Password`).
- **Bảo Mật S3 SigV4**: Ranged GET 16 Bytes soi magic bytes nhị phân, chống Stored XSS bằng `Content-Disposition: attachment`.
- **Flyway Database Migration**: Cơ chế `out-of-order: true`, kiểm soát native SQL index, chống mất mát dữ liệu production.

### 2. [Giai đoạn 2: Lát Cắt Dọc Chuẩn — Module IAM (Xác Thực & User)](./interview-notes/02-giai-doan-2-module-iam.md)
- **Kiến trúc Lát Cắt Dọc (Vertical Slice)**: Theo vết 1 request từ Controller qua UseCase, Repository Adapter tới Database.
- **Refresh Token Rotation (RTR)**: Băm SHA-256 lưu trong Redis, bẫy phát hiện trộm token (Replay Attack) thu hồi toàn bộ phiên đăng nhập.
- **Chống Timing Attack & Account Enumeration**: Phản hồi lỗi đồng nhất, chỉ kiểm tra trạng thái kích hoạt sau khi mật khẩu đã khớp.

### 3. [Giai đoạn 3: Bất Đồng Bộ & Xử Lý File Nặng — Module Audio](./interview-notes/03-giai-doan-3-module-audio.md)
- **Bypass S3 & Staging Lifecycle**: Tách biệt Data Plane và Control Plane, giải phóng hoàn toàn RAM/CPU và Tomcat Worker Threads.
- **Transactional Outbox & CDC Polling**: Đảm bảo tính nhất quán (Atomicity) giữa PostgreSQL và Kafka bằng Lease Lock phân tán.
- **Hạ Tầng Kafka Chống Nghẽn**: Backpressure, xử lý sự kiện trùng lặp (Idempotent Consumer), Dead Letter Queue (DLQ).
- **Phòng Chống Cạn Kiệt DB Connection Pool**: Nhả kết nối database trong suốt thời gian tải file và render FFmpeg nặng.
- **FFmpeg Audio Pipeline**: Chuẩn hóa âm lượng chuẩn truyền hình EBU R128 (-14 LUFS), Auto-Ducking tự động hạ nhạc nền khi có voice tag.
- **Google Cloud TTS & Two-Tier Cache**: Cache 2 tầng (Caffeine In-Memory L1 + AWS S3 L2) triệt tiêu chi phí gọi API Google TTS.

### 4. [Giai đoạn 4: Realtime Trọng Tâm — Module Live Room (STOMP & WebRTC)](./interview-notes/04-giai-doan-4-module-liveroom.md)
- **Hạ Tầng STOMP & Security**: Xác thực JWT tại frame `CONNECT`, phân quyền topic tại `SUBSCRIBE`, transactional event publishing sau commit.
- **Mô Hình Session Cycle & Graceful Teardown**: Cửa sổ hoàn tác 30s (`CANCEL_CLOSING`), thu hồi phiên mượt mà không mất dữ liệu.
- **Kiểm Soát Vào Phòng & Quản Trị Sức Chứa**: Giới hạn 7 người, phân xử Race Condition bằng `SELECT FOR UPDATE`, cấm/đuổi thành viên.
- **Đồng Bộ Phát Nhạc Bằng Anchor Timestamp**: Công thức tính toán tọa độ thời gian `startedAt`, bù lệch đồng hồ (Clock Skew) và bù trôi tua nhịp (Drift Compensation).
- **WebRTC Full-Mesh Signaling**: Backend đóng vai trò Signaling Relay trung chuyển SDP/ICE Candidates qua `/user/queue/**`, hạ tầng TURN tạm thời (Ephemeral Credentials) với TTL 24h.
- **Resource Reaper & Tối Ưu Client**: Scheduler dọn phòng rỗng và phòng vắng chủ với Double-Check Locking; Zustand Selector 60 FPS, chống trùng tab bằng Web `BroadcastChannel`, Speaking Indicator bằng Web Audio API.

### 5. [Giai đoạn 5: An Toàn, Tối Ưu & Triển Khai (Ops & Security)](./interview-notes/05-giai-doan-5-ops-security.md)
- **Redis Token Bucket Rate Limiting**: Script Lua nguyên tử chống khóa bất tử (Immortal Key), phân định Subject (`u:{id}` vs `ip:{ip}`) hóa giải thảm họa Carrier-Grade NAT, thiết kế Fail-Open khi Redis sự cố.
- **STOMP Frame Rate Limiter**: Đánh chặn tại `ChannelInterceptor`, tách 3 bucket (`/rtc/` 400 frames/10s, `/chat/` 30 frames/10s), cửa sổ trượt In-Memory nano-giây, Drop Frame không ngắt socket.
- **Nginx Reverse Proxy**: Chấm dứt TLS tại biên, map header WebSocket Upgrade tránh hỏng HTTP Keep-Alive, fix bẫy redirect 301 tại `/ws`, timeout 3600s giữ sống phòng nghe nhạc, chống giả mạo IP bằng ghi đè `$remote_addr`.
- **Đóng Gói Docker Compose & CI/CD**: Gỡ bỏ Elasticsearch bảo vệ VPS 7.6GB RAM khỏi Linux OOM Killer, mô hình Build-on-Runner Pull-Only, nguyên tắc "Only Nginx Publishes Ports", vòng lặp Quality Gate 300s chờ Flyway hoàn tất.

### 6. [Giai đoạn 6: Tài Liệu Kỹ Thuật Đọc Đối Chiếu (`docs/technical/`)](./interview-notes/06-giai-doan-6-tai-lieu-doi-chieu.md)
- Bộ tài liệu tra cứu kỹ thuật chi tiết theo lát cắt dọc và từng module: `00-ban-do-he-thong.md`, `01-architecture-overview.md`, `02-lat-cat-doc-dang-nhap.md`, `13-realtime-stomp.md`.
