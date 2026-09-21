# Giai đoạn 1: Nền Tảng Dùng Chung (Shared Foundation)

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [Giai đoạn 2: Module IAM ➡️](02-giai-doan-2-module-iam.md)

---

- **Cấu hình & Khởi động**:
  - `pom.xml` (`Backend` root): Quản lý dependencies đa module.
  - `application.yml` (`Backend/bootstrap`): Cấu hình Flyway `out-of-order: true`, Jackson, CORS.
  - `PwbApplication.java` (`com.pwb.bootstrap`): Điểm khởi chạy của JVM process.
  - *Tại sao dùng Flyway thay vì Hibernate DDL (`ddl-auto: update`)*:
    - **Kiểm soát SQL native**: Tự viết index chuyên biệt (như Partial Index), không để Hibernate sinh DDL ngầm vụng về.
    - **Hỗ trợ Data Migration**: Vừa đổi schema vừa chuyển đổi dữ liệu cũ (`UPDATE`/`INSERT`), Hibernate DDL bất lực ở điểm này.
    - **Chống rác & mất dữ liệu**: Hibernate `update` chỉ thêm chứ không xóa/đổi tên cột cũ (bỏ rơi data); tránh nguy cơ cấu hình nhầm làm mất sạch data Production.
    - **Đồng bộ đa môi trường (Database-as-Code)**: Quản lý version qua Git + bảng `flyway_schema_history`, đảm bảo Local, CI/CD, Staging, Prod khớp 100%. Tự rollback khi script lỗi.
