# Shared Module — Code Review & Fix Tracker

> Phạm vi: `backend/src/main/java/com/pwb/backend/shared/`
> Ngày review: 2026-07-11
> Ngày cập nhật: 2026-07-11
> Trạng thái: **ĐÃ FIX** cho hầu hết item — H11 được tách cấu trúc (module-local enums tồn tại) nhưng chưa migrate toàn bộ call-site (theo dõi trong checklist dưới).

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

## 🔴 CRITICAL — Fix trong sprint hiện tại

### C1. `IpRateLimitFilter` bypass `ApiResponse` contract

- **File:** `backend/src/main/java/com/pwb/backend/shared/security/IpRateLimitFilter.java:73-75`
- **Vấn đề:** Khi vượt rate-limit, filter ghi raw JSON `{"code":"...","message":"..."}` thay vì dùng `ApiResponse` shape chuẩn mà `GlobalExceptionHandler` áp dụng cho mọi response lỗi khác.
- **Tác động:** Client phải parse 2 format error khác nhau. Vi phạm API contract thống nhất. Khó debug/monitor.
- **Đề xuất fix:** Inject `ObjectMapper`, build `ApiResponse.error(...)` giống `RestAccessDeniedHandler` rồi `objectMapper.writeValue(response.getWriter(), body)`. Trả về status 429 với `Retry-After` header như cũ.
- **Trạng thái:** `DONE`

### C2. `IpRateLimitFilter` — race condition `INCR` + `EXPIRE`

- **File:** `backend/src/main/java/com/pwb/backend/shared/security/IpRateLimitFilter.java:62-65`
- **Vấn đề:** `INCR` và `EXPIRE` là 2 lệnh riêng biệt, không atomic. Nếu crash/network blip giữa 2 lệnh, key không có TTL → IP bị khóa vĩnh viễn cho đến khi Redis evict.
- **Tác động:** Một số IP sẽ bị rate-limit mãi mãi sau incident nhỏ.
- **Đề xuất fix:** Dùng Lua script atomic (`INCR` + `EXPIRE` trong 1 script) hoặc pipeline với `SET key 1 EX <ttl> NX` rồi `INCR`. Tận dụng pattern đã có ở `RedisLuaConfig`.
- **Trạng thái:** `DONE`

### C3. `ClientIpResolver` tin `X-Forwarded-For` segment đầu

- **File:** `backend/src/main/java/com/pwb/backend/shared/security/ClientIpResolver.java:38-50`
- **Vấn đề:** Lấy segment đầu tiên của `X-Forwarded-For` khi `remoteAddr` thuộc trusted list. Header XFF là user-controlled. Ngoài ra `isTrustedProxy` chỉ check exact match + loopback, không hỗ trợ CIDR (vd `10.0.0.0/8` không trust được).
- **Tác động:** Nếu proxy/CDN đứng trước app và attacker craft XFF, có thể spoof IP → bypass IP rate-limit, bypass audit log, bypass IP-based session binding. Trong test/staging có thể set trusted rộng → càng nguy hiểm.
- **Đề xuất fix:**
  1. Walk XFF từ phải qua trái, bỏ qua IP thuộc trusted chain, lấy IP đầu tiên KHÔNG thuộc trusted.
  2. Hỗ trợ CIDR ở `parseTrustedProxies` (vd `10.0.0.0/8`, `172.16.0.0/12`).
  3. Document rõ cấu hình trusted proxy chain cho từng môi trường.
- **Trạng thái:** `DONE`

### C4. `S3StorageService.configureBucketCors` — wildcard origin + wildcard headers

- **File:** `backend/src/main/java/com/pwb/backend/shared/service/impl/S3StorageService.java:82-87`
- **Vấn đề:**
  ```java
  .allowedOrigins(List.of("*"))
  .allowedHeaders(List.of("*"))
  ```
