# Refactor Roadmap: `BE` → `Backend` (Modular Monolith)

> File này là **single source of truth** để theo dõi tiến độ refactor từ `BE/` (monolith phẳng) sang `Backend/` (modular monolith chuẩn `api` / `core` / `infrastructure`).
>
> **Quy ước đánh dấu:** `[x]` = xong, `[~]` = đang làm, `[ ]` = chưa làm, `[!]` = bị chặn (xem ghi chú cuối sprint), `[-]` = đã hủy/revoke.
>
> **Cập nhật plan ngày 2026-07-18:** Sếp chốt chiến lược — hoàn thiện **IAM foundation + Notification (Outbox + Kafka)** trước khi đụng các module nghiệp vụ khác. Sprint S3 (Voice) bị **revoke** vì sẽ thiết kế lại từ đầu. Sprint S4–S5 được thay bằng **I0 → I3** với module `outbox` tách riêng (phương án D — kiến trúc scale lâu dài).

---

## 1. Tổng quan sprint (đã điều chỉnh)

| Sprint | Số micro | Phạm vi | Status |
|--------|----------|---------|:------:|
| S0 | 3 | Nền tảng (parent POM, shared-kernel, bootstrap) | ✅ xong |
| S1 | 6 | IAM core (Auth, User, Role, JWT, Google, Forgot/Change) | ✅ xong |
| S2 | 4 | IAM OTP + i18n | ✅ xong |
| S3 | 5 | ~~Voice (VoiceTag, TTS, Quota Redis, Storage)~~ | [-] **REVOKED** — redesign |
| S4 | 4 | ~~Notification (Mail, Outbox dispatcher, Kafka)~~ | 🗑️ thay bằng **I1** |
| **I-1** | **3** | **DB migration backfill (IAM roles/users/password_reset) + switch `ddl-auto` sang `validate`** | 🆕 |
| **I0** | **4** | **IAM harden (refresh rotation, brute-force lock, rate-limit, audit public paths)** | 🆕 |
| **I1** | **9** | **Notification + Outbox + Kafka (module `outbox` riêng, end-to-end mail thật)** | 🆕 |
| **I3** | **3** | **Cleanup (OpenAPI, doc, xóa `BE/`)** | 🆕 |
| **Tổng còn lại** | **19** | | |

> 💡 **Tại sao tách module `outbox` riêng (phương án D)?** Vì sếp muốn dùng outbox cho nhiều chức năng sau này (IAM, Voice, Billing tương lai). Tách riêng đảm bảo **1 schema DB duy nhất + 1 dispatcher duy nhất + 1 transaction guarantee duy nhất**. Notification chỉ là **consumer** của Kafka topic — single-responsibility.
>
> 💡 **Tại sao Sprint I-1 đứng trước I0?** Vì V1 migration mới chỉ có `otp_codes`. Còn thiếu migration cho `iam_users`, `iam_roles`, `password_reset_tokens` (đang để Hibernate `ddl-auto=update` tự tạo — không track, không audit, không rollback). Sprint I-1 chèn migration V2/V3/V4 + switch sang `ddl-auto=validate` để đảm bảo các sprint sau (I0.1 cần V5 cho refresh-token; I1.2 cần V6 cho outbox) có schema nền tảng sạch.

---

## 2. Bảng theo dõi nhanh

### 2.1 Sprint cũ — đã hoàn thành

| ID | Phạm vi | Status | Output kiểm chứng |
|----|---------|:------:|-------------------|
| M0.1 | Parent POM + `<dependencyManagement>` | [x] | `mvn -N validate` PASS, effective-pom chứa 9 explicit + 3 BOM |
| M0.2 | `shared-kernel` + `shared-web`: `ErrorCode` enum + `BaseBusinessException` + `GlobalExceptionHandler` | [x] | `mvn -pl shared-kernel,shared-web -am compile` exit 0 |
| M0.3 | `bootstrap` skeleton lean (`Application.java` + `application.yml`) | [x] | `mvn -pl bootstrap -am compile` exit 0; `spring-boot:run` start; `/actuator/health` UP |
| M1.1 | `iam/api/`: DTO + `IamFacade` interface | [x] | compile module `iam` |
| M1.2 | `iam/core/model/`: POJO `User`/`Role`/`EmailAddress`/`Password` | [x] | core không import Spring |
| M1.3 | `iam/core/service/`: `PasswordPolicyService`, `UserRegistrationService` (Argon2id) | [x] | core test thuần Java |
| M1.4 | `iam/infrastructure/persistence/`: JPA entity + repo + mapper (MapStruct) | [x] | `mvn -pl modules/iam -am clean compile` exit 0 |
| M1.5 | `iam/infrastructure/security/`: `JwtTokenProvider`, `GoogleTokenVerifier`, `SecurityConfig` | [x] | compile + runtime OK với JWT |
| M1.6 | `iam/infrastructure/web/`: `AuthController` + `IamFacadeImpl` + filter chain + 11 endpoint E2E | [x] | register/login/refresh/logout/forgot/reset/change E2E pass |
| M2.1 | `iam/core`: `OtpIssued/VerifiedDomainEvent` + `OtpService` interface | [x] | compile |
| M2.2 | `iam/infrastructure`: `OtpCodeJpaEntity` + repo + mapper + Redis Lua + Flyway V1 | [x] | `mvn` exit 0, Flyway tree chứa V1 |
| M2.3 | Wire OTP vào `register` + `verifyOtp` + `resendOtp` + `completeProfile` | [x] | register → verify → active OK |
| M2.4 | i18n resource (3 file properties) + `MessageResolver` + inject vào controller | [x] | `Accept-Language` hoạt động |

