# Câu 9: Kiến Trúc Bảo Mật 7 Lớp Liên Hoàn (Defense-in-Depth) Trong Dự Án PWB_MiNi

### ❓ Câu hỏi:
> *"Dự án PWB_MiNi xử lý bảo mật như thế nào để bảo vệ toàn diện hệ thống? Hãy phân tích mô hình Bảo Mật 7 Lớp Liên Hoàn (Defense-in-Depth) từ mép ngoài Internet cho đến tận lõi dữ liệu Database?"*

---

### 💡 Câu trả lời:

#### 1. Triết Lý Bảo Mật Defense-in-Depth (Phòng Thủ Theo Chiều Sâu)

Trong kiến trúc của `PWB_MiNi`, bảo mật không bao giờ phụ thuộc vào một "bức tường" duy nhất (như chỉ dựa vào tường lửa hay token JWT). Thay vào đó, hệ thống áp dụng triết lý **Defense-in-Depth (Phòng thủ theo chiều sâu)**: giả định rằng bất kỳ một lớp phòng ngự nào cũng có thể bị xuyên thủng, và các lớp tiếp theo phải độc lập ngăn chặn kẻ tấn công tiến sâu vào vùng dữ liệu nhạy cảm.

Mọi yêu cầu (request) từ Internet trước khi chạm tới cơ sở dữ liệu đều phải vượt qua **7 lớp kiểm soát liên hoàn**:

```
Client / Hacker ngoài Internet
  │
  ▼
[Lớp 0: Chu Vi Mạng & Reverse Proxy (Nginx & Docker Network)]
  │
  ▼
[Lớp 1: Bộ Lọc Servlet Mép Ngoài (ClientIpResolver & CorrelationIdFilter)]
  │
  ▼
[Lớp 2: Chuỗi Lọc Spring Security (JWT, Redis Blacklist & HttpRateLimitFilter)]
  │
  ▼
[Lớp 3: Nghiệp Vụ & Phân Quyền Phương Thức (RBAC & Resource Ownership)]
  │
  ▼
[Lớp 4: Mật Mã & Quản Lý Phiên (BCrypt, RTR & Token Theft Detection)]
  │
  ▼
[Lớp 5: Kênh Thời Gian Thực WebSocket STOMP (Chống Nghe Lén & Frame Flood)]
  │
  ▼
[Lớp 6: Lưu Trữ & Dữ Liệu Lõi (S3 Zero-Secret, Parameterized Query & Flyway)]
```

---

#### 2. Phân Tích Chi Tiết 7 Lớp Bảo Mật Trong Mã Nguồn

---

##### 🌐 LỚP 0: Chu Vi Mạng & Reverse Proxy (Network Perimeter)
- **Cô lập mạng nội bộ (Docker Network Isolation)**: 
  Trong `docker-compose.yml`, các hạ tầng chứa dữ liệu lõi bao gồm **PostgreSQL (5432), Redis (6379) và Kafka (9092)** đều được gom vào mạng ảo riêng `pwb-network`. Chúng không bao giờ mở cổng (bind port) trực tiếp ra Internet công cộng mà chỉ chấp nhận kết nối nội bộ từ container Backend Spring Boot.
- **Chứng chỉ SSL/TLS & Ngắt tải sớm tại Nginx**:
  Nginx đóng vai trò Reverse Proxy tiếp nhận lưu lượng HTTPS, giải mã SSL/TLS và lọc bỏ sớm các request có kích thước payload bất thường thông qua `client_max_body_size` trước khi gói tin có thể đi vào chiếm dụng bộ nhớ RAM của tiến trình JVM.

---

##### 🛡️ LỚP 1: Bộ Lọc Servlet Mép Ngoài (Pre-processing Servlet Filters)
Chạy ở tầng servlet mép ngoài cùng của `shared-web` trước khi vào Spring Security:

1. **Chống giả mạo IP (IP Spoofing Protection qua `ClientIpResolver`)**:
   - Kẻ tấn công thường tự tạo header `X-Forwarded-For: 1.2.3.4` giả mạo nhằm vượt qua cơ chế Rate Limit hoặc né danh sách đen (Ban IP).
   - Trong `ClientIpResolver.java`, hệ thống **tuyệt đối không tin tưởng header này một cách vô điều kiện**:
     ```java
     String remoteAddr = request.getRemoteAddr();
     if (isProxyTrusted(remoteAddr, trustedProxies)) {
         // Chỉ đọc X-Forwarded-For khi IP kết nối trực tiếp nằm trong danh sách trustedProxies (Nginx nội bộ)
         String forwarded = request.getHeader("X-Forwarded-For");
         ...
     }
     return remoteAddr; // Mặc định lấy IP mạng thực tế từ TCP Socket
     ```
2. **Truy vết phân tán & Chống ô nhiễm Thread Pool (`CorrelationIdFilter`)**:
   - Gán `X-Correlation-Id` vào `MDC` (Mapped Diagnostic Context) của log.
   - Bắt buộc dùng khối `try { ... } finally { MDC.remove("correlationId"); }` để giải phóng bộ nhớ, tránh tình trạng rò rỉ log chéo giữa các người dùng trên các luồng tái sử dụng của Tomcat Thread Pool.

---

##### 🔒 LỚP 2: Chuỗi Lọc Spring Security (Security Filter Chain)
Cấu hình tập trung tại `SecurityConfig.java` của module `iam`:

1. **Xác thực JWT & Kiểm tra Blacklist tức thì (`JwtAuthenticationFilter`)**:
   - Kiểm tra tính toàn vẹn chữ ký HMAC-SHA256 của Bearer Token.
   - Truy vấn nhanh trên Redis bằng JTI: `blacklistService.isAccessTokenBlacklisted(jti)`. Khi người dùng bấm Đăng xuất, JTI bị đưa ngay vào Redis Blacklist với TTL bằng thời gian sống còn lại của token, vô hiệu hóa token ngay lập tức dù chưa hết hạn JWT.
2. **Chặn DoS & Phân định hạn ngạch (`HttpRateLimitFilter`)**:
   - Đặt ngay sau JWT Filter để lấy được `userId`, đếm theo tài khoản (`u:<userId>`) để người dùng sau mạng NAT văn phòng không bị khóa oan.
3. **Triệt tiêu rò rỉ thông tin nhạy cảm (Zero WhiteLabel Error Leaks)**:
   - Thay thế trang lỗi HTML mặc định của Spring Boot bằng `RestAuthenticationEntryPoint` (401) và `RestAccessDeniedHandler` (403), trả về JSON chuẩn `ApiResponse` để không lộ phiên bản Tomcat, Spring Boot hay Stack Trace ra ngoài.
4. **Vô hiệu hóa CSRF an toàn (`csrf.disable()`)**:
   - Hệ thống là Stateless API sử dụng Bearer Token gửi qua header `Authorization`. Token không tự động đính kèm như Session Cookie truyền thống, do đó CSRF bị vô hiệu hóa hoàn toàn mà không cần CSRF Token rườm rà.
   - Refresh Token lưu trong Cookie được cấu hình an toàn tuyệt đối: `HttpOnly` (JavaScript không thể đọc), `SameSite=Lax` (chống gửi chéo trang) và `Secure` (chỉ truyền qua HTTPS).

---

##### 👮 LỚP 3: Nghiệp Vụ & Phân Quyền Phương Thức (Authorization & RBAC)
Nằm sâu trong tầng `application` use case và domain:

1. **Phân quyền vai trò (Role-Based Access Control - RBAC)**:
   - Bật `@EnableMethodSecurity`.
   - Sử dụng `@PreAuthorize("hasRole('ADMIN')")` bảo vệ các chức năng quản trị hệ thống. Các tính năng tiêu tốn chi phí (như tạo Voice Tag âm thanh) chỉ yêu cầu đã đăng nhập: chúng dựa vào `anyRequest().authenticated()` ở chuỗi filter, giới hạn tần suất theo từng endpoint, và kiểm tra quyền sở hữu trong UseCase.
2. **Kiểm tra quyền sở hữu tài nguyên (Resource Ownership Enforcement)**:
   - Tầng Use Case luôn truy vấn và so khớp quyền sở hữu: User A không thể sửa hay xóa bài hát của User B; chỉ có chủ phòng (Host) trong Live Room mới có quyền kick người khác hoặc chuyển quyền điều khiển bài nhạc.
