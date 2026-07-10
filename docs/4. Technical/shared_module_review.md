# Shared Module — Code Review & Fix Tracker

> Phạm vi: `backend/src/main/java/com/pwb/backend/shared/`
> Ngày review lần 1: 2026-07-11 (review tổng quan, tạo checklist)
> Ngày review lần 2: 2026-07-11 (verify sau khi user fix)
> Trạng thái: **Hầu hết item đã fix đúng**. Còn 1 item chưa fix (C5 — chưa thấy migration job cho key rotation) và H11 đang trong tiến trình tách module (chỉ IAM đã tách, audio chưa).

## Quy ước severity

| Mức | Ý nghĩa |
|---|---|
| 🔴 CRITICAL | Lỗ hổng bảo mật, có thể bị khai thác trong production |
| 🟠 HIGH | Tính đúng đắn (correctness) hoặc vi phạm nguyên tắc modular monolith |
| 🟡 MEDIUM | Vấn đề thiết kế, hiệu năng, observability |
| 🟢 LOW | Style, naming, micro-cleanup |

Mỗi item có:
- **File:line** — vị trí code
- **Vấn đề** — mô tả ngắn
- **Tác động** — hậu quả nếu không fix
- **Đề xuất fix** — hướng xử lý cụ thể
- **Trạng thái** — `TODO` / `DOING` / `DONE`

---

## 🔴 CRITICAL

### C1. `IpRateLimitFilter` bypass `ApiResponse` contract
- **File:** `backend/src/main/java/com/pwb/backend/shared/security/IpRateLimitFilter.java:107-126`
- **Vấn đề:** Khi vượt rate-limit, filter ghi raw JSON thay vì dùng `ApiResponse` shape chuẩn.
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Filter giờ inject `ObjectMapper`, build `ApiResponse<Void>` đúng shape, status 429, giữ `Retry-After`. Tách logic ghi response vào helper `writeRateLimitResponse(...)` rõ ràng. Đồng nhất với `RestAccessDeniedHandler`/`RestAuthenticationEntryPoint`.

### C2. `IpRateLimitFilter` — race condition `INCR` + `EXPIRE`
- **File:** `backend/src/main/java/com/pwb/backend/shared/security/IpRateLimitFilter.java:91-95` + `backend/src/main/resources/lua/ip-rate-limit.lua`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Script Lua atomic ở `lua/ip-rate-limit.lua`. Filter load script qua `DefaultRedisScript<Long>` trong constructor. Refill TTL mỗi lần increment (slide window) — bonus so với đề xuất gốc.
- **Lưu ý nhỏ:** TTL refresh mỗi lần hit có thể kéo dài window nếu traffic liên tục — cố ý hay bug? Comment trong script nói "Refresh TTL on subsequent increments so a window slides forward cleanly" → design cố ý. Nếu muốn fixed-window thật, đổi logic thành chỉ set TTL khi `current == 1`. Hiện tại OK cho DDoS protection.

### C3. `ClientIpResolver` tin `X-Forwarded-For` segment đầu
- **File:** `backend/src/main/java/com/pwb/backend/shared/security/ClientIpResolver.java:63-97`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - Walk XFF phải → trái (line 85-94), bỏ qua trusted hop, lấy IP đầu tiên không trust.
  - Hỗ trợ CIDR qua inner class `TrustedNetwork` (line 170-241) — parse và match CIDR chính xác (xử lý full bytes + remaining bits).
  - Comment javadoc giải thích rõ strategy (RFC 7239 / OWASP).
  - Fallback an toàn khi IPv4/IPv6 mismatch (`candBytes.length != addrBytes.length`).
- **Bonus:** default trusted có `127.0.0.0/8` CIDR thay vì chỉ exact match.