- **Shared Kernel** (Java thuần, không Spring/JPA):
  - `DomainBaseEntity.java` (`com.pwb.shared.domain`): Base entity cho toàn bộ domain model, không dùng `@MappedSuperclass` hay JPA để giữ domain thuần khiết.
  - `ApiResponse.java` & `PageResponse.java` (`com.pwb.shared.dto`): Chuẩn hóa cấu trúc trả về duy nhất cho toàn bộ hệ thống (`success`, `data`, `error`, `traceId`, `timestamp`).
  - `ErrorCode.java`, `ErrorCategory.java` & `BusinessException.java` (`com.pwb.shared.exception`): Hệ thống lỗi độc lập giao thức (Protocol-Agnostic), tuyệt đối không chứa mã HTTP (như 400, 404) để tái sử dụng trên cả REST, Kafka Worker và STOMP WebSocket. Việc quy sang mã HTTP do `WebErrorMapper` ở tầng `shared-web` đảm nhiệm.
  - **Áp dụng Clean Architecture & Domain-Driven Design (DDD)**:

    #### 🏛️ 1. Clean Architecture (Kiến Trúc Sạch)
    Triết lý tối thượng là **Dependency Rule (Quy tắc phụ thuộc một chiều)**: Mã nguồn từ bên ngoài chỉ được phép trỏ vào bên trong, tầng bên trong tuyệt đối không biết gì về tầng bên ngoài.
    ```
    [api] (Web, REST, STOMP)
      │
      ▼
    [application] (Use Cases, Commands)
      │
      ▼
    [domain] (Entities, Value Objects, Port Interfaces)  ◄── Tâm điểm (Pure Java)
      ▲
      │
    [infrastructure] (JPA Entities, Database, Redis, Kafka, S3)
    ```

    - **Mô hình Ports & Adapters (Kiến trúc Lục giác - Hexagonal)**:
      - *Port (Cổng kết nối)*: Interface do tầng `domain` định nghĩa. Tuyên bố yêu cầu nghiệp vụ (ví dụ: cần lưu User, cần upload file) mà không quan tâm ai làm việc đó.
        - Ví dụ: `UserRepository.java` nằm trong `domain/repository/`.
      - *Adapter (Bộ điều hợp)*: Class thực thi (Implementation) nằm ở tầng `infrastructure`.
        - Ví dụ: `UserRepositoryImpl.java` nằm trong `infrastructure/persistence/adapter/`, bên trong dùng Spring Data JPA để nói chuyện với PostgreSQL.
      - *Giá trị thực tế*: Khi công ty muốn đổi PostgreSQL sang MongoDB, hoặc đổi AWS S3 sang Google Cloud Storage, lập trình viên **chỉ cần viết lại Adapter ở Infrastructure**, toàn bộ tầng Domain và Use Case **không phải sửa bất kỳ dòng code nào**.

    - **Tách biệt 4 loại Object cho cùng 1 khái niệm**:
      1. `UserRequest` / `UserResponse` (`api/dto`): Hợp đồng giao tiếp với Client (JSON, có validation `@NotBlank`, `@Email`).
      2. `LoginCommand` / `UserView` (`application`): Dữ liệu thuần để điều phối Use Case.
      3. `User` (`domain/model`): Thực thể nghiệp vụ thuần (không JPA), bảo vệ quy tắc logic bất biến.
      4. `UserJpaEntity` (`infrastructure/entity`): Ánh xạ trực tiếp xuống bảng `iam_users` trong database.
      - *Cái giá phải trả*: Tốn công viết code chuyển đổi qua lại qua mapper.
      - *Cái mua được*: Khi sửa kiểu cột trong Database (sửa `UserJpaEntity`), hợp đồng API của Frontend (`UserResponse`) hoàn toàn không bị vỡ, và ngược lại.

    #### 🧩 2. Domain-Driven Design (DDD - Thiết Kế Hướng Miền)
    Dự án loại bỏ hoàn toàn mô hình "thiếu máu" (Anemic Domain Model - Entity chỉ có getter/setter rồi đẩy hết logic vào Service), thay bằng **Rich Domain Model (Mô hình giàu nghiệp vụ)**:

    - **Rich Domain Model (Thực thể tự bảo vệ mình)**:
      - *Không có Setter bừa bãi*: Constructor là `private`. Muốn tạo mới đối tượng phải qua static factory methods tường minh (`User.createLocal(...)`, `User.createGoogle(...)`).
      - *Tự bảo toàn tính toàn vẹn (Invariants)*: Muốn đổi mật khẩu, User có hàm `changePassword(newPassword)`. Muốn kích hoạt tài khoản có hàm `markEmailVerified()`. Logic nghiệp vụ gắn liền với dữ liệu của Entity.
      - *Khôi phục trạng thái từ DB*: Sử dụng pattern `rehydrate(...)` để khôi phục Entity từ Database mà không kích hoạt lại các validation của luồng tạo mới.
    - **Value Objects (VO - Đối tượng giá trị)**:
      - Thay vì dùng kiểu nguyên thủy lỏng lẻo (`String email`, `String password`), đóng gói thành Value Object:
        - `EmailAddress.java`: Tự trim, tự lowercase, tự kiểm tra regex định dạng email ngay lúc khởi tạo. Không ai có thể tạo ra một `EmailAddress` sai định dạng trong hệ thống.
        - `Password.java`: Đóng gói trạng thái hash, đảm bảo không bao giờ vô tình để lộ mật khẩu thô ra ngoài.
    - **Ubiquitous Language (Ngôn ngữ phổ quát)**:
      - Tên hàm, tên class trong code dùng chính xác thuật ngữ của ngành sản xuất âm nhạc: `SongStatus` (`PENDING_UPLOAD`, `PROCESSING`, `READY`), `VoiceTag` (dấu âm thanh bản quyền), `PlaybackState` & `startedAt` (anchor timestamp đồng bộ thời gian nghe), `SessionCycle` (chu kỳ phòng nghe).

    #### 🔥 3. Góc Phỏng Vấn Senior / Architect
    - **Câu hỏi**: *"Clean Architecture tách nhiều tầng và map qua lại 4 loại Object như vậy có bị quá tải (Overhead/Boilerplate) không? Khi nào bạn nên áp dụng và khi nào KHÔNG nên?"*
    - **Câu trả lời ghi điểm**:
      - Clean Architecture đòi hỏi chi phí viết code ban đầu cao hơn do phải chuyển đổi Object qua Mapper.
      - Tuy nhiên, với hệ thống có **nghiệp vụ phức tạp, nhiều luồng tương tác** (vừa có HTTP REST, vừa có Kafka Worker xử lý ngầm, vừa có STOMP WebSocket realtime), việc cô lập Domain thành Java thuần là chìa khóa sống còn để:
        1. **Kiểm thử cực nhanh và tin cậy**: Viết Unit Test cho Domain logic mà không cần bật Spring Context hay Mock Database.
        2. **Bảo vệ hệ thống khỏi biến động công nghệ**: Thay đổi database, framework web hay thư viện bảo mật bên ngoài không làm vỡ các rule nghiệp vụ lõi bên trong.
      - Với các dự án CRUD đơn giản, ít logic thì **không nên** áp dụng vì sẽ rơi vào bẫy Over-engineering.