- **Tác động:** Bất kỳ origin nào cũng gọi trực tiếp đến S3/MinIO endpoint với mọi header. Kết hợp với presigned upload URL, nếu key đoán được → ghi đè object. Trái ngược với `WebSocketConfig` từ chối wildcard.
- **Đề xuất fix:**
  1. Inject `allowedOrigins` từ `app.security.cors.allowed-origins` (cùng nguồn với web CORS).
  2. Narrow `allowedHeaders` xuống danh sách cụ thể (`Authorization`, `Content-Type`, `x-amz-*`).
  3. Hoặc tắt `autoConfigureCors` mặc định (đã `false`), chỉ bật khi cần và review thủ công.
- **Trạng thái:** `DONE`

### C5. `OutboxPayloadCipher` rotation không thể giải mã payload cũ

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/cipher/OutboxPayloadCipher.java:42-46, 81-96`
- **Vấn đề:** Chỉ giữ 1 key duy nhất. `keyVersion` không được mix vào key derivation. Khi tăng `keyVersion` để rotate, payload cũ mã hóa bằng key cũ sẽ không giải mã được bằng key mới.
- **Tác động:** Rotate key = mất toàn bộ payload chưa xử lý trong outbox. Đây là correctness bug nghiêm trọng nếu rotation kích hoạt production.
- **Đề xuất fix:**
  1. Lưu key theo `keyVersion` (Map<Integer, SecretKey>) trong `OutboxPayloadCipher`.
  2. Prefix payload với `keyVersion` (vd `enc:v2:base64...`) để biết dùng key nào khi decrypt.
  3. Encrypt luôn dùng key mới nhất.
  4. Migration: chạy job decrypt → re-encrypt bằng key mới, xong mới drop key cũ.
- **Trạng thái:** `DONE`

### C6. `AccessLogFilter` log DEBUG + lộ `principal.toString()`

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/AccessLogFilter.java:45-51, 55-61`
- **Vấn đề:**
  1. Log mức `DEBUG` → production (mặc định `INFO`) sẽ im lặng hoàn toàn, mất access log.
  2. `extractUserId()` trả `auth.getPrincipal().toString()` cho mọi authenticated request. Nếu principal là entity User (có thể chứa email, hash, etc.) → lộ data qua log.
- **Tác động:** Production mất observability. Log có thể chứa PII.
- **Đề xuất fix:**
  1. Đổi sang `INFO` hoặc tách thành 2 mức (request received vs response sent).
  2. Chỉ log identifier an toàn (vd `auth.getName()` nếu đã sanitize, hoặc inject `JwtPrincipalExtractor` để dùng logic chung).
  3. Cân nhắc bỏ `AccessLogFilter` và dùng Micrometer/AccessLog filter có sẵn của Spring.
- **Trạng thái:** `DONE`

---

## 🟠 HIGH — Tính đúng đắn & Modular Monolith

### H1. `OutboxScheduler` dùng `Function<String,String>` injection thủ công

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/scheduler/OutboxScheduler.java:20, 29-31`
- **Vấn đề:** `payloadDecryptor` là `Function<String, String>` inject qua field (không phải constructor). `delegate` qua setter, có thể `null`. Phá vỡ Spring lifecycle.
- **Tác động:** Nếu `payloadDecryptor` không được wire → NPE trong scheduled task. Khó test, khó trace dependency.
- **Đề xuất fix:**
  1. Inject `OutboxPayloadCipher` trực tiếp qua constructor, gọi `cipher.decrypt(...)`.
  2. `delegate` không nên có ở `shared` — mỗi module (iam, audio) tự có scheduler riêng, kế thừa base class trừu tượng `AbstractOutboxScheduler` ở `shared`.
- **Trạng thái:** `DONE`

### H2. `OutboxService.computeBackoffSeconds` dùng `Math.random()`

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/service/OutboxService.java:55`
- **Vấn đề:** `Math.random()` contention cao, synchronized internally, không thread-safe hiệu quả.
- **Tác động:** Không phải security issue (chỉ jitter) nhưng ảnh hưởng performance khi retry rate cao.
- **Đề xuất fix:** `ThreadLocalRandom.current().nextDouble(...)`.
- **Trạng thái:** `DONE`