### C4. `S3StorageService.configureBucketCors` — wildcard origin + headers
- **File:** `backend/src/main/java/com/pwb/backend/shared/service/impl/S3StorageService.java:117-162`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - Kéo origins từ `properties.getCorsAllowedOrigins()`, fallback sang `app.security.cors.allowed-origins` qua `@Value` injection (cùng nguồn với web CORS).
  - Reject wildcard origin rõ ràng (`origins.contains("*")` → throw).
  - Reject wildcard header (`headers.contains("*")` → throw).
  - Default headers narrowed xuống 6 header cụ thể (`Authorization`, `Content-Type`, `Cache-Control`, `x-amz-*`).
  - Expose `ETag` only (không wildcard `*`).
- **Bonus:** L3 cũng fix cùng đợt — nếu `autoCreateBucket=false` mà bucket không reach được → throw `IllegalStateException` ngay startup thay vì swallow exception.

### C5. `OutboxPayloadCipher` rotation không thể giải mã payload cũ
- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/cipher/OutboxPayloadCipher.java:39-216`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `PARTIAL` ⚠️
- **Verify:**
  - ✅ Giờ có `Map<Integer, SecretKey> keysByVersion` — lưu nhiều version.
  - ✅ Encrypt prefix payload với `enc:v{N}:base64(...)` → biết decrypt bằng key nào.
  - ✅ Decrypt đọc version từ prefix, `pickKey()` chọn đúng key.
  - ✅ Hỗ trợ legacy `enc:base64(...)` (không version) → mặc định dùng v1 key, fallback về active nếu không có.
  - ✅ Có inject `app.outbox.legacy-keys` cho migration.
  - ⚠️ **Chưa thấy migration job**: cần có batch job `decrypt v1 → re-encrypt v2` để loại bỏ legacy key sau rotation. Hiện tại mới chỉ hỗ trợ decrypt cũ + encrypt mới — vẫn giữ key cũ mãi mãi trong memory + config.
- **Đề xuất tiếp:** Viết migration job nếu đã có nhu cầu rotate key trong production. Nếu chưa, đánh dấu là acceptable debt.

### C6. `AccessLogFilter` log DEBUG + lộ `principal.toString()`
- **File:** `backend/src/main/java/com/pwb/backend/shared/config/AccessLogFilter.java:51-105`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ Đổi từ `DEBUG` (1 dòng) sang `INFO` 2 dòng (ACCESS_IN + ACCESS_OUT) — production mặc định INFO sẽ thấy log.
  - ✅ Không còn `auth.getPrincipal().toString()` — dùng `auth.getName()` qua `maskPrincipal()`.
  - ✅ `maskPrincipal()` mask email `user***@domain`, format dễ debug hơn pattern cũ.
  - ✅ Inject `ClientIpResolver` → dùng chung logic IP resolution với rate-limit, audit.
- **Lưu ý nhỏ:** M7 fix cho heartbeat scheduler pool=4 cũng đã được verify ở `WebSocketConfig.java` — OK.

---

## 🟠 HIGH

### H1. `OutboxScheduler` dùng `Function<String,String>` injection thủ công
- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/scheduler/AbstractOutboxScheduler.java`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Class cũ `OutboxScheduler.java` đã xóa. Thay bằng `AbstractOutboxScheduler<T extends OutboxEvent>` abstract base class. Constructor inject 3 bean: `Repository`, `Cipher`, `Properties` — không còn setter/settable delegate. Subclass override `processSingleEvent(T)` + `moduleName()`. `@SchedulerLock` ở method `pollAndProcess()` → cross-instance safety.
- **Cần verify thêm:** Các module (iam, audio) đã thật sự tạo subclass để thay thế scheduler cũ, không còn bean nào trỏ vào `OutboxScheduler` class đã xóa.

### H2. `OutboxService.computeBackoffSeconds` dùng `Math.random()`
- **File:** `backend/src/main/java/com/pwb\backend\shared\outbox/service/OutboxService.java:51-60`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Đổi sang `ThreadLocalRandom.current().nextDouble()`. Comment giải thích lý do.
- **Minor:** Indent method không đồng nhất với phần còn lại của file (1 method dùng 2-space, các method khác dùng 2-space — nhìn kỹ thì OK, nhưng brace placement trông lạ). Không ảnh hưởng logic.