3. **Chống tấn công Dò quét tài khoản (Anti-Enumeration & Anti-Timing Attack)**:
   - Trong `LoginUseCaseImpl.java`, trạng thái tài khoản (bị khóa, chưa kích hoạt email) **chỉ được kiểm tra sau khi mật khẩu đã được kiểm tra khớp bằng BCrypt**:
     ```java
     // Nếu email không tồn tại HOẶC mật khẩu sai -> Đều ném chung 1 lỗi BAD_CREDENTIALS
     if (user == null || !passwordHasher.matches(command.rawPassword(), user.getPassword().hash())) {
         attemptChecker.recordFailure(email, clientIp);
         throw new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS);
     }
     // Chỉ khi mật khẩu đúng mới kiểm tra tài khoản có bị Ban hay không
     if (user.isBlocked()) throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
     ```
   - Kẻ tấn công không thể dựa vào thông báo lỗi trả về để biết email nào đã tồn tại trong hệ thống.

---

##### 🔑 LỚP 4: Mật Mã & Quản Lý Phiên (Cryptography & Session Management)

1. **Băm mật khẩu 1 chiều chuẩn công nghiệp**:
   - `SpringPasswordHasher.java` sử dụng thuật toán **BCrypt** kèm chuỗi Salt ngẫu nhiên được tự động sinh cho từng người dùng, kháng hoàn toàn tấn công bảng tra cứu sẵn (Rainbow Table Attack).
2. **Khóa tài khoản chống dò mật khẩu (Brute-force Lockout)**:
   - `LoginAttemptChecker.java` theo dõi số lần đăng nhập thất bại trên Redis theo cặp `(email, clientIp)`.
   - Nếu nhập sai quá 5 lần liên tiếp: Khóa đăng nhập ngay lập tức trong **15–30 phút** với mã lỗi `ACCOUNT_LOCKED` kèm thời gian chờ `retryAfterSeconds`.
3. **Xoay Vòng Refresh Token (RTR) & Bẫy Phát Hiện Trộm Token (Token Theft Detection)**:
   - Trong `TokenManagerServiceAdapter.java`, mỗi khi refresh token được dùng để lấy access token mới, token cũ bị thu hồi và thay thế bằng một token mới tinh (Refresh Token Rotation).
   - **Bẫy phát hiện trộm token (Tombstone Key)**: Token cũ vừa bị thu hồi được lưu tạm vào Redis tại `iam:refresh:rotated:<hash>`.
   - Nếu kẻ trộm đánh cắp được token cũ và gửi lên để xin cấp quyền: Hệ thống kiểm tra thấy token nằm trong danh sách đã xoay vòng -> **Nhận diện ngay đây là hành vi Replay Attack do bị lộ token** -> Hệ thống lập tức gọi `revokeAllRefreshTokensForUser`, **đá văng toàn bộ các phiên đăng nhập của tài khoản trên mọi thiết bị** để bảo vệ an toàn cho chủ tài khoản.

---

##### ⚡ LỚP 5: Kênh Thời Gian Thực WebSocket STOMP (Realtime Security)
Các bộ lọc HTTP không thể bảo vệ kết nối TCP Socket kéo dài, hệ thống bổ sung 3 Interceptor chuyên trách tại `liveroom`:

1. **Xác thực kết nối thời gian thực (`StompAuthChannelInterceptor`)**:
   - Bắt buộc frame `CONNECT` của STOMP phải mang theo header `Authorization: Bearer <token>`. Nếu token không hợp lệ hoặc hết hạn, ngắt kết nối socket ngay lập tức với lỗi `WS_UNAUTHENTICATED`.
2. **Chống nghe lén phòng riêng tư (Anti-Eavesdropping qua `StompSubscriptionScopeInterceptor`)**:
   - Khi client gửi lệnh `SUBSCRIBE` vào `/topic/liveroom/{roomId}/**`, interceptor kiểm tra quyền qua `subscriptionPolicy.canSubscribe(userId, roomId)`. Nếu là phòng riêng tư (Private) và user chưa được host duyệt, lệnh subscribe bị hủy bỏ ngay lập tức với mã `WS_UNAUTHORIZED`.