### 2.2 Sprint cũ — bị hủy/revoke

| ID | Phạm vi | Status | Lý do |
|----|---------|:------:|-------|
| M3.1 | `voice/api/`: DTO + `VoiceFacade` interface | [-] | Voice redesign từ đầu |
| M3.2 | `voice/core/model/`: `VoiceTag` + `VoiceQuota` VO | [-] | Voice redesign từ đầu |
| M3.3 | `voice/core/service/`: `VoiceTagFactory`, `SsmlSanitizerService`, `VoiceQuotaService` | [-] | Voice redesign từ đầu |
| M3.4 | `voice/infrastructure/persistence/`: JPA + repo + mapper | [-] | Voice redesign từ đầu |
| M3.5 | `voice/infrastructure/service` + `GcpTtsClient` + `StorageService` + Redis Lua + Controller | [-] | Voice redesign từ đầu |
| M4.1 | ~~`notification/api/`: `EmailRequestedIntegrationEvent` + `NotificationFacade`~~ | [-] | Thay bằng **I1.4** (notification là consumer only) |
| M4.2 | ~~`notification/infrastructure/persistence/`: `OutboxEventJpaEntity`~~ | [-] | Thay bằng **I1.2** (outbox ở module `outbox` riêng) |
| M4.3 | ~~`notification/infrastructure/mail/`: Mail + Thymeleaf~~ | [-] | Thay bằng **I1.5** |
| M4.4 | ~~Scheduler + Kafka consumer~~ | [-] | Thay bằng **I1.3 + I1.6** (outbox scheduler ở module `outbox`) |

### 2.3 Sprint mới — sẽ làm tiếp

| ID | Phạm vi | Status | Output kiểm chứng |
|----|---------|:------:|-------------------|
| **I-1.1** | Flyway migration `V2__create_iam_roles.sql` (table `iam_roles` + `iam_user_roles` many-to-many) + `V3__create_iam_users.sql` (table `iam_users` với UNIQUE `email`, UNIQUE `username`, FK `role_id`, collation `utf8mb4_unicode_ci`) + `V4__seed_iam_roles.sql` (seed 3 roles USER/PRO/ADMIN) | [x] | `mvn -pl bootstrap -am clean install` exit 0; local DB start từ zero khởi tạo sạch; role `USER` được seed bằng `data.sql` |
| **I-1.2** | Flyway migration `V5__create_password_reset_tokens.sql` (table `iam_password_reset_tokens` với FK `user_id`, UNIQUE `token_hash`, `expires_at`, `used`, `used_at`) | [x] | E2E gọi `/auth/forgot-password` → token lưu DB với hash SHA-256; `/auth/reset-password` → token mark `used=true` *(E2E pending — sếp chốt để test sau)* |
| **I-1.3** | `application.yml` (cả dev/prod profile): đổi `spring.jpa.hibernate.ddl-auto=update` → `validate`. Hibernate sẽ KHÔNG tự ý ALTER schema; mismatch schema ↔ entity → app fail ngay ở startup với lỗi rõ ràng | [x] | App vẫn start khi schema khớp entity; thiếu cột/thừa cột/sai kiểu → fail rõ "Schema-validation: missing column X" *(E2E startup verification pending — sếp chốt để test sau)* |
| **I0.1** | Refresh token rotation: mỗi refresh sinh token mới, invalidate token cũ (lưu `revoked_at`). DB migration `V5__add_refresh_token_revocation.sql` (thêm cột `revoked_at DATETIME(6) NULL`, `revoked_reason VARCHAR(64) NULL`, index `(token_hash, revoked_at)`) | [ ] | Refresh x2 lần liên tiếp → token cũ trả `AUTH_TOKEN_INVALID` |
| **I0.2** | Brute-force lockout: 5 lần login sai → khóa 15 phút (Redis `lock:user:{email}`) | [ ] | Login 6 lần liên tiếp → trả `AUTH_ACCOUNT_LOCKED` |
| **I0.3** | Rate limit `/auth/register`, `/auth/forgot-password`, `/auth/resend-otp`: 3 req/IP/giờ | [ ] | Spam 4 lần trong 1h từ 1 IP → trả `429 TOO_MANY_REQUESTS` |
| **I0.4** | Audit `SecurityProperties.publicPaths` + verify từng endpoint có auth đúng (đặc biệt `verify-otp`, `resend-otp`, `forgot-password`, `reset-password`) | [ ] | Doc checklist + fix nếu thiếu |
| **I1.1** | `outbox/api/`: `OutboxWriter` interface (`enqueue(topic, key, payload)`) + `OutboxEventPayload` record + `@TransactionalEventListener` config | [ ] | compile |
| **I1.2** | `outbox/infrastructure/persistence/`: `OutboxEventJpaEntity` + repo + Flyway migration `V6__create_outbox_events.sql` (id, aggregate_type, aggregate_id, topic, payload JSONB, status, retry_count, created_at, sent_at, last_error) | [ ] | DB ok, Flyway tree chứa V2 → V6 |
| **I1.3** | `outbox/infrastructure/scheduler/`: `OutboxRelayScheduler` (poll 5s, claim batch `SELECT FOR UPDATE SKIP LOCKED`, publish Kafka, mark SENT) + `OutboxKafkaConfig` (topic `notification.email.v1`) | [ ] | E2E poll → Kafka publish OK |
| **I1.4** | `notification/api/`: `EmailRequestedIntegrationEvent` (record với `to`, `subjectKey`, `templateName`, `model`, `locale`) + `NotificationFacade` interface (`consumeEmailRequest(event)`) — notification là **consumer only** | [ ] | compile |
| **I1.5** | `notification/infrastructure/mail/`: `MailServiceImpl` (Spring `JavaMailSender`) + `NoOpMailService` (profile dev/test) + `MailProperties` + `ThymeleafConfig` + 6 template HTML i18n vi/en (`register-otp`, `welcome-google`, `password-reset`, `password-changed`, `linked-google`, `_layout`) | [ ] | Gửi qua MailHog thành công, tiếng Việt + tiếng Anh |
| **I1.6** | `notification/infrastructure/messaging/`: `KafkaEmailConsumer` listen `notification.email.v1` → resolve template theo `payload.templateName` + `payload.locale` → render Thymeleaf → gọi `MailService` | [ ] | Outbox → Kafka → mail đến inbox MailHog |
| **I1.7** | Wire IAM: thay `LoggingAuthEventPublisher` → `OutboxAuthEventPublisher` (gọi `outboxWriter.enqueue("notification.email.v1", userId, payloadMap)` trong `@Transactional` callback). 9 event IAM giờ gửi qua outbox | [ ] | E2E: register → OTP mail đến, forgot-password → reset link mail đến, Google login → welcome mail đến |
| **I1.8** | docker-compose.yml bổ sung: service `mailhog` (SMTP :1025, UI :8025) + service `kafka` (KRaft mode, 1 broker, port :9092 cho host) | [ ] | `docker compose up` lên cả 3 (postgres + redis + kafka + mailhog) |
| **I1.9** | Outbox retry policy: 3 lần với backoff `1s` / `5s` / `30s`, sau 3 lần mark `FAILED` với `last_error`. Test bằng cách stop Kafka → event mark retry → restart → event SENT | [ ] | Sim Kafka down/up đều pass |
| **I3.1** | OpenAPI spec (`springdoc-openapi-starter-webmvc-ui`) cho `/auth/**` + Swagger UI | [ ] | Truy cập `/swagger-ui.html` thấy 9 endpoint auth |
| **I3.2** | Update `README.md` + `STRUCTURE.md`: thêm `modules/outbox`, `modules/notification` với boundary rõ | [ ] | Doc mới phản ánh cấu trúc thật |
| **I3.3** | Xóa folder `BE/` nếu đã migrate 100% sang `Backend/` + update `docker-compose.yml` root + kiểm tra `mvn clean install` pass | [ ] | `mvn clean install` exit 0, repo không còn `BE/` |