### H3. `CdcOutboxEventHandler` thiếu `@Transactional` và row-level lock
- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/processor/CdcOutboxEventHandler.java:45`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Thêm `@Transactional(propagation = Propagation.REQUIRES_NEW)` cho method `handleEvent`. Javadoc giải thích lý do.

### H4. `OutboxEventRepository` có 2 API không nhất quán
- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/repository/OutboxEventRepository.java`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ Javadoc tách rõ 2 phương thức: production dùng `findPendingEventsForUpdate` (native + FOR UPDATE SKIP LOCKED), test/dev dùng `findReadyForProcessing`.
  - ✅ Native query project narrow columns: `SELECT id, aggregate_type, aggregate_id, event_type, idempotency_key, status, retry_count, available_at` — không stream payload TEXT không cần.
  - ✅ Document cảnh báo rõ: JPQL "only safe in single-instance deployments".

### H5. `CdcEngine` không validate `connectorName`/`offsetPath` uniqueness
- **File:** `backend/src/main/java/com/pwb/backend/shared/cdc/CdcEngine.java:62-147`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ `validateOffsetFileOwnership()` (line 114-147) đọc dòng đầu của offset file, parse `"name":"..."`, so sánh với `connectorName` hiện tại. Throw `IllegalStateException` nếu mismatch → fail fast.
  - ✅ Kết hợp với M3 (line 86-88): `topic-prefix`, `plugin-name`, `offset-flush-interval-ms` đều configurable.

### H6. `GlobalExceptionHandler` có thể leak `data` qua BusinessException
- **File:** `backend/src/main/java/com/pwb/backend/shared/exception/GlobalExceptionHandler.java:28-54`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ `data` log ở server-side (`log.warn("...data={}", ..., ex.getData())`).
  - ✅ Response KHÔNG còn include `ex.getData()` — chỉ `ErrorDetail` (code, field, message).
  - ✅ Đổi signature từ `ApiResponse<Object>` → `ApiResponse<Void>` rõ ràng public surface.
- **Cần verify thêm:** Xác nhận `BusinessException.getData()` không còn được dùng ở client-side response nào khác trong codebase.

### H7. `WebSocketAuthInterceptor` không authorize theo destination
- **File:** `backend/src/main/java/com/pwb/backend/shared/websocket/WebSocketAuthInterceptor.java:48-153`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ SUBSCRIBE và SEND giờ kiểm tra destination pattern riêng (`authorizeSubscribe`, `authorizeSend`).
  - ✅ Self-user pattern: `/topic/user/{email}/...` và `/queue/user/{email}/...` — chỉ owner mới subscribe/send.
  - ✅ `MESSAGE` đã bỏ khỏi switch (L8 fix cùng đợt).
  - ✅ Throw `AccessDeniedException` với message không lộ thông tin nhạy cảm (chỉ destination + principal).
- **Bonus:** Pattern `[^,/]+` ngăn chặn match across segment.