### H3. `CdcOutboxEventHandler` thiếu `@Transactional` và row-level lock

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/processor/CdcOutboxEventHandler.java:33-69`
- **Vấn đề:** Method chạy trên Debezium thread, gọi `repository.findById()` + `processor.processOutboxEvent(...)` mà không có `@Transactional`. Khi multi-instance scale → cùng đọc event → 2 lần xử lý.
- **Tác động:** Duplicate side-effects (gửi email, notify, v.v.) nếu HPA scale-up.
- **Đề xuất fix:** Thêm `@Transactional` cho `handleEvent`. Hoặc dùng `findPendingEventsForUpdate` (đã có `FOR UPDATE SKIP LOCKED`).
- **Trạng thái:** `DONE`

### H4. `OutboxEventRepository` có 2 API không nhất quán

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/repository/OutboxEventRepository.java:16-30`
- **Vấn đề:** `findPendingEventsForUpdate` (native + `FOR UPDATE SKIP LOCKED` + `NOW()`) vs `findReadyForProcessing` (JPQL + `Instant.now()`). Không có javadoc nói rõ khi nào dùng cái nào. Native query dùng `SELECT *` rất rộng.
- **Tác động:** Hai cách poll khác nhau → bug khi chuyển cách dùng. Native query trả full row bao gồm cột lớn (vd `payload TEXT`) → tốn bandwidth.
- **Đề xuất fix:**
  1. Document rõ: native query cho production multi-instance, JPQL cho single-instance/test.
  2. Native query chỉ select column cần thiết (id, aggregate_type, payload, status...).
  3. Chuẩn hóa 1 API duy nhất.
- **Trạng thái:** `DONE`

### H5. `CdcEngine` không validate `connectorName`/`offsetPath` uniqueness

- **File:** `backend/src/main/java/com/pwb/backend/shared/cdc/CdcEngine.java:36-45, 47-86`
- **Vấn đề:** Mỗi module con (`IamCdcConfig`, `AudioCdcConfig`) tạo một `CdcEngine` bean. Không có cơ chế đảm bảo `connectorName` + `offsetStoragePath` unique giữa các connector.
- **Tác động:** Hai connector đụng offset file → mất progress, duplicate event.
- **Tác động kỹ thuật:** Hard-code `topic.prefix = "pwb-cdc"` + `pgoutput` plugin → không linh hoạt cho RDS/wal2json.
- **Đề xuất fix:**
  1. Validate ở startup: nếu offset file đã tồn tại và `connectorName` khác → fail fast.
  2. Đẩy `topic.prefix`, `plugin.name`, `offset.flush.interval.ms` ra `app.cdc.*` properties.
  3. Xác nhận `IamCdcConfig`/`AudioCdcConfig` không trỏ cùng `tableIncludeList`.
- **Trạng thái:** `DONE`

### H6. `GlobalExceptionHandler` có thể leak `data` qua BusinessException

- **File:** `backend/src/main/java/com/pwb/backend/shared/exception/GlobalExceptionHandler.java:45`
- **Vấn đề:** `ApiResponse.error(translatedMessage, ex.getData(), ...)` trả về `data` field từ `BusinessException`. Nếu module nào throw BusinessException với `data` chứa internal ID/PII/stack → lộ qua response body.
- **Tác động:** Information disclosure qua API lỗi.
- **Đề xuất fix:**
  1. Loại bỏ `data` field khỏi response error public. Chỉ giữ ở log server-side.
  2. Nếu cần trả data cho client, contract rõ ràng qua DTO whitelist.
- **Trạng thái:** `DONE`

### H7. `WebSocketAuthInterceptor` không authorize theo destination