---

## 3. Chi tiết từng sprint

### Sprint S0 — Nền tảng chung ✅

> Đã xong — xem SPRINTS.md.bak-2026-07-18 phần "Sprint S0" để biết chi tiết.

### Sprint S1 — IAM core ✅

> Đã xong 6/6 micro — xem SPRINTS.md.bak-2026-07-18 phần "Sprint S1".

### Sprint S2 — IAM OTP + i18n ✅

> Đã xong 4/4 micro — xem SPRINTS.md.bak-2026-07-18 phần "Sprint S2".

### ~~Sprint S3 — Voice~~ REVOKED

> Lý do: sếp muốn redesign Voice từ đầu (cả logic business lẫn luồng code). Sprint này bị hủy. Khi nào redesign sẽ tạo sprint mới (đề xuất tên `V0` → `V5`) — **không thuộc scope IAM track này**.

### ~~Sprint S4 — Notification~~ THAY BẰNG **Sprint I1**

> Lý do thay: SPRINTS cũ đặt `OutboxEvent` trong `notification` (phương án A), nhưng sếp muốn outbox là cơ chế dùng chung → tách module `outbox` riêng (phương án D). Notification giờ chỉ là **consumer**, single-responsibility.

### Sprint I-1 — DB MIGRATION BACKFILL (3 micro)

**Mục tiêu:** Lấp gap migration cho IAM (hiện chỉ có V1 `otp_codes`; các table `iam_users`, `iam_roles`, `password_reset_tokens` đang do Hibernate tự tạo bằng `ddl-auto=update` — không track, không audit, không rollback). Sau sprint này, mọi thay đổi schema phải qua Flyway, Hibernate chỉ validate.

**Phụ thuộc đầu vào:** Không có.

**Phụ thuộc ra:** Có. I0.1 sẽ dùng V5 để ALTER refresh-token table. I1.2 sẽ dùng V6 cho outbox.