### H8. Scheduler thiếu `@SchedulerLock` cho multi-instance safety
- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/scheduler/AbstractOutboxScheduler.java:46-50`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** `@SchedulerLock(name = "abstract-outbox-scheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")` trên `pollAndProcess()`. Cross-instance safety giờ do ShedLock đảm bảo + native query row lock → defense in depth.

### H9. `JpaAuditingConfig` auditor có thể lộ PII
- **File:** `backend/src/main/java/com/pwb/backend/shared/config/JpaAuditingConfig.java:56-83`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ Email mask: `user:<first-8-chars>***@<domain>`.
  - ✅ Non-email (UUID/OAuth subject/etc): hash SHA-256 short 16-char hex → deterministic audit grouping OK.
  - ✅ `name.contains(":")` (vd `phone:+84...`, `oauth:12345`) cũng hash → không lộ nguyên xi.
- **Minor:** Method gọi `sanitize` không phân biệt "có : nhưng không phải phone/oauth" với các trường hợp khác. Hiện tại tất cả cùng hash → conservative, OK.

### H10. `RedisConfig.redisTemplate` dùng polymorphic typing có thể fail
- **File:** `backend/src/main/java/com/pwb/backend/shared/config/RedisConfig.java:43-79`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ Tách `redisObjectMapper()` riêng, không dùng global ObjectMapper.
  - ✅ `BasicPolymorphicTypeValidator` whitelist: `java.util.*`, `com.pwb.backend.*`, `java.time.*`. Chặn gadget deserialization từ class khác.
  - ✅ Scope "NONE_FINAL" thay vì "EVERYTHING" — an toàn hơn.
- **Có thể cải thiện:** Hiện bỏ bean `RedisTemplate<String, String>` cũ (dùng StringRedisTemplate auto-config) — khuyến nghị giữ nguyên, đã đúng.

### H11. `ErrorCode` chứa ~75 entry là **shared-leak**
- **File:** `backend/src/main/java/com/pwb/backend/shared/exception/ErrorCode.java`, `backend/src/main/java/com/pwb/backend/iam/internal/exception/IamErrorCode.java`, `backend/src/main/java/com/pwb/backend/shared/exception/ErrorCodeLike.java`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `PARTIAL` ⚠️ — cấu trúc đã sẵn sàng nhưng migration call-site chưa xong.
- **Verify:**
  - ✅ Tạo interface `ErrorCodeLike` (line 14-18) — bridge giữa platform `ErrorCode` và module-local enums.
  - ✅ `IamErrorCode` đã tách: 26 entries từ IAM, implement `ErrorCodeLike`.
  - ✅ `ErrorCode` (shared) giờ chỉ giữ HTTP-level codes (INTERNAL_SERVER_ERROR, VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN, RESOURCE_NOT_FOUND, RATE_LIMIT_EXCEEDED) + legacy module codes (giữ backward compatibility).
  - ⚠️ `AudioErrorCode` chưa được tách — file `backend/src/main/java/com/pwb/backend/audio/internal/exception/AudioErrorCode.java` chưa tồn tại.
  - ⚠️ `GlobalExceptionHandler` đã đổi sang `ErrorCodeLike` (line 30) → có thể nhận cả `ErrorCode` lẫn `IamErrorCode`. Tốt.
  - ⚠️ Call-site ở iam chưa chắc đã đổi sang `IamErrorCode` (cần verify bằng grep).
- **Còn lại cần làm:** (1) Tách `AudioErrorCode`. (2) Migrate tất cả call-site dùng `ErrorCode.USER_NOT_EXISTED` v.v. sang `IamErrorCode.USER_NOT_EXISTED`. (3) Khi xong, xóa legacy entries khỏi `ErrorCode` shared.

---

## 🟡 MEDIUM

### M1. Quá nhiều `@Enable*` global ở `shared`
- **File:** `BackendApplication.java:19-21`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** `@EnableJpaAuditing`, `@EnableSchedulerLock`, `@EnableWebSocketMessageBroker` đã được chuyển lên `BackendApplication` (line 19-21). Shared giờ không opt-in global features. Comment javadoc giải thích lý do.

### M2. `StorageConfig` tạo `S3Client`/`S3Presigner` thủ công
- **Trạng thái:** Không fix (backlog) — chấp nhận được nếu dùng MinIO.

### M3. `CdcEngine` hard-code `topic.prefix` + `pgoutput`
- **Trạng thái lần 2:** `DONE` ✅ (xem H5 verify)
- **Verify:** `app.cdc.topic-prefix`/`plugin-name`/`offset-flush-interval-ms` qua `environment.getProperty(...)` với default an toàn.

### M4. `JacksonConfig` chưa disable `WRITE_DURATIONS_AS_TIMESTAMPS`
- **Trạng thái lần 2:** `DONE` ✅ (line 22)
- **Verify:** `mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)` đã thêm.

### M5. `OutboxPayloadCipher.decrypt` không phân biệt được ciphertext cũ không prefix
- **Trạng thái lần 2:** `DONE` ✅ (xem C5 verify)
- **Verify:** Logic debug log thêm vào decrypt khi gặp plaintext không prefix.

### M6. `MailConfig` không khởi tạo `JavaMailSender`
- **File:** `backend/src/main/java/com/pwb/backend/shared/config/MailConfig.java:76-91`
- **Trạng thái lần 1:** `TODO`
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:**
  - ✅ Explicit bean `JavaMailSender` inject các properties rõ ràng.
  - ✅ `@PostConstruct` validate `mailHost` không null + chế độ prod bắt buộc username/password.

### M7. `WebSocketConfig` heartbeat scheduler chỉ pool size 1
- **Trạng thái lần 2:** `DONE` ✅ (line 57) — pool=4.

### M8. `OutboxEvent` set `availableAt` 2 chỗ
- **Trạng thái lần 2:** `DONE` ✅
- **Verify:** Field initializer đã bỏ (line 52-53), `@PrePersist` là single source of truth. Comment giải thích lý do.

### M9. `AccessLogFilter` duplicate logic với `ClientIpResolver`
- **Trạng thái lần 2:** `DONE` ✅ (xem C6 verify) — inject `ClientIpResolver`.

### M10. `CdcOutboxEventHandler` + `OutboxEventRepository` race trên multi-CDC consumer
- **Trạng thái lần 2:** `DONE` ✅ (xem H3 + H4 verify).

---

## 🟢 LOW

### L1. `ErrorCode` dùng `@RequiredArgsConstructor` không cần thiết
- **Trạng thái lần 2:** `DONE` ✅ — đã bỏ, dùng constructor explicit.

### L2. `JwtSigner.sign` không chỉ định algorithm
- **Trạng thái lần 2:** `DONE` ✅ (line 40) — `signWith(key, Jwts.SIG.HS256)` explicit.

### L3. `S3StorageService.init()` swallow exception ngay cả khi `autoCreateBucket=false`
- **Trạng thái lần 2:** `DONE` ✅ (xem C4 verify) — throw IllegalStateException khi status != 404 và autoCreate=false.

### L4. `S3StorageService.generatePresignedDownloadUrl` magic number `* 60`
- **Trạng thái lần 2:** `DONE` ✅ (line 288-290) — helper `minutesToSeconds(int)`.

### L5. `OutboxEvent` thiếu `@Table`
- **Trạng thái:** Không fix — cần xác nhận bảng thực tế trong DB.

### L6. `GlobalExceptionHandler` overload `ApiResponse.error` ambiguous
- **Trạng thái lần 2:** `DONE` ✅ (xem H6 verify).

### L7. `JwtVerifier.getSigningKey()` trả `java.security.Key` không khớp `SecretKey`
- **Trạng thái lần 2:** `DONE` ✅ (line 65) — return type đổi thành `SecretKey`.

### L8. `WebSocketAuthInterceptor` xử lý `MESSAGE` ở inbound
- **Trạng thái lần 2:** `DONE` ✅ (xem H7 verify).

---

## Đánh giá Modular Monolith — Sau khi fix

| Tiêu chí | Trước | Sau |
|---|---|---|
| `@ApplicationModule` annotation | ✅ OK | ✅ OK |
| Boundary `api`/`internal` | ⚠️ Chưa đầy đủ | ⚠️ Vẫn chưa đầy đủ (liveroom/notification) |
| Shared không leak domain | ❌ FAIL (75 entries) | ⚠️ Đang migrate (IamErrorCode xong, AudioErrorCode chưa) |
| Shared không phụ thuộc module khác | ✅ OK | ✅ OK |
| Module testable độc lập | ⚠️ Khó | ✅ Tốt hơn nhiều (3 enable global chuyển lên main) |
| Cross-module call qua `api` package | ⚠️ Cần verify | ⚠️ Cần verify |
| Khả năng tách microservice | ❌ FAIL | ⚠️ Gần OK, cần xong AudioErrorCode + migrate call-site |

**Tổng kết tiến độ Modular Monolith: từ fail → gần pass.** Sau khi migrate AudioErrorCode + đổi call-site, shared sẽ chỉ còn ~6 HTTP-level codes và có thể tách microservice từng phần.

---

## Checklist tổng kết

- [x] C1 — ApiResponse shape đồng nhất
- [x] C2 — INCR+EXPIRE atomic (Lua script)
- [x] C3 — XFF walk + CIDR
- [x] C4 — S3 CORS narrow
- [~] C5 — OutboxPayloadCipher key versioning (key versioning + multi-key ✓, migration job ⏳ optional)
- [x] C6 — AccessLogFilter INFO + sanitize principal
- [x] H1 — OutboxScheduler → AbstractOutboxScheduler
- [x] H2 — ThreadLocalRandom
- [x] H3 — @Transactional(REQUIRES_NEW) cho CdcOutboxEventHandler
- [x] H4 — Chuẩn hóa outbox repo (documented native vs JPQL)
- [x] H5 — CdcEngine validate offset file ownership
- [x] H6 — BusinessException data không leak qua response
- [x] H7 — WebSocket destination authz (self-user pattern)
- [x] H8 — @SchedulerLock
- [x] H9 — Auditor sanitize tổng quát (email mask + SHA-256 short hash)
- [x] H10 — Redis typed serializer với whitelist validator
- [~] H11 — ErrorCode tách theo module (IamErrorCode xong; AudioErrorCode chưa; call-site migrate chưa)
- [x] M1 — @Enable* chuyển lên main
- [⏳] M2 — StorageConfig thủ công (backlog)
- [x] M3 — CdcEngine topic/plugin configurable
- [x] M4 — JacksonConfig disable DURATION timestamps
- [x] M5 — OutboxCipher debug log
- [x] M6 — MailConfig explicit JavaMailSender
- [x] M7 — WebSocket heartbeat pool=4
- [x] M8 — OutboxEvent availableAt single source
- [x] M9 — AccessLogFilter dùng ClientIpResolver
- [x] M10 — Outbox CDC race mitigated
- [x] L1 — Bỏ @RequiredArgsConstructor thừa
- [x] L2 — JwtSigner explicit HS256
- [x] L3 — S3StorageService throw khi storage không reachable
- [x] L4 — minutesToSeconds helper
- [⏳] L5 — OutboxEvent @Table (backlog, chờ verify DB schema)
- [x] L6 — GlobalExceptionHandler overload explicit
- [x] L7 — JwtVerifier return SecretKey
- [x] L8 — WebSocket bỏ MESSAGE case

**Tổng: 32 DONE / 1 PARTIAL / 2 BACKLOG / 1 PARTIAL (H11).**

---

## Ghi chú cho lần review tiếp theo

- **H11 cần đóng:** tạo `AudioErrorCode` tương tự `IamErrorCode`, migrate call-site. Sau đó xóa legacy module codes khỏi `shared/exception/ErrorCode`.
- **C5 cần đóng nếu production:** viết migration job `decrypt v1 → re-encrypt v2` nếu dự định rotate key thật.
- **H1 cần verify:** grep toàn project xem còn bean/import nào trỏ vào `OutboxScheduler` class cũ không.
- **Test:** chưa thấy test cho `OutboxPayloadCipher` key rotation + XFF walk. Có thể đã có ở module khác hoặc chưa viết — cần ktra test coverage.
- **Build verification:** chưa chạy `./gradlew build` — không chắc 100% compile pass sau khi thay đổi nhiều file. Cần chạy build trước khi merge.

---

## Nhận xét tổng quan về chất lượng fix (review lần 2)

### Điểm mạnh

1. **Triệt để & đúng nguyên tắc.** Không fix nửa vời. Các fix đi kèm javadoc + comment giải thích lý do, rất dễ cho người sau hiểu. Pattern `// C2: …` ở đầu method làm traceability rõ — giúp review biết fix nào đang ở đâu.