- **File:** `backend/src/main/java/com/pwb/backend/shared/websocket/WebSocketAuthInterceptor.java:37-42`
- **Vấn đề:** Chỉ check authenticated, không check `simpDestination`. Bất kỳ user nào authenticated đều SUBSCRIBE/SEND mọi topic/destination. Case `MESSAGE` không cần check ở inbound channel.
- **Tác động:** Nếu controller không authorize lại ở tầng cao hơn, user có thể subscribe topic của user khác. Hiện tại có thể an toàn nếu controller check, nhưng thiếu defense-in-depth.
- **Đề xuất fix:**
  1. Bỏ `case MESSAGE` (Spring client inbound không nhận MESSAGE từ client).
  2. Thêm `simpDestPattern` check: `SUBSCRIBE` chỉ cho pattern `/topic/user/{email}/...` matching với email principal; `SEND` chỉ cho `/app/chat/{roomId}` nếu user là participant.
  3. Hoặc chuyển logic này ra `AuthorizationManager` cho STOMP.
- **Trạng thái:** `DONE`

### H8. Scheduler thiếu `@SchedulerLock` cho multi-instance safety

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/scheduler/OutboxScheduler.java:33`
- **Vấn đề:** `RedisConfig.java:15` đã enable `@EnableSchedulerLock` + có `LockProvider` bean, nhưng scheduler không dùng. `findReadyForProcessing` (JPQL) không có `FOR UPDATE SKIP LOCKED`. Khi deploy 2+ instance → race condition.
- **Tác động:** Cùng 1 event được process bởi nhiều instance → duplicate side-effects.
- **Đề xuất fix:** Thêm `@SchedulerLock(name = "outboxScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")` cho method `pollPendingOutboxEvents`. Hoặc bắt buộc dùng `findPendingEventsForUpdate` (native, có row lock).
- **Trạng thái:** `DONE`

### H9. `JpaAuditingConfig` auditor có thể lộ PII khi principal không có `@`

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/JpaAuditingConfig.java:32-36`
- **Vấn đề:** Nếu `name` không có `@` (vd admin principal `phone:0901234567`, OAuth subject), trả nguyên xi → lộ vào `created_by`/`updated_by` column.
- **Tác động:** PII trong audit columns (vd `phone:0901234567`).
- **Đề xuất fix:**
  1. Sanitize tổng quát: nếu không match pattern email, hash hoặc mask.
  2. Tốt hơn: dùng UUID lookup table, chỉ lưu UUID vào audit column.
- **Trạng thái:** `DONE`

### H10. `RedisConfig.redisTemplate` dùng polymorphic typing có thể fail

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/RedisConfig.java:19-36`
- **Vấn đề:** `GenericJackson2JsonRedisSerializer(objectMapper)` ép bật `@class` polymorphic typing nội bộ, mặc dù `JacksonConfig.objectMapper` không bật `activateDefaultTyping`. Mọi value Redis sẽ có field `@class` không mong muốn. Khi deserialize generic `Map`/`List`, có thể fail ở classloader khác (test/slice).
- **Tác động:** Cache cross-module có thể fail serialize/deserialize. Debug khó.
- **Đề xuất fix:**
  1. Dùng typed serializer cho từng cache (`Jackson2JsonRedisSerializer<MyDto>`).
  2. Hoặc tạo `ObjectMapper` riêng cho Redis với `activateDefaultTyping` explicit.
- **Trạng thái:** `DONE`

### H11. `ErrorCode` chứa ~75 entry là **shared-leak** vi phạm modular monolith

- **File:** `backend/src/main/java/com/pwb/backend/shared/exception/ErrorCode.java:1-91`
- **Vấn đề:** Toàn bộ error codes từ iam, audio, liveroom, notification đều nằm trong `shared/exception/ErrorCode.java`. Shared module biết về mọi module khác.
- **Tác động:**
  - Khi tách microservice, không thể tách iam khỏi audio vì ErrorCode của audio nằm trong iam's shared dependency.
  - Thay đổi 1 code ở audio phải rebuild cả shared.
  - Code conflict risk khi nhiều người cùng edit.
- **Đề xuất fix:**
  1. Mỗi module tự khai báo enum `ErrorCode` riêng trong `internal/exception/`.
  2. `shared` chỉ giữ codes HTTP-level chung (`INTERNAL_SERVER_ERROR`, `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`).
  3. `GlobalExceptionHandler` vẫn ở `shared` (HTTP-level), module-specific mapping được enrich ở module-level handler.
  4. Khi tách, chỉ giữ 5 code chung ở shared.
- **Trạng thái:** `DOING (partial)` — `IamErrorCode`/`AudioErrorCode` đã tạo, `ErrorCodeLike` interface bridge `BusinessException` ↔ shared handler đã có. Còn migrate từng call-site trong từng module sang enum mới.

---

## 🟡 MEDIUM — Thiết kế & tiện ích

### M1. Quá nhiều `@Enable*` global ở `shared`

- **File:** `JpaAuditingConfig.java:13`, `RedisConfig.java:15`, `WebSocketConfig.java:16`
- **Vấn đề:** `@EnableJpaAuditing`, `@EnableSchedulerLock`, `@EnableWebSocketMessageBroker` đặt trong shared. Khi test slice hoặc tách module → khó tắt từng cái.
- **Tác động:** Khó viết integration test cho từng module độc lập.
- **Đề xuất fix:** Đẩy 3 enable này lên main class hoặc tách thành config classes riêng có `@ConditionalOn...`.
- **Trạng thái:** `DONE`

### M2. `StorageConfig` tạo `S3Client`/`S3Presigner` thủ công

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/StorageConfig.java:22-56`
- **Vấn đề:** Code thủ công, không qua Spring Boot autoconfig. Tăng surface area, dễ lỗi khi upgrade AWS SDK.
- **Tác động:** Maintenance cost cao.
- **Đề xuất fix:** Nếu dùng MinIO thì thủ công OK. Nếu AWS thật, dùng `io.awspring.cloud:spring-cloud-aws-starter-s3` hoặc tự định nghĩa rõ version AWS SDK.
- **Trạng thái:** `DONE`

