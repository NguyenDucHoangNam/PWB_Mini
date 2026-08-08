# 🎵 PWB MiNi (Play With Beats MiNi)

Nền tảng **review và trao đổi trong quá trình làm sản phẩm âm nhạc**. Producer tải bản demo lên, tự động gắn Voice Tag nhận diện thương hiệu, rồi mời đối tác vào **Live Room** để cùng nghe — nghe đúng chất lượng gốc, ai cũng dừng nhạc được để bàn, và feedback được ghim vào đúng giây trong bài.

> **Trạng thái**: Đồ án đang phát triển. Phần [Định hướng phát triển](#-9-định-hướng-phát-triển) liệt kê rõ những gì **chưa** được triển khai, để tránh nhầm lẫn với các tính năng đã chạy được.

---

## 🌐 Language / Ngôn ngữ

- [Tiếng Việt](#-1-bài-toán--giải-pháp) (bên dưới)
- [English](#english-version)

---

## ⚡ 1. Bài toán & Giải pháp

### Vì sao không dùng Google Meet hay Zoom?

Khi cần cho đối tác nghe thử bản demo, cách phổ biến hiện nay là gọi Meet/Zoom rồi **chia sẻ màn hình kèm âm thanh**. Cách này hỏng ở bốn điểm, và đó chính là lý do Live Room tồn tại:

| Vấn đề khi dùng Meet/Zoom | Giải pháp của PWB MiNi |
|---|---|
| **Âm thanh bị nén, giật, rớt nhịp.** Nhạc phải đi qua đường truyền tối ưu cho giọng nói, nên chi tiết ở dải cao và dải trầm — thứ cần đánh giá nhất — bị mất. | Không truyền âm thanh qua cuộc gọi. **Mỗi người phát file gốc trên máy mình**, server chỉ đồng bộ *trạng thái* playback qua WebSocket (STOMP). Chất lượng nghe được đúng bằng chất lượng file. |
| **Nghe không đồng bộ.** Người chia sẻ màn hình đang ở giây 45, người nghe do độ trễ mạng lại đang ở giây 42 — góp ý "chỗ này" thành ra trỏ vào hai chỗ khác nhau. | Trạng thái playback (play / pause / seek / volume) được đồng bộ tới toàn phòng, kèm số thứ tự (sequence number) để các máy không xử lý lệch thứ tự sự kiện. |
| **Chỉ người chia sẻ mới thao tác được.** Muốn dừng lại bàn một đoạn phải nói "dừng giúp mình", chờ người kia bấm. Việc bàn bạc bị ngắt quãng liên tục. | **Bất kỳ ai trong phòng cũng dừng, tua và chỉnh âm lượng được** — không cần xin phép, không cần là chủ phòng. Ai nghe thấy vấn đề thì người đó bấm dừng ngay. |
| **Feedback trôi mất.** Góp ý nói ra bằng miệng hoặc gõ vào chat, xong buổi không còn ai nhớ nó ứng với đoạn nào của bài. | **Bình luận neo theo mốc thời gian** — mỗi ghi chú gắn với đúng giây trong bài hát, xem lại được sau buổi review. |

### Các vấn đề khác

| Vấn đề | Giải pháp đã triển khai |
|---|---|
| **Demo bị dùng lậu.** Gửi file demo gốc cho đối tác dễ dẫn tới việc sử dụng trái phép. | Tự động chèn **Voice Tag** thương hiệu vào bản demo bằng FFmpeg, lặp lại theo chu kỳ cấu hình được. Âm lượng tag được khớp theo độ lớn đo được của bài hát và có ducking, nên tag nghe rõ mà không đè lên nhạc. File chỉ phát qua **presigned URL** ngắn hạn từ S3. |
| **Trao đổi qua chat text quá chậm** khi đang nghe. | Kênh thoại **WebRTC Mesh P2P** (codec Opus) chạy song song với nhạc, tín hiệu SDP/ICE đi qua backend, luồng âm thanh đi thẳng giữa các trình duyệt. |
| **Người lạ vào phòng** nghe được sản phẩm chưa phát hành. | Cơ chế **phòng chờ (Waiting Room)**: người tham gia gửi yêu cầu, chủ phòng duyệt. Kèm công cụ kiểm duyệt: kick (có cooldown), tắt mic từ xa. |
| **Phiên đăng nhập bị chiếm đoạt.** | **Refresh Token Rotation** với blacklist trên Redis, khóa tài khoản và khóa theo IP khi đăng nhập sai nhiều lần, rate limit **fail-closed** (Redis chết thì từ chối, không thả request qua). |

---

## 🛠️ 2. Stack Công nghệ

### Backend
| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ / Framework | Java 21, **Spring Boot 3.5.16** |
| Kiến trúc | Modular Monolith (Maven multi-module) |
| Bảo mật | Spring Security, JWT (jjwt), Google Identity |
| Realtime | Spring WebSocket + STOMP, SockJS fallback |
| CSDL quan hệ | PostgreSQL 16 + Flyway migration |
| Cache & khóa phân tán | Redis 7.2 |
| Message broker | Apache Kafka 7.6 (KRaft mode) |
| Tìm kiếm | Elasticsearch 8.12 (có fallback về Postgres) |
| Lưu trữ file | AWS S3 (presigned URL) |
| Xử lý audio | FFmpeg / FFprobe |
| Text-to-Speech | Google Cloud TTS (tạo Voice Tag từ văn bản) |

### Frontend
| Thành phần | Công nghệ |
|---|---|
| Framework | **Next.js 16** (App Router), React 19 |
| Ngôn ngữ | TypeScript (chế độ `strict`) |
| Giao diện | TailwindCSS v4, shadcn/ui, Base UI, Framer Motion |
| State phía client | Zustand |
| State phía server | TanStack Query + Axios |
| Realtime | `@stomp/stompjs` + SockJS |
| Đa ngôn ngữ | next-intl (Tiếng Việt / English) |
| Form & kiểm tra dữ liệu | React Hook Form + Zod |
| Kiểm thử | Vitest + Testing Library |

---

## 🏛️ 3. Kiến trúc

Backend là một **modular monolith**: các module nghiệp vụ tách biệt về code và schema database, nhưng đóng gói và triển khai thành một ứng dụng duy nhất.

```
Backend/
├── bootstrap/                  # Điểm khởi động, cấu hình application*.yml
├── shared/
│   ├── shared-kernel/          # Kiểu dữ liệu dùng chung, mã lỗi
│   ├── shared-web/             # Xử lý exception, rate limit, CORS
│   └── shared-infrastructure/  # Storage (S3), Mail, Outbox, Search
└── modules/
    ├── iam/                    # Định danh & phân quyền
    ├── audio/                  # Bài hát, Voice Tag, xử lý FFmpeg
    └── liveroom/               # Phòng nghe chung, chat, WebRTC
```

**Giao tiếp bất đồng bộ** dùng **Outbox Pattern**: sự kiện được ghi vào bảng outbox trong **cùng transaction** với thay đổi nghiệp vụ, sau đó một scheduler đọc và đẩy sang Kafka. Bản ghi được đánh dấu `lease_until` khi xử lý, nên không bị publish trùng. Có retry với backoff và Dead Letter Topic.

Luồng dùng Outbox: gửi email OTP, đánh index tìm kiếm.

**Tìm kiếm** đi qua Elasticsearch nhưng luôn có đường lui: khi cluster không phản hồi (timeout đặt rất ngắn — 1s kết nối, 2s socket), truy vấn tự động chuyển về Postgres. Vì vậy health check của Elasticsearch bị **tắt có chủ đích** — search chậm không được phép làm cả ứng dụng bị đánh dấu unhealthy.

---

## 🎯 4. Các Phân hệ

### 4.1. IAM — Định danh & Truy cập
- Đăng ký kèm xác thực **OTP qua email** (gửi bất đồng bộ qua Kafka)
- Đăng nhập JWT + **Refresh Token Rotation**
- Đăng nhập bằng Google
- Quên mật khẩu / đặt lại mật khẩu qua token có thời hạn
- Khóa tài khoản theo số lần sai, và khóa theo IP
- Rate limit từng endpoint trên Redis
- Quản lý hồ sơ, ảnh đại diện (S3 presigned URL, TTL 15 phút)
- Trang quản trị người dùng cho ADMIN
- Vai trò: `USER`, `PRO`, `ADMIN`

### 4.2. Audio — Bài hát & Voice Tag
- Upload bài hát **trực tiếp lên S3** qua presigned URL (không đi qua server), hỗ trợ MP3 / WAV / FLAC, tối đa 200 MB
- Voice Tag từ hai nguồn: **tải file lên** hoặc **tạo bằng Google TTS**
- Ghép Voice Tag vào bài hát bằng FFmpeg: khớp độ lớn (loudness matching), ducking nhạc nền, chèn theo chu kỳ
- Xử lý bất đồng bộ qua Kafka, vòng đời trạng thái `UPLOADED → PROCESSING → PROCESSED / FAILED` kèm retry
- Tìm kiếm bài hát và voice tag (hỗ trợ tiếng Việt có dấu)

📖 Chi tiết: [docs/audio-module.md](docs/audio-module.md)

### 4.3. Live Room — Phòng review sản phẩm âm nhạc

Phòng làm việc để producer và đối tác cùng nghe một bản demo và trao đổi về nó. Xem [phần 1](#vì-sao-không-dùng-google-meet-hay-zoom) để hiểu vì sao module này không thay thế được bằng một cuộc gọi Meet/Zoom.

**Đồng bộ nghe chung**
- Đồng bộ trạng thái playback (play / pause / seek / volume) qua STOMP, có sequence number chống xử lý lệch thứ tự
- **Mọi người trong phòng đều điều khiển được** playback — quyền dừng/tua không dành riêng cho chủ phòng. Riêng thao tác *phát* nhạc bị chặn khi chủ phòng vắng mặt, tránh việc phòng tự chạy khi không còn người chịu trách nhiệm
- Chọn bài từ thư viện của chủ phòng, link phát có hiệu lực 1 giờ

**Trao đổi & ghi nhận feedback**
- **Bình luận neo theo mốc thời gian** của bài hát (tối đa 200 ký tự, 200 bình luận/bài) — feedback không trôi mất sau buổi review
- Chat văn bản trong phòng (lưu ở PostgreSQL, giữ 90 ngày)
- Thoại **WebRTC Mesh P2P** chạy song song với nhạc, sức chứa mặc định 7 người/phòng

**Quản lý phòng**
- Tạo phòng (yêu cầu vai trò `PRO`), tham gia bằng **mã phòng**
- **Phòng chờ**: yêu cầu tham gia cần chủ phòng duyệt
- Vòng đời phòng: tự dọn phòng trống sau 5 phút, debounce khi chủ phòng rớt mạng, cửa sổ hoàn tác việc kết thúc phòng
- Kiểm duyệt: kick (cooldown 5 phút), tắt mic từ xa (không ép bật lại — tôn trọng quyền riêng tư)
- Rate limit ngay ở tầng STOMP frame, tách hạn mức riêng cho chat và tín hiệu WebRTC

📖 Chi tiết: [docs/liveroom-module.md](docs/liveroom-module.md)

---

## 📂 5. Cấu trúc thư mục

```text
PWB_MiNi/
├── Backend/            # Spring Boot, Maven multi-module
├── Frontend/           # Next.js 16, quản lý bằng pnpm
│   └── src/
│       ├── app/        # App Router: (auth) (dashboard) (liveroom) (public)
│       ├── features/   # auth, liveroom, voice, profile, landing, contact
│       ├── components/ # UI dùng chung
│       ├── stores/     # Zustand
│       └── i18n/       # next-intl
├── docs/               # Tài liệu đặc tả & kế hoạch triển khai
├── docker/             # Script khởi tạo cho container hạ tầng
├── .agents/            # Quy chuẩn code cho AI agent
└── docker-compose.yml  # Hạ tầng phục vụ phát triển cục bộ
```

---

## 🚀 6. Chạy dự án cục bộ

### Yêu cầu môi trường
- JDK 21
- Node.js 20+ và pnpm
- Docker Desktop
- FFmpeg + FFprobe (có trong PATH, hoặc trỏ bằng `PWB_AUDIO_FFMPEG_DIR`)

### Bước 1 — Khởi động hạ tầng

```bash
docker compose up -d
```

Lệnh này dựng PostgreSQL (cổng **5433**), Redis, Kafka và Elasticsearch.

### Bước 2 — Cấu hình biến môi trường

Tạo file `.env` ở **thư mục gốc** repo (đã nằm trong `.gitignore`). Các biến tối thiểu:

```dotenv
POSTGRES_PASSWORD=pwb_password
REDIS_PASSWORD=pwb_redis_secret_pass

# Đăng nhập Google (tùy chọn khi phát triển)
GOOGLE_CLIENT_ID=

# Gửi email OTP
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=

# AWS S3 — bắt buộc để upload/phát nhạc hoạt động
STORAGE_S3_BUCKET=
STORAGE_S3_REGION=ap-southeast-1
STORAGE_S3_ACCESS_KEY=
STORAGE_S3_SECRET_KEY=
```

### Bước 3 — Chạy Backend

```bash
mvn -f Backend/pom.xml -pl bootstrap -am spring-boot:run \
  -Dspring-boot.run.workingDirectory="<đường dẫn tuyệt đối tới repo>"
```

Backend chạy ở `http://localhost:8080`. Flyway tự động áp dụng migration khi khởi động.

- `-am` là **bắt buộc**: thiếu nó, Maven lấy các module `audio` / `liveroom` từ `~/.m2` thay vì từ source, và ứng dụng sẽ chạy trên code cũ mà không báo gì.
- `workingDirectory` cũng **bắt buộc**: `spring-boot:run` fork tiến trình với thư mục làm việc là `Backend/bootstrap`, nên thư viện dotenv không tìm thấy file `.env` ở gốc repo.

Profile `dev` seed sẵn tài khoản demo (xem `application-dev-users.yml`), gồm cả tài khoản vai trò `PRO` cần cho Live Room.

### Bước 4 — Chạy Frontend

```bash
cd Frontend
pnpm install
pnpm dev
```

Frontend chạy ở `http://localhost:3000`.

Nếu backend đang chạy ở cổng khác, đặt biến trước khi chạy:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1 pnpm dev
```

### 🔧 Xử lý sự cố thường gặp

<details>
<summary><b>Windows: mọi kết nối mạng của JVM đều lỗi <code>Unable to establish loopback connection</code></b></summary>

Xảy ra khi đường dẫn thư mục tạm chứa dấu cách. Mọi `Selector` của NIO đều hỏng, kéo theo Kafka, S3 và Elasticsearch client cùng chết. Thêm cờ:

```bash
-Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir="
```
</details>

<details>
<summary><b>IntelliJ: chạy được nhưng S3 lỗi, Redis sai mật khẩu, OTP không gửi</b></summary>

`spring-dotenv` chỉ đọc file `.env` nằm trong **thư mục làm việc của tiến trình**. IntelliJ mặc định đặt nó theo module, không phải gốc repo, nên file `.env` ở gốc không được nạp và ứng dụng chạy bằng các giá trị mặc định trong `application-dev.yml`.

Triệu chứng rời rạc và trông như nhiều lỗi khác nhau: mọi lệnh xác thực trả `IAM_034` (Redis sai mật khẩu, throttler fail-closed), mọi URL presigned trả `STORAGE_005` (thiếu AWS credentials).

Sửa: mở run config `PwbApplication` → đặt **Working directory** = thư mục gốc repo.
</details>

<details>
<summary><b><code>mvn clean install -DskipTests</code> lỗi ở module <code>pwb-iam</code></b></summary>

`-DskipTests` vẫn **biên dịch** test. Dùng `-Dmaven.test.skip=true` để bỏ qua hẳn.
</details>

<details>
<summary><b>Next.js báo "couldn't find the Next.js package"</b></summary>

Symlink của pnpm trỏ sai sau khi thư mục được đổi tên. Cài lại sạch:

```bash
cd Frontend && rm -rf node_modules .next && pnpm install
```

Phải xóa cả `.next` — cache hỏng vẫn tồn tại sau khi cài lại và làm các route động trả lỗi 500.
</details>

---

## 🧪 7. Kiểm thử

```bash
# Backend — unit test
mvn -f Backend/pom.xml test

# Backend — integration test (dùng Testcontainers, cần Docker)
mvn -f Backend/pom.xml verify

# Frontend
cd Frontend && pnpm vitest run
```

Quy ước: file `*Test.java` là unit test (chạy bởi Surefire), `*IT.java` là integration test (chạy bởi Failsafe).

---

## 📖 8. Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/audio-module.md](docs/audio-module.md) | Hướng dẫn nghiệp vụ module Audio |
| [docs/liveroom-module.md](docs/liveroom-module.md) | Thiết kế kỹ thuật Live Room |
| [docs/RATE_LIMITING.md](docs/RATE_LIMITING.md) | Cơ chế giới hạn tần suất |
| [docs/deployment-plan.md](docs/deployment-plan.md) | Kế hoạch triển khai production |
| [Frontend/ARCHITECTURE.md](Frontend/ARCHITECTURE.md) | Kiến trúc frontend |
| [.agents/AGENTS.md](.agents/AGENTS.md) | Quy chuẩn code & đặt tên |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Git Flow & quy ước commit |

---

## 🗺️ 9. Định hướng phát triển

Những mục dưới đây **chưa được triển khai**. Chúng nằm trong thiết kế ban đầu và được giữ lại ở đây để theo dõi, không phải mô tả trạng thái hiện tại.

- [ ] **Streaming HLS mã hóa AES-128** — hiện phát nhạc qua presigned URL của S3, chưa có phân đoạn HLS hay mã hóa
- [ ] **Liên kết chia sẻ bảo mật** — giới hạn theo thời hạn / IP / số lượt nghe
- [ ] **Job ẩn danh hóa dữ liệu theo GDPR** — tự động xử lý tài khoản đã xóa sau 30 ngày
- [ ] **TURN server** — hiện chỉ cấu hình STUN, nên thoại WebRTC sẽ thất bại với người dùng sau NAT đối xứng
- [ ] **Scale ngang backend** — broker STOMP đang chạy in-memory (`enableSimpleBroker`), cần chuyển sang broker ngoài (RabbitMQ / ActiveMQ) mới chạy được nhiều instance

---

## 🤝 10. Đóng góp

Xem [CONTRIBUTING.md](CONTRIBUTING.md) để nắm quy trình Git Flow, quy ước đặt tên nhánh và định dạng commit (Conventional Commits). Quy chuẩn viết code nằm ở [.agents/AGENTS.md](.agents/AGENTS.md).

---
---

# English Version

A platform for **reviewing and discussing music while it is still being made**. Producers upload a demo, watermark it automatically with a branded voice tag, then invite collaborators into a **Live Room** to listen together — at original file quality, where anyone can stop the music to talk, and feedback is pinned to the exact second it refers to.

> **Status**: Active student project. See [Roadmap](#-9-roadmap-en) for what is **not** yet implemented.

## ⚡ 1. Problem & Solution

### Why not just use Google Meet or Zoom?

The usual way to play a demo for a collaborator is a video call with **screen sharing plus audio**. That breaks in four ways, and those four are exactly why Live Room exists:

| Problem with Meet/Zoom | How PWB MiNi solves it |
|---|---|
| **Audio is compressed and stutters.** Music travels over a pipe tuned for speech, so the highs and lows — the very things being judged — are gone. | Audio never goes through the call. **Each person plays the original file locally** and the server synchronizes only the playback *state* over WebSocket (STOMP). What you hear is exactly what the file contains. |
| **Listeners are out of sync.** The presenter is at 0:45 while network lag puts a listener at 0:42, so "this part right here" means two different parts. | Playback state (play / pause / seek / volume) is broadcast room-wide with a sequence number, so clients never apply events out of order. |
| **Only the presenter can touch the controls.** Stopping to discuss a bar means asking someone else to pause and waiting. | **Anyone in the room can pause, seek and adjust volume** — no permission needed, no host privilege. Whoever hears the problem is the one who stops the music. |
| **Feedback evaporates.** Notes said out loud or typed into chat lose their connection to the part of the track they were about. | **Comments are anchored to a timestamp** — each note is pinned to a specific second in the song and survives the session. |

### Other problems

| Problem | Implemented solution |
|---|---|
| **Demo misuse.** Sending raw demo files invites unauthorized use. | Automatic **voice tag** watermarking via FFmpeg at a configurable interval. Tag loudness is matched against the song's measured loudness with ducking, so it stays audible without burying the music. Playback happens only through short-lived S3 presigned URLs. |
| **Text chat is too slow** while listening. | **WebRTC Mesh P2P** voice (Opus) running alongside the music. SDP/ICE signaling goes through the backend; audio flows browser-to-browser. |
| **Uninvited listeners** hearing unreleased work. | **Waiting room** — joiners request access and the host approves. Plus moderation: kick with cooldown and remote mic mute. |
| **Session hijacking.** | **Refresh Token Rotation** with a Redis blacklist, per-account and per-IP login lockout, and **fail-closed** rate limiting (if Redis is down, requests are rejected rather than waved through). |

## 🛠️ 2. Tech Stack

**Backend** — Java 21, Spring Boot 3.5.16 (modular monolith), Spring Security, Spring WebSocket/STOMP, PostgreSQL 16 + Flyway, Redis 7.2, Apache Kafka 7.6 (KRaft), Elasticsearch 8.12 with Postgres fallback, AWS S3, FFmpeg, Google Cloud TTS.

**Frontend** — Next.js 16 (App Router), React 19, TypeScript strict, TailwindCSS v4, shadcn/ui, Zustand, TanStack Query, `@stomp/stompjs`, next-intl, React Hook Form + Zod, Vitest.

## 🏛️ 3. Architecture

A **modular monolith**: business modules are isolated in code and database schema but ship as one deployable.

```
Backend/
├── bootstrap/                  # Entry point, application*.yml
├── shared/{kernel,web,infrastructure}
└── modules/{iam,audio,liveroom}
```

Async communication uses the **Outbox Pattern** — events are written in the same transaction as the business change, then a leased scheduler publishes them to Kafka with retry and a dead letter topic. Used for OTP email delivery and search indexing.

Search goes through Elasticsearch but always has a fallback: with deliberately short timeouts (1s connect, 2s socket), a slow cluster degrades to a Postgres query. Its health check is therefore **disabled on purpose** — degraded search must never mark the whole application unhealthy.

## 🎯 4. Modules

- **IAM** — registration with email OTP (delivered async via Kafka), JWT + refresh token rotation, Google sign-in, password reset, account/IP lockout, per-endpoint Redis rate limiting, profile & avatar (S3 presigned, 15-min TTL), admin user management. Roles: `USER`, `PRO`, `ADMIN`.
- **Audio** — direct-to-S3 upload via presigned URL (MP3/WAV/FLAC, up to 200 MB), voice tags from file upload or Google TTS, FFmpeg watermarking with loudness matching and ducking, async Kafka processing with `UPLOADED → PROCESSING → PROCESSED / FAILED` lifecycle and retry, Vietnamese-aware search.
- **Live Room** — a review room, not a meeting room. Playback state (play / pause / seek / volume) is synced over STOMP with sequence numbers, and **every participant can control it** — only *starting* playback is blocked while the host is away. Feedback is captured as **timestamp-anchored track comments** (200 chars, 200 per song) rather than lost in chat. Also: code-based join gated by a waiting room, room creation restricted to the `PRO` role, empty-room cleanup after 5 minutes, in-room text chat (PostgreSQL, 90-day retention), WebRTC mesh voice (default capacity 7), moderation (kick with cooldown, remote mute), and STOMP-frame-level rate limiting.

## 🚀 5. Running Locally

**Prerequisites**: JDK 21, Node.js 20+ with pnpm, Docker Desktop, FFmpeg/FFprobe on PATH.

```bash
# 1. Infrastructure (Postgres on port 5433, Redis, Kafka, Elasticsearch)
docker compose up -d

# 2. Create .env at the repo root — see the Vietnamese section for required keys

# 3. Backend → http://localhost:8080
mvn -f Backend/pom.xml -pl bootstrap -am spring-boot:run \
  -Dspring-boot.run.workingDirectory="<absolute path to repo>"

# 4. Frontend → http://localhost:3000
cd Frontend && pnpm install && pnpm dev
```

Both flags on the Maven command are required. Without `-am`, Maven resolves the `audio` and `liveroom` modules from `~/.m2` instead of source and the app silently runs stale code. Without `workingDirectory`, `spring-boot:run` forks with its working directory set to `Backend/bootstrap`, so dotenv never finds the repo-root `.env`.

Flyway applies migrations on startup. The `dev` profile seeds demo accounts including the `PRO` role that Live Room requires.

See the [Vietnamese troubleshooting section](#-xử-lý-sự-cố-thường-gặp) for three non-obvious local-setup failures on Windows.

## 🧪 6. Testing

```bash
mvn -f Backend/pom.xml test      # unit tests (Surefire, *Test.java)
mvn -f Backend/pom.xml verify    # integration tests (Failsafe, *IT.java, needs Docker)
cd Frontend && pnpm vitest run
```

## 🗺️ 9. Roadmap (EN)

Not yet implemented — listed for tracking, not as a description of current state:

- [ ] HLS streaming with AES-128 encryption (playback currently uses S3 presigned URLs)
- [ ] Secure share links with expiry / IP / play-count limits
- [ ] GDPR anonymization job for deleted accounts
- [ ] TURN server (STUN only today, so WebRTC voice fails behind symmetric NAT)
- [ ] Horizontal backend scaling (the STOMP broker is in-memory via `enableSimpleBroker`)

## 🤝 10. Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for Git Flow, branch naming, and Conventional Commits. Code standards live in [.agents/AGENTS.md](.agents/AGENTS.md).