**Trạng thái (cập nhật 2026-07-18 11:05):** 3/3 micro code xong. **E2E verification pending** — sếp chốt 2026-07-18 11:00 "không cần test, làm xong sau này test sau". App restart + E2E test sẽ thực hiện sau.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **I-1.1** ✅ | **Bước 1**: Fix V1 (PostgreSQL syntax — `BINARY(16)` → `UUID`, `TINYINT(1)` → `BOOLEAN`, `DATETIME(6)` → `TIMESTAMPTZ(6)`, bỏ `ENGINE=InnoDB`). **Bước 2**: Tạo V2 `iam_roles` (name VARCHAR(32) UNIQUE, description). **Bước 3**: Tạo V3 `iam_users` (username/email UNIQUE, FK role_id, 4 indexes, 3 unique constraints). **Bước 4**: Tạo V4 `seed_iam_roles.sql` (3 roles USER/PRO/ADMIN — KHÔNG dùng `data.sql` vì Spring Boot khuyến cáo không dùng data.sql cùng Flyway). **Bước 5**: Update `UserJpaEntity`/`RoleJpaEntity` (`@Table(name = "iam_*")` + `@Index` + `@UniqueConstraint`). **Bước 6**: Sửa `bootstrap/pom.xml` (bỏ `flyway-mysql`, thêm `flyway-database-postgresql` + `postgresql` driver) | `mvn -pl bootstrap -am clean compile` exit 0; drop DB + restart → Flyway chạy V1+V2+V3+V4 OK; `SELECT * FROM iam_roles;` trả 3 dòng USER/PRO/ADMIN; `\d iam_users` thấy đủ 18 cột + 4 indexes + 3 unique constraints + FK `fk_iam_users_role` |
| **I-1.2** ✅ | **Code xong, E2E pending.** Tạo `V5__create_password_reset_tokens.sql`: bảng `iam_password_reset_tokens (id UUID PK, user_id UUID NOT NULL FK→iam_users.id, token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ(6) NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE, used_at TIMESTAMPTZ(6) NULL, created_at, updated_at, version, deleted, deleted_at, created_by, updated_by)` với index `(user_id)`, index `(expires_at)`. **Quan trọng**: `token_hash` lưu SHA-256 hex (64 char) của token raw — không lưu raw token. `PasswordResetTokenJpaEntity` extends `IamJpaBaseEntity` đã map sẵn các cột audit. `IamFacadeImpl.resetPassword(...)` đã dùng bảng này từ trước (không cần sửa code business) | E2E gọi `/auth/forgot-password` → token lưu DB với hash SHA-256; `/auth/reset-password` → token mark `used=true` *(pending)* |
| **I-1.3** ✅ | **Code xong, E2E pending.** Sửa `application.yml` (profile `default`): đổi `spring.jpa.hibernate.ddl-auto=update` → `validate`. Thêm `spring.jpa.properties.hibernate.hbm2ddl.schema_generation.create_schemas=false` để chắc chắn Hibernate không can thiệp. `@EnableJpaAuditing` (ở `IamJpaConfig`) không bị ảnh hưởng — chỉ touch `created_at`/`updated_at` ở runtime qua listener, không phải schema-level. **Profile `dev`/`prod`** chưa có file riêng → kế thừa `default` (đã verify chỉ có 1 file `application.yml`) | App vẫn start khi schema khớp entity; thiếu cột/thừa cột/sai kiểu → fail rõ ràng ngay startup *(pending)* |

### Sprint I0 — IAM HARDEN (4 micro)

**Mục tiêu:** Nâng chất lượng code + bảo mật cho flow auth hiện có. **Không thêm endpoint mới, không thêm feature.**

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **I0.1** | `RefreshToken` entity thêm field `revokedAt`, `revokedReason`. `IamFacadeImpl.refresh(...)` trước khi cấp token mới: set `revokedAt = now` cho token cũ. `JwtTokenProvider` thêm `validateRefreshToken` check `revokedAt == null`. DB migration V3 thêm 2 cột + index `(token_hash, revoked_at)` | Refresh lần 1 OK → refresh lần 2 với **token cũ** → trả `AUTH_TOKEN_INVALID` |
| **I0.2** | `LoginAttemptService` (Redis-backed): `INCR login:fail:{email}` mỗi lần login fail, set TTL 15 phút ở lần fail đầu. Sau 5 lần → throw `AUTH_ACCOUNT_LOCKED`. Reset counter khi login success. Cấu hình: `app.auth.max-fail-attempts=5`, `app.auth.lock-duration-minutes=15` | Login sai 5 lần liên tiếp → lần 6 trả `AUTH_ACCOUNT_LOCKED`. Đợi 15 phút → login lại OK với password đúng |
| **I0.3** | `RateLimitService` (Redis token-bucket): áp dụng cho `/auth/register`, `/auth/forgot-password`, `/auth/resend-otp`. Key `ratelimit:{endpoint}:{ip}`. Cấu hình: 3 req/giờ/IP, lỗi → throw `AUTH_RATE_LIMIT_EXCEEDED` (HTTP 429). GlobalExceptionHandler thêm handler trả 429 | Từ 1 IP, gọi `/auth/register` 4 lần trong 1h → lần 4 trả 429. IP khác vẫn OK |
| **I0.4** | Đọc `SecurityProperties.java` + từng method trong `AuthController.java`. Verify matrix: `register`/`login`/`google`/`refresh`/`verify-otp`/`resend-otp`/`forgot-password`/`reset-password` = public; `logout`/`complete-profile`/`change-password` = `@PreAuthorize("isAuthenticated()")`. Viết checklist, fix nếu thiếu `@PreAuthorize` | Doc `docs/auth-endpoint-matrix.md` + 0 endpoint sai auth |

### Sprint I1 — NOTIFICATION + OUTBOX + KAFKA (9 micro)

**Mục tiêu:** IAM gửi mail thật qua transactional outbox + Kafka. 9 event auth đều có mail đến user.

**Kiến trúc tổng quan:**

