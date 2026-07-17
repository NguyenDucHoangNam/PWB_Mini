# Refactor Roadmap: `BE` → `Backend` (Modular Monolith)

> File này là **single source of truth** để theo dõi tiến độ refactor từ `BE/` (monolith phẳng) sang `Backend/` (modular monolith chuẩn `api` / `core` / `infrastructure`).
>
> **Quy ước đánh dấu:** `[x]` = xong, `[~]` = đang làm, `[ ]` = chưa làm, `[!]` = bị chặn (xem ghi chú cuối sprint).

---

## 1. Tổng quan 25 micro-sprint

| Sprint | Số micro | Phạm vi |
|--------|----------|---------|
| S0 | 3 | Nền tảng (parent POM, shared-kernel, bootstrap) |
| S1 | 6 | IAM core (Auth, User, JWT, Google, Forgot/Change) |
| S2 | 4 | IAM OTP + i18n + outbox wire |
| S3 | 5 | Voice (VoiceTag, TTS, Quota Redis, Storage) |
| S4 | 4 | Notification (Mail, Outbox dispatcher, Kafka) |
| S5 | 3 | Cleanup, xóa BE, polish docs |
| **Tổng** | **25 micro** | |

---

## 2. Bảng theo dõi nhanh

| ID | Phạm vi | Status | Output kiểm chứng |
|----|---------|:------:|-------------------|
| M0.1 | Parent POM + `<dependencyManagement>` | [x] | `mvn -N validate` PASS, effective-pom chứa 9 explicit + 3 BOM |
| M0.2 | `shared-kernel` + `shared-web`: `ErrorCode` enum (SYS_/ORD_/INV_), cây `BaseBusinessException` abstract + `NotFoundException` + `BadRequestException`, `ErrorResponse` DTO, `GlobalExceptionHandler` (3 handler) | [x] | `mvn -pl shared-kernel,shared-web -am compile` exit 0 |
| M0.3 | `bootstrap` skeleton lean (`Application.java` + `application.yml` + banner + template, **không** kéo `MessageSource`/`Jackson`/`CorrelationId` ở sprint này) | [x] | `mvn -pl bootstrap -am compile` exit 0; `spring-boot:run` start; `/actuator/health` UP |
| M1.1 | `iam/api/`: DTO + `IamFacade` interface (M1.1a: Register/Login/AuthResponse + 9 DTO còn lại + 11 method public) | [x] | compile module `iam` |
| M1.2 | `iam/core/model/`: POJO `User`/`Role`/`EmailAddress`/... | [x] | core không import Spring |
| M1.3 | `iam/core/service/`: `PasswordPolicyService`, `UserRegistrationService` | [x] | core test thuần Java |
| M1.4 | `iam/infrastructure/persistence/`: JPA entity + repo + mapper | [x] | `mvn -pl modules/iam -am clean compile` exit 0; 0 file có `jakarta.persistence` trong `core/`/`api/` |
| M1.5 | `iam/infrastructure/security/`: `JwtTokenProvider`, `GoogleTokenVerifier`, `SecurityConfig` | [ ] | `/auth/login` trả JWT |
| M1.6 | `iam/infrastructure/web/`: `AuthController` + `IamFacadeImpl` | [ ] | register/login E2E |
| M2.1 | `iam/core`: `OtpIssued/VerifiedDomainEvent` + `OtpService` interface | [ ] | compile |
| M2.2 | `iam/infrastructure`: `OtpJpaEntity` + repo + `OtpServiceImpl` | [ ] | DB có bảng `otp_codes` |
| M2.3 | Wire OTP vào flow `register` + `CompleteProfileRequest` | [ ] | register → verify → active |
| M2.4 | i18n resource (3 file properties) + inject `MessageSource` vào controller | [ ] | `Accept-Language` hoạt động |
| M3.1 | `voice/api/`: DTO + `VoiceFacade` interface | [ ] | compile module `voice` |
| M3.2 | `voice/core/model/`: POJO `VoiceTag` + `VoiceQuota` value object | [ ] | core thuần Java |
| M3.3 | `voice/core/service/`: `VoiceTagFactory`, `SsmlSanitizerService`, `VoiceQuotaService` | [ ] | test thuần Java |
| M3.4 | `voice/infrastructure/persistence/`: JPA mapping + repo + mapper | [ ] | DB có bảng `voice_tags` |
| M3.5 | `voice/infrastructure/`: `GcpTtsClient`, `StorageService`, 3 file Lua → config, controller, service impl | [ ] | `/voice-tags/**` + preview chạy |
| M4.1 | `notification/api/`: `EmailRequestedIntegrationEvent` + `NotificationFacade` | [ ] | compile |
| M4.2 | `notification/infrastructure/persistence/`: `OutboxEventJpaEntity` + repo | [ ] | DB ok |
| M4.3 | `notification/infrastructure/mail/`: `MailServiceImpl`, `NoOpMailService`, `ThymeleafConfig`, 6 template HTML | [ ] | gửi mail test qua SMTP |
| M4.4 | `notification/infrastructure/messaging/`: scheduler + Kafka topic + consumer | [ ] | outbox poll → mail gửi |
| M5.1 | Wire cross-module facade trong `bootstrap` | [ ] | app start không lỗi DI |
| M5.2 | Migrate `DataSeeder`, `ElasticsearchConfig`, `RedisConfig`, `OpenApiConfig`, `JpaAuditorAware` → `bootstrap` | [ ] | seed chạy OK |
| M5.3 | Xóa `BE/` + update `docker-compose.yml` + `README.md` | [ ] | `mvn clean install` pass |