### M3. `CdcEngine` hard-code `topic.prefix` + `pgoutput`

- **File:** `backend/src/main/java/com/pwb/backend/shared/cdc/CdcEngine.java:80-82`
- **Vấn đề:** Hard-code `"pwb-cdc"` + `"pgoutput"`.
- **Tác động:** Không dùng được cho RDS yêu cầu `wal2json`, không tái sử dụng cho môi trường khác.
- **Đề xuất fix:** Inject từ `app.cdc.topic-prefix`, `app.cdc.plugin-name`.
- **Trạng thái:** `DONE`

### M4. `JacksonConfig` chưa disable `WRITE_DURATIONS_AS_TIMESTAMPS`

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/JacksonConfig.java:18-21`
- **Vấn đề:** `WRITE_DATES_AS_TIMESTAMPS` đã disable nhưng `WRITE_DURATIONS_AS_TIMESTAMPS` chưa.
- **Tác động:** `Duration` serialize thành số (vd `60.0`) thay vì ISO-8601.
- **Đề xuất fix:** Thêm `mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)`.
- **Trạng thái:** `DONE`

### M5. `OutboxPayloadCipher.decrypt` không phân biệt được ciphertext cũ không prefix

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/cipher/OutboxPayloadCipher.java:83`
- **Vấn đề:** `if (!enabled || !stored.startsWith("enc:")) return stored;` — nếu trước đây `enabled=false` lưu plaintext, giờ bật `enabled=true`, decrypt vẫn trả plaintext. Logic đúng nhưng dễ nhầm khi debug.
- **Tác động:** Không rõ ràng trong log/monitoring, có thể làm sai lệch thống kê "payload đã mã hóa".
- **Đề xuất fix:** Log warning khi gặp payload không có prefix để dễ audit.
- **Trạng thái:** `DONE`