```
┌──────────┐    @Transactional     ┌──────────────┐    poll 5s    ┌──────────────┐
│ IAM      │  ──────────────────►  │ Outbox table │  ──────────►  │ OutboxRelay  │
│ Facade   │  enqueue(event)       │ (DB)         │               │ Scheduler    │
└──────────┘                       └──────────────┘               └──────┬───────┘
                                                                         │ publish
                                                                         ▼
                                                                  ┌──────────────┐
                                                                  │ Kafka topic  │
                                                                  │ notification │
                                                                  │ .email.v1    │
                                                                  └──────┬───────┘
                                                                         │ consume
                                                                         ▼
                                                                  ┌──────────────┐
                                                                  │ KafkaEmail   │
                                                                  │ Consumer     │
                                                                  └──────┬───────┘
                                                                         │
                                                                         ▼
                                                                  ┌──────────────┐
                                                                  │ Thymeleaf    │
                                                                  │ render + SMTP│
                                                                  └──────────────┘
```

**Tham chiếu dependency:**

```
iam ──depends──► outbox/api  (chỉ interface OutboxWriter)
outbox ──depends──► shared-kernel + shared-web + shared-event (Kafka producer)
notification ──depends──► outbox/api (chỉ KafkaConsumer config), shared-kernel + shared-web
bootstrap ──depends──► tất cả module
```

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **I1.1** | Tạo `modules/outbox/` skeleton (pom.xml depend `shared-kernel` + `spring-kafka` + `spring-data-jpa` + `flyway-mysql`). Tạo `outbox/api/dto/OutboxEventPayload.java` (record: `String topic`, `String key`, `String aggregateType`, `String aggregateId`, `Map<String, Object> payload`, `Instant occurredAt`). Tạo `outbox/api/OutboxWriter.java` interface (`void enqueue(OutboxEventPayload payload)`). Tạo `outbox/infrastructure/config/OutboxTransactionalConfig.java` (`@TransactionalEventListener(phase = AFTER_COMMIT)`). Outbox entity + repo làm ở I1.2 | `mvn -pl modules/outbox -am compile` exit 0 |
| **I1.2** | `outbox/infrastructure/persistence/OutboxEventJpaEntity.java` (id UUID, aggregate_type VARCHAR(64), aggregate_id VARCHAR(128), topic VARCHAR(128), payload_key VARCHAR(128) nullable, payload JSONB, status enum `PENDING`/`PROCESSING`/`SENT`/`FAILED`, retry_count INT default 0, created_at, sent_at nullable, last_error TEXT nullable, next_attempt_at nullable). `OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID>` + custom query `@Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' AND next_attempt_at <= now() FOR UPDATE SKIP LOCKED LIMIT :batch", nativeQuery = true)`. Flyway migration `V2__create_outbox_events.sql` (PostgreSQL JSONB + index `(status, next_attempt_at)`) | `mvn -pl modules/outbox -am compile` exit 0; Flyway tree có V2 |
| **I1.3** | `outbox/infrastructure/scheduler/OutboxRelayScheduler.java` (`@Scheduled(fixedDelay = 5000)`, `@Transactional`, `@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)`). Logic: (1) gọi repo `claimBatch(batch=20)` → set status `PROCESSING`, (2) cho từng event publish lên Kafka qua `KafkaTemplate.send(topic, key, payload)`, (3) mark `SENT` nếu thành công / tăng `retry_count` + set `next_attempt_at = now + backoff(retry_count)` + set `last_error` nếu fail. `outbox/infrastructure/config/OutboxKafkaConfig.java` (`@Configuration` + `NewTopic` bean cho `notification.email.v1` partitions=3 replication=1). `app.outbox.relay.batch-size=20` config | E2E: enqueue event trong transaction IAM → 5s sau event được publish lên Kafka topic `notification.email.v1` (verify bằng `kafka-console-consumer.sh --topic notification.email.v1 --from-beginning`) |
| **I1.4** | `notification/api/event/EmailRequestedIntegrationEvent.java` (record: `String eventId`, `String to`, `String subjectKey`, `String templateName`, `Map<String, Object> model`, `Locale locale`, `Instant occurredAt`). `notification/api/NotificationFacade.java` interface (`void consumeEmailRequest(EmailRequestedIntegrationEvent event)`). Module `notification` KHÔNG có scheduler, KHÔNG có producer — chỉ là **consumer of Kafka** | `mvn -pl modules/notification -am compile` exit 0 |
| **I1.5** | `notification/infrastructure/mail/` — `MailService` interface (`send(String to, String subject, String htmlBody)`). `MailServiceImpl` (Spring `JavaMailSender`, đọc SMTP host/port/username/password từ `MailProperties`, profile `prod`). `NoOpMailService` (profile `dev`/`test`, chỉ log). `ThymeleafConfig` (`SpringTemplateEngine` resolve từ `classpath:/templates/email/`). 6 template HTML với i18n: `templates/email/_layout.html`, `register-otp.html`, `welcome-google.html`, `password-reset.html`, `password-changed.html`, `linked-google.html`. Mỗi template dùng `${messages.msgKey}` resolve qua `MessageSource` theo `Locale` | Gửi mail test qua MailHog (port 1025): subject + body render đúng tiếng Việt/Anh |
| **I1.6** | `notification/infrastructure/messaging/KafkaEmailConsumer.java` (`@KafkaListener(topics = "notification.email.v1", groupId = "notification-email-consumer")`). Logic: parse `EmailRequestedIntegrationEvent` từ Kafka payload → resolve template theo `event.templateName()` → set `Locale` cho `MessageSource` → render Thymeleaf với `event.model()` → lấy subject từ `event.subjectKey()` qua `MessageSource` → gọi `mailService.send(event.to(), subject, htmlBody)`. Wrap trong try-catch: nếu fail thì rethrow để Kafka redeliver (theo `DefaultErrorHandler` + `FixedBackOff(1000, 3)`) | E2E: outbox poll → Kafka publish → consumer nhận → mail render → SMTP gửi → mail đến MailHog UI :8025 |
| **I1.7** | Trong `iam/infrastructure/security/event/` — thay `LoggingAuthEventPublisher` bằng `OutboxAuthEventPublisher implements AuthEventPublisher`. Inject `OutboxWriter`. Mỗi `publishXxx(...)` method gọi `outboxWriter.enqueue(new OutboxEventPayload("notification.email.v1", userId.toString(), "User", userId.toString(), payloadMap, Instant.now()))` với `payloadMap` chứa `templateName`, `subjectKey`, `to`, `model`. **Quan trọng**: method phải được gọi trong `@Transactional` callback của `IamFacadeImpl` — nếu IAM transaction rollback thì outbox event cũng rollback (transactional outbox guarantee) | E2E: register user mới → 5s sau OTP mail đến MailHog. Forgot password → reset link mail đến. Google login mới → welcome-google mail đến. Đổi password → password-changed mail đến |
| **I1.8** | `docker-compose.yml` ở root repo — thêm service `mailhog`: image `mailhog/mailhog:v1.0.1`, ports `1025:1025` (SMTP) + `8025:8025` (UI). Thêm service `kafka`: image `apache/kafka:3.7.0` (KRaft mode, không cần Zookeeper), env `KAFKA_NODE_ID=1` `KAFKA_PROCESS_ROLES=broker,controller` `KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093` `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092` `KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER` `CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk` | `docker compose up -d postgres redis kafka mailhog` → tất cả healthy. Truy cập `localhost:8025` thấy MailHog UI |
| **I1.9** | `outbox/infrastructure/scheduler/OutboxRelayScheduler` — thêm retry logic: nếu publish fail, tăng `retry_count`. Nếu `retry_count >= 3` thì set status `FAILED` + `last_error`. Backoff: `next_attempt_at = now + Duration.ofSeconds(1 * (5 ^ retry_count))` (1s, 5s, 25s). `OutboxEventJpaRepository` thêm method `findAllByStatusAndRetryCountLessThan(FAILED, 3)` để có thể retry thủ công sau. `bootstrap` config thêm endpoint admin `GET /admin/outbox/failed` (chỉ role `ADMIN` — IAM scope mở rộng nhẹ để có role admin, hoặc dùng `app.outbox.admin.enabled=true` để gate) | Test: stop Kafka → enqueue event → 3 lần retry fail → mark FAILED → restart Kafka → manual retry → SENT |