---

## 3. Chi tiết từng sprint

### Sprint S0 — Nền tảng chung

**Mục tiêu:** Backend compile được, multi-module wiring đúng.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M0.1** | Parent `pom.xml`: thêm `<properties>` (Java 21, Spring Boot 4.1.x), `<dependencyManagement>` import BOM Spring Boot + Lombok + JJWT + MapStruct + Flyway + Kafka + Thymeleaf + GCP SDK + AWS SDK v2 | `mvn -N validate` PASS (effective-pom xác nhận đủ 9 explicit dep + 3 BOM) |
| **M0.2** | `shared-kernel/pom.xml`: depend `lombok` + `jakarta.validation-api`. Tạo `exception/ErrorCode.java` (enum với 3 trường: `code` prefix module + `message` tiếng Việt + `httpStatus` int), `exception/BaseBusinessException.java` (abstract extends RuntimeException, giữ ErrorCode), `exception/NotFoundException.java`, `exception/BadRequestException.java`, `exception/ErrorResponse.java` (DTO), `exception/ErrorDetail.java`. `shared-web/pom.xml`: depend `shared-kernel` + `spring-boot-starter-web`. Tạo `exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`, 3 handler: `BaseBusinessException`, `MethodArgumentNotValidException`, `Exception.class` chốt chặn) | `mvn -pl shared-kernel,shared-web -am compile` exit 0 |
| **M0.3** | `bootstrap/pom.xml`: depend `shared-kernel` + `shared-web` + `spring-boot-starter-web` + `spring-boot-starter-actuator` + `springboot4-dotenv`. Tạo `BackendApplication.java` (chỉ `@SpringBootApplication(scanBasePackages = "com.pwb")`, **không** `@EnableJpaAuditing`/`@EnableScheduling`/`@ConfigurationPropertiesScan` ở sprint này). Tạo `application.yml` (port, profile, actuator). Copy `banner.txt` + `application-local.yaml.template` từ `BE/`. **LEAVE FOR LATER**: `MessageSourceConfig` (M2.4), `JacksonConfig` (M3.5/M5.2), `CorrelationIdFilter` (M1.6), `SecurityConfig` (M1.5), `application-dev.yaml` (M5.2), `bootstrap/config/` package (lazy — tạo khi có file config đầu tiên) | `mvn -pl bootstrap -am compile` exit 0; `mvn -pl bootstrap spring-boot:run` start banner; `curl /actuator/health` → `UP`; Ctrl+C graceful shutdown exit 0 |

---

### Sprint S1 — IAM core

