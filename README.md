# 🎵 PWB MiNi (Play With Beats MiNi)

PWB MiNi (Play With Beats MiNi) is a real-time audio collaboration and secure demo sharing platform designed specifically for professional music producers and their partners.

---

## 🌐 Language Options / Lựa chọn ngôn ngữ
- [English Version](#english-version)
- [Tiếng Việt (Vietnamese Version)](#tiếng-việt-vietnamese-version)

---

# English Version

## ⚡ 1. Problem & Solution
Modern music production workflows face two major challenges: protecting intellectual property and maintaining a synchronized listening experience during remote reviews.

*   **Demo Piracy**:
    *   *Problem*: Sending high-quality demo files (WAV, MP3) often results in unauthorized downloads or piracy.
    *   *Solution*: PWB MiNi features automated voice tagging, AES-128 HLS (HTTP Live Streaming) encryption to prevent browser-level downloads, and expiring shared links with IP/play-count restrictions.
*   **Audio Desync & High Latency in Live Rooms**:
    *   *Problem*: Collaborators listening to a demo remotely struggle with desynchronized playback (seeking/pausing is not synced) and laggy voice communications.
    *   *Problem*: Host-delegated control roles are absent, and resource leaks occur when users abruptly drop connection.
    *   *Solution*: Real-time playback synchronization (Play/Pause/Seek) is maintained for all listeners via WebSockets and Redis. Low-latency voice chat (< 150ms) is implemented using WebRTC Mesh P2P. A waiting room controls access, and a room lifecycle manager automatically reclaims server resources upon host departure.
*   **Security & GDPR compliance**:
    *   *Problem*: Active sessions need to be tightly managed to prevent hijacking, and user account deletion must adhere to data protection regulations.
    *   *Solution*: Refresh Token Rotation (RTR) with a Redis blacklist, active session termination, and a scheduled batch job to anonymize deleted accounts within 30 days.

---

## 🛠️ 2. Technology Stack
- **Backend**: Java 21, Spring Boot 4.1.x, Spring Security, Spring WebSockets.
  - **Databases**: PostgreSQL (Relational data), MongoDB (Chat history, audit logs).
  - **Caching & Brokers**: Redis (Session, token blacklist, rate limiting), Apache Kafka (Outbox pattern events, async notification queue).
  - **CDC (Change Data Capture)**: Debezium Embedded Engine (PostgreSQL connector) as primary Outbox processor.
- **Frontend**: Next.js 15+ (App Router), TypeScript (`strict` mode), TailwindCSS v4 (Grayscale Monochrome design system), Zustand (client-state), TanStack Query (server-state).
- **Audio & Streaming**: WebRTC Mesh P2P (Opus Codec) for ultra-low latency voice chat, HLS (HTTP Live Streaming) with AES-128 encryption for secure audio playback.

---

## 📂 3. Directory Structure
```text
PWB_MiNi/
├── backend/            # Spring Boot backend source code (Maven project)
├── frontend/           # Next.js frontend source code (TypeScript, pnpm)
├── docs/               # Detailed system design & features specifications
└── .agents/            # Agent custom instructions & codebase rules
```

---

## 🎯 4. Core System Modules
The system is divided into three core business modules:
1.  **Identity & Access Management (IAM)**: Registration with OTP via Kafka, login with JWT & Refresh Token Rotation (RTR), session management, and a GDPR anonymization cron job.
2.  **Live Room**: Room lifecycle management, waiting room screening, WebSocket-based playback state synchronization, and low-latency WebRTC Mesh P2P audio chat.
3.  **Secure Audio Streaming**: High-quality audio upload and automatic transcoding, automated voice tag insertion, HLS secure streaming (AES-128), and secure shared link creation & revocation.

---

## 📖 5. Documentation Map
A comprehensive index of all feature specifications, database schemas, and sequence diagrams is located in the [docs/README.md](./docs/README.md) file.
For an overview of the system architecture, please see [docs/00_introduction.md](./docs/00_introduction.md).

---

## 🤝 6. Development & Contributing
Please read our [CONTRIBUTING.md](./CONTRIBUTING.md) for details on our branching strategy (Git Flow), commit message guidelines (Conventional Commits), and development workflow.
Refer to [.agents/AGENTS.md](./.agents/AGENTS.md) for coding styles, naming conventions, and API design standards.

---

# Tiếng Việt (Vietnamese Version)

## ⚡ 1. Bài toán & Giải pháp
Quy trình sản xuất âm nhạc hiện đại đối mặt với hai vấn đề sống còn: bảo vệ bản quyền sản phẩm và đồng bộ hóa trải nghiệm lắng nghe trong các buổi làm việc từ xa.

*   **Đánh cắp bản quyền thử nghiệm (Demo Piracy)**:
    *   *Vấn đề*: Gửi file demo gốc (WAV, MP3) cho đối tác dễ dẫn đến việc tải về trái phép hoặc sử dụng lậu.
    *   *Giải pháp*: PWB MiNi tự động chèn Voice Tag thương hiệu, mã hóa luồng stream HLS AES-128 ngăn chặn tải lậu từ Network tab, và hỗ trợ tạo liên kết chia sẻ bảo mật giới hạn thời gian/IP/lượt nghe.
*   **Mất đồng bộ & Trễ cao trong Phòng Live (Live Room)**:
    *   *Vấn đề*: Khi nghe chung demo từ xa, trạng thái phát nhạc (Play/Pause/Seek) của Host và Listener bị lệch pha; đàm thoại trễ cao gây cản trở việc phản hồi; thiếu phân quyền Host/Co-host; rò rỉ tài nguyên server khi kết nối đứt đột ngột.
    *   *Giải pháp*: Đồng bộ hóa playback thời gian thực qua WebSocket & Redis. Kênh thoại WebRTC Mesh P2P trễ cực thấp (< 150ms). Cơ chế phòng chờ (Waiting Room) kiểm duyệt người tham gia và tiến trình tự động dọn dẹp vòng đời phòng (Room Lifecycle Cleanup) giải phóng tài nguyên.
*   **An toàn thông tin & Tuân thủ GDPR**:
    *   *Vấn đề*: Nguy cơ chiếm đoạt phiên đăng nhập và yêu cầu ẩn danh hóa dữ liệu người dùng khi xóa tài khoản.
    *   *Giải pháp*: Cơ chế quay vòng token RTR (Refresh Token Rotation) với blacklist trên Redis, quản lý phiên hoạt động và Scheduled Job chạy ngầm tự động ẩn danh hóa tài khoản sau 30 ngày đóng băng.

---

## 🛠️ 2. Stack Công nghệ
- **Backend**: Java 21, Spring Boot 4.1.x, Spring Security, Spring WebSockets.
  - **Cơ sở dữ liệu**: PostgreSQL (Dữ liệu quan hệ), MongoDB (Nhật ký chat, audit logs).
  - **Caching & Hàng đợi**: Redis (Session, token blacklist, rate limiting), Apache Kafka (Xử lý sự kiện Outbox, gửi email OTP bất đồng bộ).
  - **CDC (Change Data Capture)**: Debezium Embedded Engine (PostgreSQL connector) làm bộ xử lý Outbox chính.
- **Frontend**: Next.js 15+ (App Router), TypeScript (`strict` mode), TailwindCSS v4 (Hệ màu Grayscale Monochrome đơn sắc), Zustand, TanStack Query.
- **Truyền dẫn Âm thanh**: WebRTC Mesh P2P (Opus Codec) thoại trực tiếp trễ siêu thấp, HLS (HTTP Live Streaming) mã hóa AES-128 bảo vệ file phát nhạc tĩnh.

---

## 📂 3. Cấu trúc thư mục dự án
```text
PWB_MiNi/
├── backend/            # Mã nguồn Backend (Dự án Maven, Spring Boot)
├── frontend/           # Mã nguồn Frontend (Next.js, quản lý bằng pnpm)
├── docs/               # Tài liệu đặc tả tính năng và thiết kế chi tiết
└── .agents/            # Quy chuẩn dự án & cấu hình cho AI Agent
```

---

## 🎯 4. Các Phân hệ Cốt lõi
Hệ thống được chia thành 3 phân hệ nghiệp vụ chính:
1.  **Quản lý Định danh & Truy cập (IAM)**: Đăng ký xác thực OTP qua Kafka, đăng nhập JWT kết hợp xoay vòng Refresh Token (RTR), quản lý phiên hoạt động và Job ẩn danh hóa GDPR.
2.  **Phòng Live Room**: Quản lý vòng đời phòng, hàng chờ phòng duyệt (Waiting Room), đồng bộ Playback qua WebSocket và đàm thoại Mesh P2P trễ siêu thấp qua WebRTC.
3.  **Stream Âm thanh Bảo mật (Secure Audio Streaming)**: Tải lên và tự động chuyển mã audio, chèn Voice Tag tự động, stream nhạc mã hóa HLS AES-128 và kiểm soát liên kết chia sẻ bảo mật.

---

## 📖 5. Hướng dẫn Đọc Tài liệu
Mục lục chi tiết của tất cả tài liệu đặc tả nghiệp vụ, API, Database schema nằm tại file [docs/README.md](./docs/README.md).
Xem tài liệu giới thiệu tổng quan kiến trúc và giải pháp tại [docs/00_introduction.md](./docs/00_introduction.md).

---

## 🤝 6. Phát triển & Đóng góp
Vui lòng đọc kỹ [CONTRIBUTING.md](./CONTRIBUTING.md) để nắm rõ quy trình Git Flow, quy chuẩn đặt tên nhánh và định dạng viết commit (Conventional Commits).
Xem thêm các quy chuẩn viết code backend, frontend và quy tắc thiết kế tại [.agents/AGENTS.md](./.agents/AGENTS.md).