- **Shared Web**:
  - `GlobalExceptionHandler.java` & `WebErrorMapper.java` (`com.pwb.web.exception`): Dịch `ErrorCategory` thuần sang `HttpStatus` tương ứng (400, 401, 403, 404, 409, 422, 429, 500) và dịch thông báo lỗi i18n qua `MessageSource`.
  - `SecurityConfig.java` & `JwtAuthenticationFilter.java` (`com.pwb.web.security`): Cấu hình Spring Security stateless, tắt CSRF (vì dùng Bearer token), whitelist CORS cho Next.js và xác thực token JWT.
  - `ClientIpResolver.java` (`com.pwb.web.security`): **Chống giả mạo IP (IP Spoofing)**. Chỉ tin tưởng header `X-Forwarded-For` khi IP kết nối trực tiếp (`getRemoteAddr()`) nằm trong danh sách `trustedProxies` (như Nginx nội bộ). Tránh việc hacker tự gửi header giả để bypass rate limit hay né IP ban.
  - `ErrorResponseWriter.java` (`com.pwb.web.security`): Ghi thẳng JSON chuẩn `ApiResponse` khi dính lỗi 401/403 tại Spring Security Filter Chain, loại bỏ hoàn toàn trang lỗi HTML WhiteLabel mặc định.
  - `CorrelationIdFilter.java` (`com.pwb.web.filter`): Đọc hoặc sinh `X-Correlation-Id`, đưa vào SLF4J MDC phục vụ truy vết phân tán (Distributed Tracing). Luôn xóa MDC trong khối `finally` để tránh ô nhiễm Thread Pool của Tomcat.
  - Custom Argument Resolvers (`@CurrentUser`, `@CurrentClientIp`, `@CurrentUserAgent`): Tự động tiêm thông tin user, IP tin cậy và User-Agent vào Controller method, giữ Controller sạch sẽ không dính `HttpServletRequest`.

  - **Kiến Trúc Rate Limiting "Phòng Thủ Chiều Sâu" (6 Tầng Độc Lập)**:

    #### 🛡️ Tầng 1: Global HTTP Rate Limiter (`HttpRateLimitFilter` & `HttpRateLimitService`)
    - *Vị trí đắt giá trong chuỗi filter*: Nằm **sau** `JwtAuthenticationFilter` (để có thông tin định danh user) và **trước** `AuthorizationFilter` (để request ẩn danh vẫn bị tính hạn mức).
    - *Cơ chế đếm "Ưu tiên tài khoản trước, IP sau"*:
      - Đã đăng nhập $\rightarrow$ Đếm theo `u:<userId>`.
      - Khách ẩn danh $\rightarrow$ Đếm theo `ip:<clientIp>`.
      - *Ý nghĩa*: Tránh "phạt oan" cả một văn phòng hay trường học dùng chung mạng NAT công cộng khi có một người spam.
    - *Kỹ thuật Redis Lua Script nguyên tử (Atomic)*: Chạy script trực tiếp trong RAM của Redis (`INCR` + `EXPIRE`), loại bỏ triệt để nguy cơ race condition hoặc rò rỉ key vĩnh viễn khi tiến trình gặp sự cố.
    - *Hạn mức cấu hình*:
      - Trần toàn cục: 500 request/phút.
      - Luật riêng cho `POST /api/v1/voice-tags/tts/preview`: 20 request/phút. Đây là luật rate limit duy nhất sinh ra vì **TIỀN**, do mỗi lần nghe thử là Google Cloud tính phí dịch vụ TTS.
    - *Headers trả về*: Luôn trả kèm `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` và `Retry-After` (khi bị 429).

    #### 🔐 Tầng 2: Thao Tác Nhạy Cảm IAM (`RateLimitGuard` & `ThrottlingServiceAdapter`)
    - Nằm ở tầng Application của IAM, kiểm tra song song cả 2 chiều:
      - `scope:ip:<clientIp>`: Chặn 1 IP dò quét hàng loạt email khác nhau.
      - `scope:subject:<email>`: Chặn mạng botnet phân tán (nhiều IP khác nhau) cùng dồn vào dò mật khẩu của 1 tài khoản `admin`.
    - Áp dụng cho: `login`, `register`, `verify-otp`, `resend-otp`, `forgot-password`, `reset-password`, `change-password`.

    #### ⏳ Tầng 3: Cooldown Giãn Cách (IAM Cooldown)
    - Trả lời câu hỏi: *"Đã đủ thời gian kể từ lần thao tác trước chưa?"*
    - Sử dụng khóa Redis `iam:cooldown:<purpose>:<email>` (TTL 60s) để ngăn chặn việc bấm liên tục nút gửi lại OTP (`resend-otp`) hoặc quên mật khẩu.

    #### 🚫 Tầng 4: Chống Dò Mật Khẩu & Khóa Tài Khoản (`LoginAttemptChecker`)
    - Đếm số lần nhập sai mật khẩu: `iam:login:fail:<email>:<clientIp>` (TTL 15 phút).
    - Nếu gõ sai quá **5 lần** $\rightarrow$ Kích hoạt cờ khóa tài khoản: `iam:login:lock:<email>:<clientIp>` (TTL 30 phút).
    - Khi tài khoản bị khóa, dù nhập đúng mật khẩu hệ thống vẫn ném lỗi `ACCOUNT_LOCKED` kèm số giây `retryAfterSeconds` phải chờ.

    #### 🔍 Tầng 5: Throttle Tra Cứu Mã Phòng (`LiveRoom`)
    - Khi người dùng gọi API tìm phòng bằng mã 6 ký tự (`GET /rooms/by-code/{code}`), hệ thống throttle theo IP trên Redis.
    - Ngăn chặn kẻ xấu viết script tự động brute-force quét từ `000000` đến `999999` để tìm ra các phòng riêng tư.

    #### ⚡ Tầng 6: Realtime WebSocket Rate Limiter (`StompRateLimitInterceptor`)
    - Chặn đứng spam ngay trên Inbound Channel của STOMP WebSocket bằng 3 thùng chứa (Buckets):
      1. `Bucket.RTC`: Tín hiệu WebRTC signaling (cho phép tần suất cao hơn để bắt tay P2P).
      2. `Bucket.CHAT`: Tin nhắn chat và bình luận trên timeline nhạc.
      3. `Bucket.OTHER`: Các frame điều khiển nhạc (play/pause/seek/volume).
    - Khi client spam frame vượt ngưỡng trong 1 giây, interceptor sẽ **âm thầm DROP frame đó** và gửi thông báo cảnh báo qua user queue riêng, ngăn chặn sập WebSocket Broker.

    #### 💻 Trải Nghiệm Đồng Bộ Phía Frontend (Next.js)
    - `api-client.ts`: Đọc số giây chờ từ header `Retry-After` hoặc từ body payload `error.retryAfterSeconds` (phòng trường hợp proxy trung gian nuốt mất header).
    - Hook `use-retry-countdown.ts`: Tự động khóa nút bấm (`disabled={true}`) và hiển thị đồng hồ đếm ngược thời gian thực trên các form đăng nhập, đăng ký, đổi mật khẩu: *"Bạn đã thử quá nhiều lần, vui lòng thử lại sau X giây..."*.

  - **Kiến Trúc Bảo Mật 7 Lớp Liên Hoàn (Defense-in-Depth)**:
    Mô hình bảo vệ từ ngoài vào trong theo đúng hành trình của một request từ Internet vào đến Database:

    ```
    Client / Hacker ngoài Internet
      │
      ▼
    [Lớp 0: Chu vi Mạng & Proxy (Nginx & Docker Network)]
      │
      ▼
    [Lớp 1: Bộ Lọc Servlet Mép Ngoài (ClientIpResolver & CorrelationId)]
      │
      ▼
    [Lớp 2: Chuỗi Lọc Spring Security (JWT, Blacklist & HttpRateLimit)]
      │
      ▼
    [Lớp 3: Nghiệp Vụ & Phân Quyền Phương Thức (RBAC & Resource Ownership)]
      │
      ▼
    [Lớp 4: Mật Mã & Phiên Đăng Nhập (BCrypt, RTR & Theft Detection)]
      │
      ▼
    [Lớp 5: Kênh Thời Gian Thực (STOMP WebSocket & Chống Nghe Lén)]
      │
      ▼
    [Lớp 6: Lưu Trữ & Dữ Liệu Lõi (S3 Presigned URL & PostgreSQL)]
    ```

    #### 🌐 Lớp 0: Chu Vi Mạng & Reverse Proxy (Network Perimeter)
    - *Nginx & Docker Network*: Database PostgreSQL (5433), Redis (6379), Kafka (9092) và Backend (8080) đều cô lập trong mạng nội bộ Docker (`pwb-network`), không mở cổng trực tiếp ra Internet.
    - *SSL/TLS & Giới hạn tải*: Nginx xử lý HTTPS, giải mã SSL, chặn sớm các payload vượt quá kích thước cho phép (`client_max_body_size`) trước khi chạm vào tiến trình JVM.

    #### 🛡️ Lớp 1: Bộ Lọc Servlet Mép Ngoài (Pre-processing Filters)
    - *Chống giả mạo IP (IP Spoofing Protection)*: `ClientIpResolver.java` chỉ tin tưởng header `X-Forwarded-For` khi gói tin đến từ proxy tin cậy (`trustedProxies` như Nginx nội bộ). Hacker tự chế header IP để né rate limit hoặc né ban sẽ bị loại bỏ ngay và lấy IP kết nối thực tế.
    - *Truy vết phân tán & Chống rò rỉ log*: `CorrelationIdFilter.java` gán `X-Correlation-Id` vào SLF4J MDC, luôn dọn sạch trong khối `finally` để tránh ô nhiễm Thread Pool của Tomcat.

    #### 🔒 Lớp 2: Chuỗi Lọc Spring Security (Security Filter Chain)
    - *Xác thực JWT & Blacklist*: `JwtAuthenticationFilter.java` kiểm tra chữ ký HMAC-SHA256 và check ngay trên Redis xem token có nằm trong danh sách đen (`iam:jwt:blacklist:<jti>`) do vừa logout hay không.
    - *Chặn DoS sớm (Early Drop)*: `HttpRateLimitFilter.java` dùng Redis Lua Script chặn đứng request spam trước khi chạm vào Controller.
    - *Chống rò rỉ thông tin (No WhiteLabel Leaks)*: `ErrorResponseWriter.java` bắt lỗi 401/403 tại filter và trả về JSON chuẩn `ApiResponse`, loại bỏ hoàn toàn trang lỗi HTML mặc định của Spring.
    - *Stateless & CSRF*: Tắt CSRF an toàn vì dùng Bearer token trong header; cookie refresh token có gắn `HttpOnly`, `SameSite=Lax`, `Secure`.

    #### 👮 Lớp 3: Nghiệp Vụ & Phân Quyền Phương Thức (Authorization & RBAC)
    - *Phân quyền vai trò (RBAC)*: Bật `@EnableMethodSecurity`, dùng `@PreAuthorize("hasRole('ADMIN')")` bảo vệ các API quản trị (đặt ở cấp class trên `AdminUserController`). Hệ thống chỉ còn hai vai trò `USER` và `ADMIN`; các tính năng tốn tài nguyên bên Audio chỉ yêu cầu đã đăng nhập, dựa vào `anyRequest().authenticated()` của chuỗi filter cộng với kiểm tra quyền sở hữu trong UseCase.
    - *Kiểm tra quyền sở hữu tài nguyên (Resource Ownership)*: Tầng Use Case luôn kiểm tra: User A không thể sửa bài hát của User B; thành viên thường không thể kick người khác hay tắt mic của phòng.
    - *Chống tấn công dò quét tài khoản (Anti-Enumeration & Anti-Timing Attack)*: Trong `LoginUseCaseImpl.java`, trạng thái tài khoản (Active, Ban, Pending) chỉ được kiểm tra **sau khi mật khẩu đã khớp**. Kẻ tấn công không thể dựa vào lỗi trả về để biết email nào đã tồn tại trong hệ thống.

    #### 🔑 Lớp 4: Mật Mã & Quản Lý Phiên (Cryptography & Token Defense)
    - *Băm mật khẩu 1 chiều*: `SpringPasswordHasher.java` dùng BCrypt kèm salt ngẫu nhiên, kháng hoàn toàn tấn công Rainbow Table.
    - *Khóa chống Brute-force*: `LoginAttemptChecker.java` tự động khóa IP + Email trong 30 phút nếu nhập sai mật khẩu quá 5 lần.
    - *Xoay vòng Refresh Token (RTR) & Bẫy phát hiện trộm token*:
      - Refresh token lưu trong Redis dưới dạng mã băm SHA-256 (chống lộ khi dump RAM Redis).
      - Token cũ khi xoay vòng được đưa vào "bia mộ" (`iam:refresh:rotated:<hash>`). Nếu kẻ trộm dùng lại token cũ này $\rightarrow$ Hệ thống lập tức phát hiện và **hủy toàn bộ phiên đăng nhập của tài khoản trên mọi thiết bị** (`revokeAllRefreshTokensForUser`).

    #### ⚡ Lớp 5: Kênh Thời Gian Thực (Realtime WebSocket / STOMP Security)
    - *Bắt buộc xác thực khi kết nối*: `StompAuthChannelInterceptor.java` chặn đứng các kết nối WebSocket không mang JWT hợp lệ ngay tại frame `CONNECT`.
    - *Chống nghe lén phòng riêng tư (Anti-Eavesdropping)*: `StompSubscriptionScopeInterceptor.java` chặn client subscribe vào topic `/topic/liveroom/{roomId}/**` nếu chưa được chủ phòng phê duyệt tham gia.
    - *Chống giả mạo máy chủ*: Cấm client gửi frame trực tiếp vào broker topic (`/topic/**`, `/queue/**`), bắt buộc phải đi qua `/app/**`.
    - *Rate Limit Frame*: `StompRateLimitInterceptor.java` chặn flood frame chat và WebRTC làm treo broker.

    #### 🗄️ Lớp 6: Lưu Trữ & Dữ Liệu Lõi (Data & Cloud Storage Layer)
    - *Kiến trúc S3 Zero-Secret*: Bucket S3 chặn 100% truy cập công khai. Client không bao giờ có AWS Access Key, chỉ được cấp Pre-signed URL có thời hạn vài phút để tải/nghe file.
    - *Chống SQL Injection*: 100% câu truy vấn dùng JPA Parameterized Queries hoặc JPA Specification.
    - *Bảo vệ toàn vẹn Schema*: Flyway kiểm tra Checksum SHA-256 của từng file migration, ngăn chặn việc sửa lén cấu trúc database.