**Mục tiêu:** Auth + User + Role + JWT + Forgot/Change password + Google OAuth chạy được.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M1.1** | `iam/api/dto/`: copy 8 request (`RegisterRequest`, `LoginRequest`, `GoogleLoginRequest`, `RefreshTokenRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `ChangePasswordRequest`, `CompleteProfileRequest`) + 3 response (`AuthResponse`, `AuthMessageResponse`, `GoogleIdTokenPayload`). Tạo `IamFacade` interface với signature của tất cả method public | compile pass, không thiếu import |
| **M1.2** | `iam/core/model/`: `User.java`, `Role.java`, `RoleName.java` (enum), `EmailAddress.java` (Value Object validate regex), `Password.java` (Value Object áp dụng `PasswordPolicyService`), `PasswordResetToken.java`, `OAuthProvider.java` (enum), `BaseEntity.java` (`createdAt`, `updatedAt`) — tất cả POJO thuần, không `@Entity` | `javac` thuần pass, không có `org.springframework.*` trong classpath |
| **M1.3** | `iam/core/service/`: `PasswordPolicyService` (validate độ mạnh MK — Argon2id + modern-strict: ≥12 chars, upper/lower/digit/special, no whitespace), `UserRegistrationService.register(...)` — chỉ xử lý logic thuần (validate, hash MK bằng Argon2id 3 iter/64MB), KHÔNG gọi DB. `PasswordPolicyServiceImpl`, `UserRegistrationServiceImpl`, `PasswordPolicyResult`, `PasswordPolicyViolation`, `WeakPasswordException` (IAM_002), `RegisterCommand`. Added `de.mkammerer:argon2-jvm` 2.12 + `slf4j-api` vào `iam/pom.xml` | `mvn -pl modules/iam -am clean compile` exit 0; PasswordPolicyService.validate() và UserRegistrationService.register() sẵn sàng cho IamFacadeImpl (M1.6) compose |
| **M1.4** | `iam/infrastructure/persistence/`: `UserJpaEntity`, `RoleJpaEntity`, `PasswordResetTokenJpaEntity` (annotation JPA đầy đủ); `UserRepository`, `RoleRepository`, `PasswordResetTokenRepository` extends `JpaRepository`; mapper JPA↔domain (có thể dùng MapStruct nếu parent POM có) | boot app với profile `dev` không lỗi JPA mapping |
| **M1.5** | `iam/infrastructure/security/`: `JwtTokenProvider` (chuyển từ `BE/security/jwt/`), `CustomUserDetails`, `GoogleTokenVerifier`, `SecurityConfig` (`@EnableWebSecurity`, filter chain), `AuthEntryPoint`, `AccessDeniedHandlerImpl`. Lưu secret qua `${app.jwt.secret}` | POST `/auth/login` → 200 + JWT body |
| **M1.6** | `iam/infrastructure/web/AuthController.java` (chuyển từ `BE/controller/`), `IamFacadeImpl.java` (inject các core service, gọi infrastructure), `IamModuleConfig.java` (`@ComponentScan("com.pwb.iam")`). Xóa code IAM cũ trong `BE/` | full flow register → login → refresh chạy end-to-end qua Postman/curl |

---

### Sprint S2 — IAM OTP + i18n + outbox wire

**Mục tiêu:** Hoàn thiện luồng đăng ký có OTP email + i18n.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M2.1** | `iam/core/events/OtpIssuedDomainEvent.java`, `OtpVerifiedDomainEvent.java`. `iam/api/OtpService` interface (`requestOtp(email)`, `verifyOtp(email, code)`) | compile |
| **M2.2** | `iam/infrastructure/persistence/OtpJpaEntity.java` + repo. `iam/infrastructure/service/impl/OtpServiceImpl.java` — generate code 6 số, TTL 5 phút, rate limit (cấu hình qua `OtpProperties`) | Flyway migration mới tạo `otp_codes` |
| **M2.3** | `IamFacadeImpl.register(...)` gọi `otpService.requestOtp(...)` thay vì active luôn. Endpoint mới `POST /auth/verify-otp`, `POST /auth/resend-otp`, `POST /auth/complete-profile`. Verify success → update user status ACTIVE | flow: register (status=PENDING_OTP) → verify-otp (status=ACTIVE) → login OK |
| **M2.4** | Copy 3 file `messages*.properties` từ `BE/src/main/resources/i18n/` → `bootstrap/src/main/resources/i18n/`. Inject `MessageSource` vào `AuthController` và `IamFacadeImpl`. Validation annotation dùng key `{validation.email.required}` | thay đổi `Accept-Language: vi` → message trả về tiếng Việt |

---

### Sprint S3 — Voice

**Mục tiêu:** VoiceTag CRUD + GCP TTS + Redis quota + S3 Storage + WebSocket.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M3.1** | `voice/api/dto/`: `CreateVoiceTagRequest`, `VoiceTagResponse`, `PreviewResponse`, `VoiceOption`, `VoiceWhitelistResponse`. `voice/api/VoiceFacade` interface | compile |
| **M3.2** | `voice/core/model/VoiceTag.java` (POJO), `VoiceQuota.java` (value object: `dailyLimit`, `used`, `resetAt`) | core thuần Java |
| **M3.3** | `voice/core/service/VoiceTagFactory.java` (tạo tag mới), `SsmlSanitizerService.java` (escape SSML), `VoiceQuotaService.java` (interface — impl Redis sau) | service test thuần Java |
| **M3.4** | `voice/infrastructure/persistence/VoiceTagJpaEntity.java` + `VoiceTagRepository.java` + mapper JPA↔domain | DB OK |
| **M3.5** | `infrastructure/service/impl/VoiceTagServiceImpl`, `GcpTtsClient`, `StorageService` + `StorageServiceImpl` (S3), `VoiceQuotaRedisService` (load 3 file Lua từ `BE/scripts/redis/`). `web/VoiceTagController`. `config/S3Config`, `GcpTtsConfig`, `GcpTtsProperties`, `VoiceTagProperties`, `StorageProperties`. `WebSocketConfig` nếu cần | POST `/voice-tags` → tag tạo; GET `/voice-tags/preview/:id` trả audio base64; quota Redis giảm đúng |

---

### Sprint S4 — Notification

**Mục tiêu:** Mail qua Thymeleaf + Outbox dispatcher + Kafka consumer.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M4.1** | `notification/api/events/EmailRequestedIntegrationEvent.java` (subject, to, templateName, model). `notification/api/NotificationFacade` interface (`sendEmail(...)`) | compile |
| **M4.2** | `notification/infrastructure/persistence/OutboxEventJpaEntity.java` + `OutboxEventRepository.java`. Chuyển từ IAM sang (nếu S2.4 chưa làm) | DB ok |
| **M4.3** | `notification/infrastructure/mail/MailServiceImpl` + `NoOpMailService` + `ThymeleafConfig` + 6 template HTML (`welcome-google`, `register-otp`, `password-reset`, `password-changed`, `account-linked-google`, `_layout`). `MailProperties` | gửi mail test qua SMTP (MailHog local) thành công |
| **M4.4** | `OutboxEventProcessor` (poll bảng outbox, set PROCESSING), `OutboxRelayScheduler` (translate sang `EmailRequestedIntegrationEvent`, publish Kafka, set SENT/FAILED). `KafkaTopicConfig`. `KafkaEmailConsumer` (listen và gọi `MailServiceImpl`) | flow: IAM ghi outbox → scheduler pick → Kafka → consumer → mail gửi → outbox update SENT |

---

### Sprint S5 — Cleanup

**Mục tiêu:** Đưa hệ thống về production-ready, xóa `BE/`.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **M5.1** | `bootstrap/Application.java` config scan tất cả `com.pwb.*`. Inject các facade (`IamFacade`, `VoiceFacade`, `NotificationFacade`) vào `bootstrap` nếu có API top-level (vd: `/health`, swagger gộp). Scheduler config enable. Security config gộp các filter | start app không lỗi DI, không trùng bean |
| **M5.2** | Migrate `DataSeeder`, `ElasticsearchConfig`, `RedisConfig`, `OpenApiConfig`, `JpaAuditorAware`, `SchedulingConfig` → `bootstrap/config/` | `mvn spring-boot:run` seed OK nếu DB trống |
| **M5.3** | Sau khi full E2E pass: xóa folder `BE/`. Update `docker-compose.yml` (service `backend` trỏ vào `Backend/`). Update `README.md`, `STRUCTURE.md` | `mvn clean install` full pass; repo không còn `BE/` |

---

## 4. Quy tắc chung khi thực hiện

### Code conventions
- **Java 21 + Spring Boot 4.1.x**, Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`, `@Builder`)
- **JPA entity**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (KHÔNG `@Data` cho entity)
- **DTO**: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- **Không comment trong source code** (file markdown này là ngoại lệ)
- **Không tự tạo test** — chỉ khi sếp yêu cầu
- **i18n**: mọi message trả cho user qua `MessageSource` với key `UPPER_SNAKE_CASE`
- **Logging**: `log.info("event: x={}, y={}", x, y)` — có context, không log PII