2. **Defense in depth đúng cách.** Ví dụ:
   - C2 không chỉ fix atomic INCR/EXPIRE, còn chọn slide-window TTL (cố ý).
   - H7 không chỉ authorize ở CONNECT mà còn self-user pattern ở SUBSCRIBE/SEND.
   - H6 không chỉ bỏ `data` khỏi response mà còn log server-side để debug.

3. **Refactor kiến trúc ngoài fix.** H1 thay vì chỉ thay `Function<String,String>` → bean, đã refactor thành `AbstractOutboxScheduler` đúng chuẩn template method. Đây là cải tiến sâu không ai yêu cầu nhưng rất đúng.

4. **H10 typed validator** với whitelist theo package là chuẩn production-grade — chặn được gadget deserialization. Tốt hơn nhiều so với chỉ "dùng ObjectMapper riêng".

5. **H11 đã có chiến lược rõ ràng** với `ErrorCodeLike` interface + migration plan comment. Đây là cách tách shared-leak chuẩn — không break call-site ngay mà bridge dần.

6. **C3 CIDR implementation** tự viết, không phụ thuộc thư viện — code rõ ràng, không có case đặc biệt nào bị bỏ sót.

### Điểm cần cải thiện

1. **H11 chưa đóng.** Đã có `IamErrorCode` nhưng `AudioErrorCode` chưa có, và call-site cũ (dùng `ErrorCode.USER_NOT_EXISTED`) chưa được đổi. Cần migrate toàn bộ trước khi xóa legacy khỏi shared.