3. **Chống giả mạo máy chủ (Anti-Spoofing & Broker Protection)**:
   - Cấm tuyệt đối client gửi frame `SEND` trực tiếp vào các đích đến của Message Broker (`/topic/**`, `/queue/**`, `/user/**`). Toàn bộ lệnh bắt buộc phải gửi qua tiền tố `/app/**` để tầng ứng dụng kiểm tra tính hợp lệ trước khi broadcast.
4. **Chống tràn bộ đệm socket (`StompRateLimitInterceptor`)**:
   - Phân chia 3 bucket trượt để giới hạn tần suất gửi frame WebRTC Signaling và frame Chat text.

---

##### 🗄️ LỚP 6: Lưu Trữ & Dữ Liệu Lõi (Data & Cloud Storage Layer)

1. **AWS S3 Zero-Secret & Presigned URL**:
   - Bucket S3 đóng kín 100% (`Block All Public Access`). Mã nguồn không lưu cứng Access Key/Secret Key mà dùng IAM Role qua `DefaultCredentialsProvider`.
   - Client chỉ được cấp Presigned URL có thời hạn ngắn (15 phút đến 1 giờ).
2. **Chống Stored XSS khi Download**:
   - Ép buộc header `Content-Disposition: attachment` trên mọi link download GET để trình duyệt buộc phải tải file về máy thay vì thông dịch mã độc HTML/SVG trên domain của S3.
3. **Xác thực định dạng file thật bằng "Ranged GET 16 Bytes & Magic Bytes"**:
   - Backend kéo đúng 16 byte đầu tiên qua HTTP header `Range: bytes=0-15` để kiểm tra chữ ký nhị phân (`ID3`, `RIFF`, `fLaC`, `OggS`), từ chối các file mã độc đổi đuôi giả dạng âm thanh.
4. **Chống SQL Injection tuyệt đối**:
   - 100% câu truy vấn dữ liệu đều sử dụng **Spring Data JPA Parameterized Queries** hoặc Criteria API / Specification. Không bao giờ cộng chuỗi SQL thô (`String concatenation`).
5. **Bảo vệ toàn vẹn Schema bằng Flyway Checksum**:
   - Mọi tệp migration đều được Flyway băm mã SHA-256 Checksum. Nếu ai đó sửa lén cấu trúc database trên production, ứng dụng sẽ từ chối khởi động để ngăn chặn phá hoại dữ liệu.

---

#### 3. Bảng Tổng Hợp 7 Lớp Phòng Thủ (Defense-in-Depth Matrix)

| Lớp | Tên Lớp Bảo Vệ | Nguy Cơ Bị Tấn Công | Giải Pháp Kỹ Thuật Đã Triển Khai Trong Mã Nguồn |
| :---: | :--- | :--- | :--- |
| **0** | **Chu Vi Mạng** | Tấn công hạ tầng DB, DDoS | Docker Network cô lập, Nginx Reverse Proxy, `client_max_body_size`, SSL/TLS |
| **1** | **Servlet Mép Ngoài** | IP Spoofing, rò rỉ log | `ClientIpResolver` (chỉ tin `trustedProxies`), `CorrelationIdFilter` (`finally MDC.remove`) |
| **2** | **Spring Security Chain** | Token rác, WhiteLabel leak, CSRF | `JwtAuthenticationFilter`, Redis Blacklist JTI, `HttpRateLimitFilter`, Cookie `SameSite=Lax` |
| **3** | **Phân Quyền Nghiệp Vụ** | Leo thang đặc quyền, IDOR, User Enumeration | `@PreAuthorize` RBAC, kiểm tra Resource Ownership, Verify mật khẩu trước khi check status |
| **4** | **Mật Mã & Quản Lý Phiên** | Brute-force mật khẩu, Đánh cắp Token | BCrypt Salt ngẫu nhiên, `LoginAttemptChecker` (lock 15-30p), RTR & Bẫy Tombstone Reused |
| **5** | **Realtime WebSockets** | Nghe lén phòng Live, Giả mạo broadcast | `StompAuthChannelInterceptor`, `StompSubscriptionScopeInterceptor`, Cấm send trực tiếp broker |
| **6** | **Lưu Trữ & Dữ Liệu Lõi** | Stored XSS, Fake Audio, SQL Injection | S3 Presigned URL, `Content-Disposition: attachment`, Magic Bytes Ranged GET 16B, JPA Parameterized |