### Sprint I3 — CLEANUP (3 micro)

**Mục tiêu:** Doc + OpenAPI + xóa `BE/` cũ.

| ID | Nội dung | Output kiểm chứng |
|----|----------|-------------------|
| **I3.1** | Thêm `springdoc-openapi-starter-webmvc-ui:2.6.0` vào `bootstrap/pom.xml`. Config trong `application.yml`: `springdoc.swagger-ui.path=/swagger-ui.html`, `springdoc.api-docs.path=/v3/api-docs`. Thêm `@Operation`, `@ApiResponse`, `@Schema` annotations cho 9 endpoint trong `AuthController.java` | Truy cập `localhost:8080/swagger-ui.html` thấy đầy đủ 9 endpoint auth với mô tả |
| **I3.2** | Update `Backend/README.md`: thêm dòng `\| modules/outbox \| Transactional outbox + Kafka relay \|` và `\| modules/notification \| Email consumer (Thymeleaf + SMTP) \|`. Update `Backend/STRUCTURE.md` (nếu có) thêm sơ đồ dependency mới: iam → outbox → kafka → notification. Thêm `docs/auth-endpoint-matrix.md` (output của I0.4) | README mới phản ánh đúng 5 module: `shared-kernel`, `shared-web`, `bootstrap`, `modules/iam`, `modules/outbox`, `modules/notification` |
| **I3.3** | Verify 0 file trong `Backend/BE/` còn reference code cũ. Sau đó `rm -rf Backend/BE/`. Update root `docker-compose.yml` (nếu có) trỏ vào `Backend/`. Chạy `mvn clean install` từ root | `mvn clean install` exit 0. Repo không còn `BE/` |

---

## 4. Quy tắc chung khi thực hiện