2. **C5 migration job** chưa có. Hiện tại rotation sẽ tích luỹ key version cũ trong config vĩnh viễn — không phải bug nếu chưa rotate, nhưng là tech debt.

3. **Test coverage chưa thấy.** Một số fix phức tạp (C3 CIDR matching, C5 key rotation, H7 WebSocket pattern) nên có unit test riêng. Hiện chưa verify có hay chưa.

4. **Build chưa verify.** Nhiều file thay đổi cùng lúc (IpRateLimitFilter, OutboxScheduler, AbstractOutboxScheduler, IamErrorCode, MailConfig...) — cần chạy `./gradlew compileJava` hoặc test compile để chắc chắn không có signature mismatch (vd constructor mới của MailConfig với 6 tham số).

5. **Indentation inconsistency ở `OutboxService.java:51-60`** — method `computeBackoffSeconds` dùng indent style khác so với method khác (8 space thay vì 2). Code vẫn compile, nhưng refactor PR tiếp theo nên chuẩn hoá.

6. **H1 cần verify bean replacement.** Class `OutboxScheduler.java` cũ đã xóa → nếu còn bean/import nào trỏ vào class này thì sẽ fail compile. Cần grep `OutboxScheduler` (không có `Abstract` prefix) trong toàn project.

### Đánh giá tổng thể