- **Shared Infrastructure**:
  - `OutboxRelayScheduler.java` (`com.pwb.infra.outbox.scheduler`): Quét bảng `outbox_events` mỗi 5s, khóa lease lock phân tán và đẩy event sang Kafka.
  - `S3StorageService.java` (`com.pwb.infra.storage.s3`): Cấp Pre-signed PUT/GET URL truy cập AWS S3.

  - **Tóm Tắt Cốt Lõi Về Object Storage AWS S3**:

    #### 1. Triết Lý Bypass Backend (Zero-Payload-Through-Backend)
    - Trình duyệt upload và phát nhạc **trực tiếp với AWS S3** thông qua Pre-signed URL.
    - **Mục đích**: Giải phóng 100% RAM Heap, CPU, băng thông mạng và tránh giam lỏng Worker Thread của Tomcat khi xử lý các file âm thanh nặng từ 50MB – 200MB.

    #### 2. Bảo Mật Chữ Ký Pre-signed 2 Chiều
    - **Presigned Upload (PUT)**: Khóa cứng `contentLength` và `contentType` vào chuỗi băm chữ ký SigV4 (`X-Amz-SignedHeaders`). Nếu client upload vượt quá 1 byte dung lượng đã xin phép hoặc sai loại MIME, AWS S3 sẽ **tự động ngắt kết nối và trả về 403 Forbidden**.
    - **Presigned Download (GET)**: Luôn ép header `responseContentDisposition("attachment")` để **chống tấn công Stored XSS** (ngăn trình duyệt render file HTML độc hại trên origin của S3), trong khi thẻ HTML5 `<audio>` của web vẫn phát nhạc bình thường.

    #### 3. Kỹ Thuật Ranged GET 16 Bytes & Soi Magic Bytes (`MediaTypeUtils`)
    - Hàm `readHead(key, 16)` gửi HTTP header `Range: bytes=0-15` để **chỉ tải đúng 16 byte đầu tiên** của file thay vì tải cả bài hát 200MB về máy chủ.
    - Kiểm tra chữ ký nhị phân thực tế để xác thực định dạng file, tuyệt đối không tin đuôi mở rộng (`.mp3`) từ client:
      - **MP3**: Header `ID3` (`0x49 0x44 0x33`) hoặc Sync Frame `0xFF 0xE0`.
      - **WAV**: Chuỗi `RIFF` (`0x52 0x49 0x46 0x46`).
      - **FLAC**: Chuỗi `fLaC` (`0x66 0x4C 0x61 0x43`).
      - **OGG**: Chuỗi `OggS` (`0x4F 0x67 0x67 0x53`).

    #### 4. Tăng Tốc Song Song & Server-Side Copy
    - **S3 Transfer Manager**: Hàm `downloadToFile` và `uploadFile` tự động bẻ nhỏ file thành các khối 5MB để truyền song song đa luồng qua nhiều kết nối TCP, phá vỡ giới hạn Bandwidth-Delay Product (BDP) khi truyền file lớn trên mạng WAN.
    - **Server-Side Copy**: Phương thức `copy(...)` yêu cầu các máy chủ AWS tự nhân bản file nội bộ trong data center, không tốn 1 byte băng thông nào kéo về JVM rồi đẩy lại.

    #### 5. Chiến Lược Retry Thông Minh Với Stream
    - Gắn `@Retryable` cho `byte[]` và `Path` (vì có thể đọc lại nhiều lần khi đứt mạng).
    - **Cố tình loại trừ `@Retryable` khỏi `upload(InputStream)`**: Do stream không thể tua lại (cannot rewind). Nếu retry khi stream đã đọc dở, lần thử thứ hai sẽ đọc từ giữa/cuối luồng, gây lỗi sai lệch Content-Length và che lấp lỗi thật của S3.

    #### 6. Phòng Thủ Path Traversal & Zero-Secret
    - Hàm `validateKey` chặn đứng ký tự `..` và chỉ cho phép regex `^[a-zA-Z0-9/_\\-\\.]+$` với độ dài $\le 1024$.
    - `credentialsProvider` ưu tiên `DefaultCredentialsProvider` trên môi trường Production để tự nhận diện **IAM Role từ AWS EC2/ECS/EKS**, không bao giờ lưu cứng Access Key/Secret Key trong mã nguồn.
- **Frontend Lib Dùng Chung**:
  - `api-client.ts` (`Frontend/src/lib`): Cấu hình Axios interceptor tự gắn JWT, đọc header/body rate-limit.
  - `auth-refresh.ts` (`Frontend/src/lib`): Quản lý hàng đợi tự động refresh token khi gặp 401.

---

---

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [Giai đoạn 2: Module IAM ➡️](02-giai-doan-2-module-iam.md)