### Code conventions
- **Java 21 + Spring Boot 4.1.x**, Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`, `@Builder`)
- **JPA entity**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (KHÔNG `@Data` cho entity)
- **DTO**: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- **Record** ưu tiên cho event immutable (`EmailRequestedIntegrationEvent`, `OutboxEventPayload`)
- **Không comment trong source code** (file markdown này là ngoại lệ)
- **Không tự tạo test** — chỉ khi sếp yêu cầu
- **i18n**: mọi message trả cho user qua `MessageSource` với key `UPPER_SNAKE_CASE`
- **Logging**: `log.info("event: x={}, y={}", x, y)` — có context, không log PII

### Dependency rules
- `shared-kernel` KHÔNG phụ thuộc module nào, KHÔNG có Spring annotation
- `shared-web` phụ thuộc `shared-kernel` + Spring Web
- Module nghiệp vụ (`iam`, `outbox`, `notification`) chỉ depend `shared-kernel` + `shared-web` + `api/` của module khác (qua facade/writer interface)
- **Outbox module có thể depend `notification/api`** KHÔNG — `outbox` là generic, không biết consumer là ai. Consumer tự listen Kafka topic.
- `core` KHÔNG import `infrastructure/` của chính nó
- `api` KHÔNG chứa `@Entity`, `@RestController`, `@Repository`
- `bootstrap` depend tất cả module, chứa main class

### Naming
- Class: `PascalCase` danh từ
- Method: `camelCase` động từ
- Boolean: prefix `is/has/can/should`
- Constant: `UPPER_SNAKE_CASE`

---

## 5. Câu hỏi đang chờ trả lời (cập nhật 2026-07-18)

| # | Câu hỏi | Sprint áp dụng | Status |
|---|---------|----------------|:------:|
| Q1 | MapStruct (auto mapper) hay mapper thủ công? | S1 | ✅ Đã chốt MapStruct |
| Q2 | JWT dùng HS256 (secret env) hay RSA (JWKS)? | S1 | ✅ Đã chốt HS256 |
| **Q3** | ~~Entity `OutboxEvent` đặt ở module nào?~~ | ~~S2 / S4~~ | ✅ **Chốt 2026-07-18: module `outbox` riêng (phương án D)** |
| Q4 | Khi user xoá account, quota reset qua `VoiceFacade` (sync) hay publish Kafka event (async)? | (đã bỏ — Voice revoke) | 🗑️ |
| Q5 | `ssml` Lua script đặt ở `modules/voice/src/main/resources/scripts/` hay `bootstrap`? | (đã bỏ — Voice revoke) | 🗑️ |
| **Q6** | Outbox poll interval là `fixedDelay=5000` hay configurable qua property? | I1.3 | 🆕 |
| **Q7** | Khi outbox event fail 3 lần, mark FAILED. Có cần endpoint admin retry hay không? | I1.9 | 🆕 |
| **Q8** | Locale trong email resolve từ `user.preferredLocale` (DB) hay `Accept-Language` header? | I1.5 | 🆕 |
| **Q9** | Kafka topic dùng 1 topic chung `notification.email.v1` hay nhiều topic theo template? | I1.3 / I1.6 | 🆕 |

> Em recommend default cho các câu Q6-Q9 (sẽ hỏi sếp khi đến sprint tương ứng):
> - Q6: configurable `app.outbox.relay.fixed-delay-ms=5000`
> - Q7: có endpoint admin retry (role `ADMIN`)
> - Q8: ưu tiên `user.preferredLocale` → fallback `Accept-Language` → fallback `messages_en`
> - Q9: 1 topic chung `notification.email.v1`, phân loại theo `templateName` trong payload

---

## 6. Definition of Done cho mỗi micro

Một micro-sprint được tính là **xong** khi TẤT CẢ điều kiện sau thoả mãn:

- [ ] Source code đã được move / viết theo đúng cấu trúc `api`/`core`/`infrastructure`
- [ ] `mvn -pl <module> -am compile` exit 0
- [ ] Nếu có endpoint mới: test thủ công qua curl/Postman pass
- [ ] Nếu có Flyway migration: file `.sql` trong `src/main/resources/db/migration/` đúng convention `V{N}__description.sql`
- [ ] Nếu có event Kafka: producer publish + consumer subscribe đều log OK
- [ ] Nếu có template email: render test qua MailHog với cả `vi` + `en`
- [ ] Đã xóa file tương ứng trong `BE/` (nếu là move chứ không phải viết mới)
- [ ] Không có code comment thừa, không có TODO/FIXME
- [ ] Không hardcode message user-facing (i18n qua MessageSource)
- [ ] Không log PII (password, OTP raw, token raw)
- [ ] Linter `ReadLints` không báo lỗi mới

---

## 7. Progress summary

Cập nhật mỗi lần tick xong micro:

| Lần cập nhật | Micro xong | Tổng đã xong |
|--------------|-----------|--------------|
| (khởi tạo) | — | 0 / 25 (plan cũ) |
| 2026-07-17 05:22 | M0.1 | 1 / 25 |
| 2026-07-17 05:32 | M0.2 | 2 / 25 |
| 2026-07-17 05:46 | M0.3 | 3 / 25 |
| 2026-07-17 06:20 | M1.1a | 4 / 25 |
| 2026-07-17 06:45 | M1.2 | 5 / 25 |
| 2026-07-17 07:18 | M1.3 | 6 / 25 |
| 2026-07-17 07:40 | M1.4 | 7 / 25 |
| 2026-07-17 07:55 | M1.5 | 8 / 25 |
| 2026-07-17 08:25 | M1.6 | 9 / 25 |
| 2026-07-18 08:14 | M2.1 | 10 / 25 |
| 2026-07-18 08:35 | M2.2 | 11 / 25 |
| 2026-07-18 08:44 | M2.3 | 12 / 25 |
| 2026-07-18 08:53 | M2.4 | 13 / 25 |
| **2026-07-18 09:04** | **REVOKE S3-S5, chốt plan mới I0-I3** | **13 / 25 → reset về 13 / 32 (13 sprint cũ + 16 sprint mới I0-I3, S3-S5 bị hủy 6 micro)** |
| **2026-07-18 09:10** | **CHÈN Sprint I-1 (DB migration backfill) — 3 micro: V2 iam_roles, V3 iam_users, V4 password_reset_tokens + switch `ddl-auto` → `validate`** | **13 / 32 → 13 / 35 (13 done + 19 todo: I-1=3, I0=4, I1=9, I3=3)** |
| **2026-07-18 09:35** | **I-1.1 DONE**: Fix V1 (MySQL→PostgreSQL syntax), tạo V2/V3/V4 migrations (V2=iam_roles, V3=iam_users, V4=seed 3 roles), update UserJpaEntity+RoleJpaEntity (`@Table=iam_*` + indexes + uniqueConstraints), sửa bootstrap/pom.xml (bỏ flyway-mysql, thêm flyway-database-postgresql + postgresql driver). NOTE: dùng V4 cho seed (không dùng data.sql vì Spring Boot khuyến cáo không dùng data.sql khi có Flyway). I-1.2 phải đổi từ V4 → V5 | **13 / 35 → 14 / 35 (I-1.1 xong, còn I-1.2 + I-1.3)** |
| **2026-07-18 11:05** | **I-1.2 + I-1.3 code DONE**: V5 `iam_password_reset_tokens` đã tạo + apply (token_hash VARCHAR(64) UNIQUE, FK user_id, 2 index user_id/expires_at); `application.yml` đã đổi `ddl-auto: validate` + `create_schemas: false`. **E2E test pending** — sếp chốt 11:00 "không cần test, làm xong sau này test sau". Schema validation restart + E2E `/auth/forgot-password` + `/auth/reset-password` để dành cho sau | **14 / 35 → 16 / 35 (I-1 xong code, E2E pending). Tổng: 16 micro code xong / 35 tổng — còn 19 (I0=4, I1=9, I3=3) + 3 E2E verify cho I-1** |

> **Tổng cập nhật cuối cùng**: 13 micro đã xong (S0=3, S1=6, S2=4) + 19 micro sẽ làm (I-1=3, I0=4, I1=9, I3=3) = **32 micro**. S3-S5 (12 micro) đã bị revoke.

> **Tổng cập nhật 2026-07-18 11:05**: Sprint I-1 hoàn thành **3/3 micro về code**. E2E verification (`mvn clean install` + restart + `/auth/forgot-password` + `/auth/reset-password`) pending theo yêu cầu sếp. Tổng code xong: **16 / 35** (S0=3 + S1=6 + S2=4 + I-1=3). Còn lại: **19 micro** (I0=4, I1=9, I3=3).

---

## 8. Lịch sử thay đổi plan

| Ngày | Thay đổi | Lý do |
|------|----------|-------|
| 2026-07-18 09:04 | Revoke toàn bộ S3 (Voice) — sếp muốn redesign | Sếp yêu cầu xây lại Voice từ đầu |
| 2026-07-18 09:04 | Revoke S4-S5 (Notification cũ + Cleanup cũ), thay bằng **I0-I3** | Sếp muốn hoàn thiện IAM + Notification trước |
| 2026-07-18 09:04 | Chốt kiến trúc: tách **module `outbox` riêng** (phương án D) | Outbox là cơ chế dùng chung, không gắn vào notification |
| 2026-07-18 09:04 | Notification là **consumer only** (không scheduler, không producer) | Single-responsibility; producer ở `outbox` |
| 2026-07-18 09:04 | docker-compose.yml thêm Kafka + MailHog | Cần infra cho I1 E2E |
| 2026-07-18 09:10 | **Phát hiện gap migration**: V1 chỉ có `otp_codes`; `iam_users`/`iam_roles`/`password_reset_tokens` đang dùng Hibernate auto-DDL | Sếp hỏi về migration, em khảo sát thấy thiếu 3 migration cho IAM |
| 2026-07-18 09:10 | **CHÈN Sprint I-1** (3 micro) trước Sprint I0: V2 `iam_roles`+`iam_user_roles`, V3 `iam_users`, V4 `password_reset_tokens` + switch `ddl-auto` từ `update` sang `validate` | Đảm bảo I0.1 (V5) và I1.2 (V6) có schema nền tảng sạch, không còn phụ thuộc auto-DDL |
| 2026-07-18 09:10 | Đánh lại số V migration: V1=otp, V2/V3/V4=IAM backfill, V5=refresh-token revocation, V6=outbox events | Tránh đụng số V khi các sprint sau chạy đồng thời |
| 2026-07-18 09:35 | **I-1.1 HOÀN THÀNH**: V1 đã fix MySQL→PostgreSQL, V2/V3/V4 chạy thành công, 3 roles (USER/PRO/ADMIN) đã seed. Dùng V4 cho seed (không dùng data.sql vì Spring Boot khuyến cáo) → I-1.2 phải đổi từ V4 sang V5 | Spring Boot không support `data.sql` + Flyway cùng lúc; cách chuẩn là seed bằng migration SQL |
| 2026-07-18 11:05 | **I-1.2 + I-1.3 HOÀN THÀNH (code)**: V5 `iam_password_reset_tokens` tạo + apply với token_hash VARCHAR(64) UNIQUE + FK→iam_users + 2 index; `application.yml` đổi `ddl-auto: validate` + `create_schemas: false`. PasswordResetTokenJpaEntity extends IamJpaBaseEntity match schema. **E2E pending** theo yêu cầu sếp | Schema validation sẽ bảo vệ các sprint sau (I0.1 cần ALTER refresh-token; I1.2 cần tạo outbox). Nếu thiếu cột → fail ngay ở startup |