### M6. `MailConfig` không khởi tạo `JavaMailSender`

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/MailConfig.java`
- **Vấn đề:** Class chỉ validate credentials ở `@PostConstruct`. Không định nghĩa bean `JavaMailSender`.
- **Tác động:** Phụ thuộc hoàn toàn vào Spring Boot autoconfig. Nếu `spring.mail.host` không set → NPE ở runtime khi gửi mail.
- **Đề xuất fix:** Explicit bean `JavaMailSender` hoặc `@ConditionalOnProperty` rõ ràng.
- **Trạng thái:** `DONE`

### M7. `WebSocketConfig` heartbeat scheduler chỉ pool size 1

- **File:** `backend/src/main/java/com/pwb/backend/shared/websocket/WebSocketConfig.java:51-55`
- **Vấn đề:** 1 thread cho heartbeat của tất cả STOMP session.
- **Tác động:** Nghẽn khi nhiều session.
- **Đề xuất fix:** Pool size 2-4.
- **Trạng thái:** `DONE`

### M8. `OutboxEvent` set `availableAt` 2 chỗ

- **File:** `backend/src/main/java/com/pwb/backend/shared/outbox/model/OutboxEvent.java:48, 53-60`
- **Vấn đề:** Field initializer `private Instant availableAt = Instant.now();` + `@PrePersist` lại set lại.
- **Tác động:** Redundancy, dễ bug khi edit.
- **Đề xuất fix:** Chỉ giữ 1 trong 2. Tốt nhất giữ `@PrePersist` để dùng `super.prePersist()`.
- **Trạng thái:** `DONE`

### M9. `AccessLogFilter` duplicate logic với `ClientIpResolver`

- **File:** `backend/src/main/java/com/pwb/backend/shared/config/AccessLogFilter.java:63-69` vs `ClientIpResolver.java:38-50`
- **Vấn đề:** `extractClientIp` ở AccessLogFilter tự parse XFF, không dùng `ClientIpResolver` đã có.
- **Tác động:** Logic IP resolution rẽ nhánh → inconsistent giữa access log, rate-limit, audit.
- **Đề xuất fix:** Inject `ClientIpResolver` vào `AccessLogFilter`, gọi `clientIpResolver.resolve(request)`.
- **Trạng thái:** `DONE`

### M10. `CdcOutboxEventHandler` + `OutboxEventRepository` race trên multi-CDC consumer

- **File:** `CdcOutboxEventHandler.java:55-65`, `OutboxEventRepository.java:16-30`
- **Vấn đề:** Tương tự H8 — nếu 2 CDC consumer cùng đọc 1 event → 2 lần xử lý. `findPendingEventsForUpdate` có lock nhưng `findReadyForProcessing` không.
- **Tác động:** Duplicate side-effects.
- **Đề xuất fix:** Chuẩn hóa 1 query, ép dùng native có lock.
- **Trạng thái:** `DONE`

---

## 🟢 LOW — Style & micro-cleanup

### L1. `ErrorCode` dùng `@RequiredArgsConstructor` không cần thiết
- **File:** `ErrorCode.java:8`
- **Vấn đề:** Enum constructor auto-generated, không cần `@RequiredArgsConstructor` (Lombok tạo constructor private thừa).
- **Đề xuất fix:** Bỏ `@RequiredArgsConstructor`.

### L2. `JwtSigner.sign` không chỉ định algorithm
- **File:** `JwtSigner.java:37`
- **Vấn đề:** `signWith(key)` không explicit algorithm.
- **Đề xuất fix:** `signWith(key, Jwts.SIG.HS256)` để audit rõ ràng (JJWT 0.12.x API).

### L3. `S3StorageService.init()` swallow exception ngay cả khi `autoCreateBucket=false`
- **File:** `S3StorageService.java:62-77`
- **Vấn đề:** Nếu `headBucket` fail vì IAM credentials sai và `autoCreateBucket=false`, app vẫn start, fail ở runtime.
- **Đề xuất fix:** Throw nếu không phải 404 và `autoCreateBucket=false`.

### L4. `S3StorageService.generatePresignedDownloadUrl` magic number `* 60`
- **File:** `S3StorageService.java:176`
- **Vấn đề:** Method overload gọi `expirationMinutes * 60` không rõ ràng.
- **Đề xuất fix:** Đổi tên param rõ `expirationSeconds` ở overload, hoặc viết hẳn helper `private long toSeconds(int minutes)`.

### L5. `OutboxEvent` thiếu `@Table`
- **File:** `OutboxEvent.java:17-19`
- **Vấn đề:** Entity dùng tên class làm table name mặc định. Native query dùng `outbox_events`. Cần xác nhận bảng thực tế trong DB.
- **Đề xuất fix:** Thêm `@Table(name = "outbox_events")` cho rõ ràng. Verify với migration scripts.

### L6. `GlobalExceptionHandler` overload `ApiResponse.error` ambiguous
- **File:** `GlobalExceptionHandler.java:45`
- **Vấn đề:** `ApiResponse.error(translatedMessage, ex.getData(), List.of(detail))` — compiler có thể ambiguous giữa overload 3-arg và 4-arg.
- **Đề xuất fix:** Explicit cast hoặc đổi tên method factory.

### L7. `JwtVerifier.getSigningKey()` trả `java.security.Key` không khớp `SecretKey`
- **File:** `JwtVerifier.java:62-64`
- **Vấn đề:** Return type là `java.security.Key`, các nơi khác dùng `SecretKey`.
- **Đề xuất fix:** Đổi return type về `SecretKey` cho nhất quán.

### L8. `WebSocketAuthInterceptor` xử lý `MESSAGE` ở inbound
- **File:** `WebSocketAuthInterceptor.java:39`
- **Vấn đề:** Spring client inbound channel không nhận MESSAGE từ client. Case `MESSAGE` thừa.
- **Đề xuất fix:** Bỏ khỏi switch.

---

## Đánh giá Modular Monolith — Tổng quan

| Tiêu chí | Đánh giá | Ghi chú |
|---|---|---|
| `@ApplicationModule` annotation | ✅ OK | Shared = `OPEN`, iam/audio có `NamedInterface` |
| Boundary `api`/`internal` | ⚠️ Chưa đầy đủ | Chỉ iam + audio; liveroom/notification chưa rõ |
| Shared không leak domain | ❌ FAIL | `ErrorCode` chứa ~75 entry từ mọi module (xem H11) |
| Shared không phụ thuộc module khác | ✅ OK | Chỉ depend Spring/JJWT/AWS/Kafka |
| Module testable độc lập | ⚠️ Khó | Quá nhiều `@Enable*` global ở shared (xem M1) |
| Cross-module call qua `api` package | ⚠️ Cần verify | Một số chỗ có thể bypass |
| Khả năng tách microservice | ❌ FAIL | Vì H11, không thể tách iam/audio mà không sửa shared |

---

## Thứ tự ưu tiên khi fix

1. **🔴 Sprint này:** C1 → C2 → C4 → C5 → C6 → C3
2. **🟠 Sprint tiếp theo:** H11 (ErrorCode refactor) → H1 (OutboxScheduler) → H8 (@SchedulerLock) → H7 (WebSocket authz) → H3/H10
3. **🟡 Backlog:** M1 → M9 → M3 → M4 → M7 → M2/M5/M6/M8/M10
4. **🟢 Cleanup:** L1 → L8 cùng đợt refactor

---

## Checklist khi fix xong

- [x] C1 — ApiResponse shape đồng nhất
- [x] C2 — INCR+EXPIRE atomic
- [x] C3 — XFF walk + CIDR
- [x] C4 — S3 CORS narrow
- [x] C5 — OutboxPayloadCipher key versioning
- [x] C6 — AccessLogFilter INFO + sanitize principal
- [x] H1 — OutboxScheduler refactor (xóa delegate hack, thêm AbstractOutboxScheduler)
- [x] H2 — ThreadLocalRandom
- [x] H3 — @Transactional cho CdcOutboxEventHandler
- [x] H4 — Chuẩn hóa 1 query outbox (narrow column, Javadoc rõ)
- [x] H5 — CdcEngine validate uniqueness + config từ app.cdc.*
- [x] H6 — BusinessException data không leak (đã bỏ khỏi response, log server-side)
- [x] H7 — WebSocket destination authz
- [x] H8 — @SchedulerLock (IamOutboxScheduler)
- [x] H9 — Auditor sanitize tổng quát (SHA-256 fingerprint cho non-email)
- [x] H10 — Redis typed serializer (ObjectMapper riêng, whitelist package)
- [ ] H11 — ErrorCode tách theo module (enums đã tạo, cần migrate call-site từng module)
- [x] M1-M10 — Design cleanup
- [x] L1-L8 — Style cleanup

## Tóm tắt file thay đổi

| File | Thay đổi |
|---|---|
| `shared/security/IpRateLimitFilter.java` | C1+C2: ApiResponse + Lua atomic |
| `shared/security/ClientIpResolver.java` | C3: XFF walk + CIDR |
| `shared/service/impl/S3StorageService.java` | C4+L3+L4: narrow CORS, fail-fast init, helper `minutesToSeconds` |
| `shared/config/StorageProperties.java` | C4: thêm `corsAllowedOrigins`, `corsAllowedHeaders` |
| `shared/outbox/cipher/OutboxPayloadCipher.java` | C5+M5: versioned payload, legacy keys, log warning |
| `shared/outbox/config/OutboxProperties.java` | C5: `legacyKeys` |
| `shared/config/AccessLogFilter.java` | C6+M9: INFO log, ClientIpResolver, mask email |
| `shared/outbox/scheduler/OutboxScheduler.java` | H1: xóa (delegate hack) |
| `shared/outbox/scheduler/AbstractOutboxScheduler.java` | H1: base class mới |
| `iam/internal/job/IamOutboxScheduler.java` | H8: @SchedulerLock + @Transactional |
| `shared/outbox/service/OutboxService.java` | H2: ThreadLocalRandom |
| `shared/outbox/processor/CdcOutboxEventHandler.java` | H3: @Transactional(REQUIRES_NEW) |
| `shared/outbox/repository/OutboxEventRepository.java` | H4: narrow native query + Javadoc |
| `shared/cdc/CdcEngine.java` | H5+M3: ownership check + configurable topic.prefix / plugin.name / flush |
| `shared/exception/BusinessException.java` | H6: ErrorCodeLike + cảnh báo `data` không còn serialise |
| `shared/exception/ErrorCode.java` | H11: implements ErrorCodeLike, Javadoc hướng dẫn tách |
| `shared/exception/ErrorCodeLike.java` | H11: marker mới |
| `iam/internal/exception/IamErrorCode.java` | H11: enum mới |
| `audio/internal/exception/AudioErrorCode.java` | H11: enum mới |
| `shared/exception/GlobalExceptionHandler.java` | H6+L6: bỏ data khỏi response |
| `shared/websocket/WebSocketAuthInterceptor.java` | H7+L8: destination authz, bỏ MESSAGE case |
| `shared/config/JpaAuditingConfig.java` | H9+M1: bỏ @EnableJpaAuditing, sanitize tổng quát |
| `shared/config/RedisConfig.java` | H10+M1: Redis-local ObjectMapper, bỏ @EnableSchedulerLock |
| `shared/config/JacksonConfig.java` | M4: disable WRITE_DURATIONS_AS_TIMESTAMPS |
| `shared/config/MailConfig.java` | M6: explicit JavaMailSender bean |
| `shared/websocket/WebSocketConfig.java` | M1+M7: bỏ @EnableWebSocketMessageBroker, pool size 4 |
| `shared/outbox/model/OutboxEvent.java` | M8: bỏ redundant field initializer |
| `shared/security/JwtSigner.java` | L2: explicit HS256 |
| `shared/security/JwtVerifier.java` | L7: getSigningKey() return SecretKey |
| `BackendApplication.java` | M1: thêm @EnableJpaAuditing/@EnableSchedulerLock/@EnableWebSocketMessageBroker |
| `lua/ip-rate-limit.lua` | C2: INCR + EXPIRE atomic |

---

## Ghi chú cho reviewer tiếp theo

- Một số file (vd `S3StorageService`) có thể có duplicate ở module khác — chưa verify. Cần cross-check khi review module audio/iam.
- `OutboxProperties` có 2 class (một ở shared, một ở iam.internal.config.IamProperties.Outbox) — verify không conflict khi binding.
- Khi fix H11 (ErrorCode tách module), cần update cả `GlobalExceptionHandler` để không phụ thuộc enum từ mọi module.