### Dependency rules (theo `Backend/STRUCTURE.md`)
- `shared-kernel` KHÔNG phụ thuộc module nào
- Module nghiệp vụ chỉ depend `shared-kernel` + `api/` module khác (qua facade)
- `bootstrap` depend tất cả module
- `core` KHÔNG import `infrastructure/` của chính nó
- `api` KHÔNG chứa `@Entity`, `@RestController`, `@Repository`

### Naming
- Class: `PascalCase` danh từ
- Method: `camelCase` động từ
- Boolean: prefix `is/has/can/should`
- Constant: `UPPER_SNAKE_CASE`

---

## 5. Câu hỏi đang chờ trả lời

Các câu sau sẽ được hỏi đúng sprint tương ứng, không cần trả lời trước:

| # | Câu hỏi | Sprint áp dụng |
|---|---------|----------------|
| Q1 | MapStruct (auto mapper) hay mapper thủ công? | S1 (M1.4) |
| Q2 | JWT dùng HS256 (secret env) hay RSA (JWKS)? | S1 (M1.5) |
| Q3 | Entity `OutboxEvent` đặt ở module nào? | S2 (M2.4) / S4 (M4.2) |
| Q4 | Khi user xoá account, quota reset qua `VoiceFacade` (sync) hay publish Kafka event (async)? | S3 (M3.5) |
| Q5 | `ssml` Lua script đặt ở `modules/voice/src/main/resources/scripts/` hay `bootstrap`? | S3 (M3.5) |