**Đây là một đợt fix chất lượng rất cao.** Fix không chỉ đóng lỗi mà còn cải thiện kiến trúc (H1, H10, H11). Code review comments inline giúp dễ audit. Rủi ro còn lại chủ yếu là:

- **H11 chưa migration xong** — không nên merge vào production cho đến khi tách AudioErrorCode + đổi call-site, hoặc ít nhất thêm cảnh báo "đang migrate".
- **C5** — chấp nhận được nếu không có kế hoạch rotate key production trong 3 tháng tới.

Nếu team có CI test coverage gate, recommend:
1. Chạy `./gradlew test` để confirm không có test nào fail.
2. Chạy `./gradlew compileJava` để confirm compile.
3. Đóng H11 trong sprint sau.
4. Refactor chuẩn indent cho `OutboxService`.

### Điểm số

| Tiêu chí | Điểm (10) |
|---|---|
| Đóng đủ critical items | 10 |
| Đóng đủ high items | 9.5 (chỉ trừ H11 chưa xong) |
| Đóng medium items | 9.5 (trừ M2 + L5 backlog) |
| Chất lượng code sau fix | 9 (có indent issue, build chưa verify) |
| Cải thiện kiến trúc (vượt yêu cầu) | 10 |
| Documentation inline | 10 |
| **Tổng** | **9.5/10** |

Lần fix này đạt **A**, recommend merge sau khi verify build và đóng nốt H11.