---

## 6. Definition of Done cho mỗi micro

Một micro-sprint được tính là **xong** khi TẤT CẢ điều kiện sau thoả mãn:

- [ ] Source code đã được move / viết theo đúng cấu trúc `api`/`core`/`infrastructure`
- [ ] `mvn -pl <module> -am compile` exit 0
- [ ] Nếu có endpoint mới: test thủ công qua curl/Postman pass
- [ ] Đã xóa file tương ứng trong `BE/` (nếu là move chứ không phải viết mới)
- [ ] Không có code comment thừa, không có TODO/FIXME
- [ ] Không hardcode message user-facing (i18n qua MessageSource)
- [ ] Linter `ReadLints` không báo lỗi mới

---

## 7. Progress summary

Cập nhật mỗi lần tick xong micro:

| Lần cập nhật | Micro xong | Tổng đã xong |
|--------------|-----------|--------------|
| (khởi tạo) | — | 0 / 25 |
| 2026-07-17 05:22 | M0.1 | 1 / 25 |
| 2026-07-17 05:32 | M0.2 | 2 / 25 |
| 2026-07-17 05:46 | M0.3 | 3 / 25 |
| 2026-07-17 06:20 | M1.1a (Register/Login facade + DTO) | 4 / 25 |
| 2026-07-17 06:45 | M1.2 (iam/core/model: POJO + VO + enum) | 5 / 25 |
| 2026-07-17 07:18 | M1.3 (iam/core/service: PasswordPolicy + UserRegistration, Argon2id) | 6 / 25 |
| 2026-07-17 07:40 | M1.4 (iam/infrastructure/persistence: 3 entity + 4 repo + 3 mapper MapStruct) | 7 / 25 |
