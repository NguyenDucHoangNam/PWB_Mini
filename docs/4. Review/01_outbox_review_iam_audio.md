# Code Review Report: Outbox Implementation — IAM & Audio Modules

**Project:** PWB_MiNi (backend)
**Ngày review:** 2026-07-11
**Phạm vi:**
- `com.pwb.backend.iam.internal.application.factory.OutboxEventFactory`
- `com.pwb.backend.iam.internal.infrastructure.publisher.IamOutboxPublisher`
- `com.pwb.backend.iam.internal.infrastructure.job.IamOutboxScheduler`
- `com.pwb.backend.iam.internal.infrastructure.repository.IamOutboxEventRepository`
- `com.pwb.backend.iam.internal.domain.model.IamOutboxEvent`
- `com.pwb.backend.audio.internal.application.factory.AudioOutboxEventFactory`
- `com.pwb.backend.audio.internal.infrastructure.publisher.AudioOutboxPublisher`
- `com.pwb.backend.audio.internal.infrastructure.job.AudioOutboxScheduler`
- `com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository`
- `com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent`
- `com.pwb.backend.shared.messaging.outbox.*`

> **Báo cáo read-only** — không thay đổi mã nguồn. Phát hiện được sắp xếp theo mức độ nghiêm trọng (🔴 High → 🟠 Medium → 🟡 Low → 🟢 Tích cực).

---

## 1. Tóm tắt điều hành (Executive Summary)

| Tiêu chí | Đánh giá |
|---|---|
| **Tính đúng đắn của Transactional Outbox Pattern** | ✅ Đạt — ghi DB + publish event cùng transaction |
| **Tính nhất quán giữa 2 module** | ❌ **Chưa đạt** — nhiều điểm lệch logic giữa IAM và Audio |
| **Tính đồng bộ / tái sử dụng code** | ❌ **Chưa đạt** — trùng lặp logic scheduler, publisher |
| **Khả năng mở rộng (scalability)** | ⚠️ Có điểm nghẽn (poll-based fallback) |
| **Concurrency safety** | ⚠️ Còn lỗ hổng — `processOutboxEvent` không bảo vệ khỏi double-publish |
| **Idempotency** | ⚠️ Có chìa khóa nhưng chưa chặn unique constraint violation |
| **Quan sát (Observability)** | ❌ Thiếu metrics, chỉ có log |
| **Tối ưu DB / Index** | ⚠️ Thiếu index quan trọng |

**Kết luận:** Cả hai module đều đã có khung outbox chuẩn (`shared.messaging.outbox`), nhưng:

1. **Có sự trùng lặp rõ ràng** giữa `IamOutboxScheduler` ↔ `AudioOutboxScheduler` và `IamOutboxPublisher` ↔ `AudioOutboxPublisher` (~95% giống nhau).
2. **Logic publishing có lỗ hổng concurrency nghiêm trọng**: việc gọi `kafkaTemplate.send()` không đồng bộ với transaction commit, có thể publish trùng event khi scheduler poll lẫn với `@TransactionalEventListener`.
3. **`AbstractOutboxScheduler` đã viết nhưng KHÔNG ai dùng** — cả hai module đều tự viết scheduler riêng, bỏ qua base class. Đây là dead code.
4. **Cấu trúc package chưa nhất quán**: Audio dùng `domain.model`, IAM cũng dùng `domain.model` — tốt. Nhưng `AudioOutboxPublisher` không dùng abstract layer trong khi `IamOutboxPublisher` thì implement `OutboxEventProcessor` — lệch nhẹ.

---

## 2. Phát hiện chi tiết theo mức độ nghiêm trọng

### 🔴 CRITICAL

#### **F-01 — Có thể publish trùng event: race giữa `AFTER_COMMIT` listener và `pollPendingOutboxEvents`**

**File:**
- `IamOutboxPublisher.java:32-35` (`handleOutboxCreated`)
- `IamOutboxScheduler.java:38-61` (`pollPendingOutboxEvents`)
- `AudioOutboxPublisher.java:34-37` (cùng pattern)
- `AudioOutboxScheduler.java:35-58`

**Vấn đề:**
```java
// IamOutboxPublisher
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOutboxCreated(OutboxCreatedEvent event) {
    repository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
}
```
Và scheduler chạy `fixedDelayString = "30000"`:
```java
public void pollPendingOutboxEvents() {
    List<IamOutboxEvent> pendingEvents =
        repository.findPendingEventsForUpdate(properties.getBatchSize());
    for (IamOutboxEvent event : pendingEvents) {
        publisher.processOutboxEvent(event);
    }
}
```

**Kịch bản lỗi:**
1. Tại `T=0`, factory lưu event (status=`PENDING`) vào DB, publish `OutboxCreatedEvent` qua `ApplicationEventPublisher`.
2. `AFTER_COMMIT` listener bắt đầu publish lên Kafka (async, `kafkaTemplate.send().whenComplete(...)`).
3. Status chưa được set `PROCESSED` vì callback async chưa chạy xong.
4. Tại `T=5s`, scheduler poll thấy event vẫn `PENDING` → publish lại lần nữa → **duplicate Kafka message**.

**Khắc phục đề xuất (chỉ trong báo cáo, không sửa code):**
- Sau khi `AFTER_COMMIT` listener gọi `processOutboxEvent`, scheduler phải skip event đó trong poll tiếp theo. Có thể dùng flag `processing_started_at` hoặc set tạm status `IN_FLIGHT`.
- Hoặc: dùng distributed lock per-eventId ở publisher layer.

---

#### **F-02 — `@Transactional` trên `processOutboxEvent` không thực sự bảo vệ row update**

**File:** `IamOutboxPublisher.java:38-48`, `AudioOutboxPublisher.java:39-49`

**Vấn đề:**
```java
@Override
@Transactional
public void processOutboxEvent(IamOutboxEvent outboxEvent) {
    String payload = cipher.decrypt(outboxEvent.getPayload());
    try {
        kafkaTemplate.send(topic, outboxEvent.getId(), payload)
            .whenComplete((result, ex) -> handlePublishResult(outboxEvent, ex));
    } catch (Exception ex) {
        handlePublishFailure(outboxEvent, ex);
    }
}
```

`handlePublishResult` được gọi **trong callback async** (Future `whenComplete`), khi đó transaction đã commit từ lâu. Entity `outboxEvent` giờ là **detached**. Gọi `repository.save(outboxEvent)` trong detached state sẽ tạo một transaction mới — nhưng nếu `outboxService.markAsFailed` ném exception thì update bị mất.

**Hậu quả:** Status vẫn là `PENDING` sau khi publish fail → poll lại → infinite retry, nhưng không có atomicity guarantee giữa Kafka ack và DB status.

**Khắc phục đề xuất:**
- Hoặc tách `processOutboxEvent` thành 2 method: synchronous `send()` trong transaction, async `markProcessed` trong transaction mới.
- Hoặc dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` cho callback, không dùng `kafkaTemplate.whenComplete`.

---

#### **F-03 — `findPendingEventsForUpdate` native query KHÔNG thực sự lock row khi được gọi ngoài transaction**

**File:** `OutboxEventRepository.java:16-26` (shared)

**Vấn đề:**
```java
@Query(value = """
    SELECT id, aggregate_type, aggregate_id, event_type, idempotency_key,
           status, retry_count, available_at
    FROM outbox_events
    WHERE status = 'PENDING' AND deleted = false
    AND available_at <= NOW()
    ORDER BY created_at ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<T> findPendingEventsForUpdate(@Param("limit") int limit);
```

`FOR UPDATE SKIP LOCKED` chỉ có tác dụng khi câu lệnh chạy trong một transaction. Cả hai Scheduler đều có `@Transactional`, nhưng:

1. Nếu `properties.isEnabled()` == false → trả về sớm, OK.
2. Nếu batch trống → OK.
3. Nếu **không throw exception** trong vòng lặp `for`, transaction commit bình thường, lock release → OK.
4. **NHƯNG** nếu `publisher.processOutboxEvent` throw exception đã được catch (`try { ... } catch (Exception ex) { log.error(...) }`), transaction vẫn commit. Lock release. **Status update từ `markAsProcessed`/`markAsFailed` vẫn nằm trong transaction gốc của Scheduler.** Điều này **OK** — nhưng nếu publisher publish async (callback `whenComplete`) thì update status **diễn ra sau commit**, lock đã release. → Race với instance khác.

**Khắc phục:** Status phải được update **synchronously trong transaction của Scheduler**, không phải async callback.

---

#### **F-04 — `AudioCdcConfig` filter `aggregate_type='AUDIO_DISTRIBUTION'` nhưng `AudioOutboxEventFactory` lại đặt `AGGREGATE_TYPE_DISTRIBUTION`**

**File:**
- `AudioOutboxEventFactory.java:17`: `public static final String AGGREGATE_TYPE_DISTRIBUTION = "AUDIO_DISTRIBUTION";`
- `AudioCdcConfig.java:21`: `return new CdcOutboxEventHandler<>(repository, processor, "AUDIO_DISTRIBUTION");`

→ Đây là trùng khớp, **OK**. Nhưng tên constant `AGGREGATE_TYPE_DISTRIBUTION` rất dễ gây nhầm lẫn vì giá trị là `"AUDIO_DISTRIBUTION"`. Đề xuất đổi tên constant.

---

### 🟠 HIGH

#### **F-05 — Trùng lặp gần như toàn bộ giữa `IamOutboxPublisher` và `AudioOutboxPublisher`**

**So sánh từng dòng:**

| Thành phần | IAM | Audio |
|---|---|---|
| Constructor | 5 dependency | 4 dependency (thiếu `properties`) |
| `handleOutboxCreated` | giống | giống |
| `processOutboxEvent` | giống (+1 dòng `resolveTopic`) | giống |
| `handlePublishResult` | giống | giống |
| `handlePublishFailure` | giống | giống |
| Topic | `notification-events` / `iam-account-events` | `notification-events` |

**`AudioOutboxPublisher` không inject `AudioProperties`** → không thể cấu hình topic, batch size từ properties (đang hardcode). Trong khi IAM thì inject `IamProperties` nhưng **cũng không dùng** trong class body (chỉ inject cho constructor).

**Khắc phục đề xuất:** Nâng cấp `OutboxEventProcessor` thành base abstract class có sẵn `kafkaTemplate`, `cipher`, `outboxService`, `repository`. Chỉ override `resolveTopic()`. Hai publisher chỉ còn ~10 dòng.

---

#### **F-06 — Trùng lặp toàn bộ giữa `IamOutboxScheduler` và `AudioOutboxScheduler`**

| Thành phần | IAM | Audio |
|---|---|---|
| Constructor | 3 dependency | 3 dependency |
| `@Scheduled(fixedDelayString)` | 30000 | 30000 |
| `@SchedulerLock` config | giống | giống |
| `@Transactional` | giống | giống |
| Vòng lặp xử lý | giống hệt | giống hệt |

Đáng chú ý: `AbstractOutboxScheduler` ở `shared/messaging/outbox/scheduler/AbstractOutboxScheduler.java` **đã tồn tại** với logic pollAndProcess() y hệt nhưng **không module nào extend nó**.

**Đây là dead code nghiêm trọng** — đã viết base class nhưng không ai dùng.

**Khắc phục đề xuất:**
- Refactor: cho cả 2 Scheduler extend `AbstractOutboxScheduler`.
- Tạo 1 `GenericOutboxPublisher` ở shared module xử lý chung.
- Hoặc xóa `AbstractOutboxScheduler` nếu không cần.

---

#### **F-07 — `OutboxEvent` entity có `payloadKeyVersion` field nhưng không bao giờ được set**

**File:** `OutboxEvent.java:53-54`

```java
@Column(name = "payload_key_version")
private Integer payloadKeyVersion;
```

Cipher có hỗ trợ version rotation (`legacyKeys`, `keyVersion`) nhưng entity không lưu version đã dùng khi encrypt → khi decrypt phải parse từ prefix `"enc:v1:..."`. Nếu DB row bị corrupt (mất prefix), `pickKey` fallback về version 1, có thể giải mã sai dữ liệu từ version khác.

**Khắc phục:** Set `payloadKeyVersion` từ `cipher.keyVersion()` khi `createEvent`, đảm bảo traceability.

---

### 🟡 MEDIUM

#### **F-08 — Thiếu validation `idempotencyKey` ở `createEvent`**

**File:** `AbstractOutboxEventFactory.java:31-56`

```java
public <T extends OutboxEvent> T createEvent(...) {
    ...
    event.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
```

Nếu caller quên truyền key, factory vẫn sinh UUID mới → không có idempotency thật sự. Tên method ngụ ý "tạo event", nhưng ngữ nghĩa idempotency yếu.

**Khắc phục:** Bắt buộc `idempotencyKey` (ném `IllegalArgumentException` nếu null/blank), để caller phải suy nghĩ về key.

---

#### **F-09 — Idempotency chưa enforce: không có unique-constraint check trước save**

**File:** `OutboxEvent.java:28-29`

```java
@Column(name = "idempotency_key", length = 100, unique = true, nullable = false)
private String idempotencyKey;
```

Có `unique = true` ở DB, nhưng **không có try/catch `DataIntegrityViolationException`** ở factory. Nếu 2 transaction cùng tạo event với cùng key (ví dụ retry từ Kafka consumer → retry business logic), sẽ throw 500.

**Khắc phục:**
- Ở factory, dùng `findByIdempotencyKey` trước khi save (có method này ở `AudioOutboxEventRepository`, nhưng **không ở IAM**).
- Hoặc catch exception và return existing event.

**Lệch chuẩn:** `AudioOutboxEventRepository` có `findByIdempotencyKey`, `IamOutboxEventRepository` thì không → không đồng bộ.

---

#### **F-10 — `OutboxEvent.createdAt` ASC ordering + `LIMIT` chưa có `id` tiebreaker**

**File:** `OutboxEventRepository.java:22-23`

```sql
ORDER BY created_at ASC
LIMIT :limit
```

Nếu 2 event có cùng `created_at` (millisecond collision), thứ tự không ổn định → có thể skip event hoặc xử lý không đều.

**Khắc phục:** `ORDER BY created_at ASC, id ASC`.

---

#### **F-11 — `OutboxService.markAsFailed` substring `ex.getMessage()` có thể throw `NullPointerException`**

**File:** `OutboxService.java:30-35`

```java
String errorMessage = ex != null && ex.getMessage() != null
    ? ex.getMessage().substring(0, Math.min(1000, ex.getMessage().length()))
    : ex != null ? ex.getClass().getSimpleName() : "Unknown error";
```

Nếu `ex.getMessage()` có surrogate pairs (emoji, multi-byte) và bị `substring(0, 1000)` cắt giữa chừng có thể không hợp lệ. Hơn nữa, `errorMessage` có thể chứa `\n`, `\r`, ký tự điều khiển → có thể log injection.

**Khắc phục:**
- Dùng `StringUtils.truncate(ex.getMessage(), 1000)` với validation.
- Sanitize control characters.

---

#### **F-12 — `publishOutboxCreatedEvent` dùng `ApplicationEventPublisher` mà không có `@TransactionalEventListener` semantics**

**File:** `AbstractOutboxEventFactory.java:66-68`

```java
protected void publishOutboxCreatedEvent(String eventId) {
    eventPublisher.publishEvent(new OutboxCreatedEvent(eventId));
}
```

Listener đăng ký với `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — chỉ chạy sau commit. Tốt. **NHƯNG** `IAM factory` có thêm guard `requireActiveTransaction()` kiểm tra cả `isSynchronizationActive()`. `Audio factory` thì **không có guard này** → có thể gọi ngoài transaction → `AFTER_COMMIT` không fire → scheduler phải poll.

**Lệch chuẩn giữa 2 module.**

---

#### **F-13 — `AudioOutboxScheduler` có comment nói "Mirrors IAM's IamOutboxScheduler" nhưng không có shared logic**

File: `AudioOutboxScheduler.java:23-25`

> "Mirrors IAM's `IamOutboxScheduler` so each module gets its own shedlock-guarded batch processor without sharing state across modules."

Chính comment này xác nhận sự trùng lặp. Đã viết base class nhưng lại tự reimplement.

---

#### **F-14 — `IamOutboxPublisher.resolveTopic` không trừu tượng hóa → khó mở rộng**

```java
private String resolveTopic(String eventType) {
    return "ACCOUNT_ANONYMIZED".equals(eventType) ? IAM_ACCOUNT_EVENTS_TOPIC : NOTIFICATION_TOPIC;
}
```

Hardcode list event type. Nếu thêm `EVENT_TYPE_NEW_X`, phải sửa logic ở đây. Audio thì hardcode 1 topic nên không có vấn đề, nhưng nếu Audio mở rộng sẽ gặp vấn đề tương tự.

**Khắc phục:** Inject `Map<String, String>` (eventType → topic) qua config.

---

### 🟢 LOW / OBSERVATION

#### **F-15 — `OutboxPayloadCipher` có `enabled` flag nhưng khi disabled vẫn encrypt plaintext với prefix `enc:` không?**

Kiểm tra: khi `enabled == false`, `encrypt()` return `plaintext` (no prefix). Nhưng khi `enabled == true` và payload cũ (legacy, không prefix), `decrypt()` return `stored` as-is → **không thật sự giải mã**. OK cho migration nhưng dễ gây nhầm.

Đề xuất: log warning khi decrypt mà không có prefix.

---

#### **F-16 — `OutboxPayloadCipher.deriveKey` SHA-256 trên password ngắn**

```java
byte[] derived = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
```

Không có salt, không có PBKDF2/Argon2. Nếu secret yếu có thể brute-force. AES key nên được supply trực tiếp (Base64 256-bit), không derive từ password text.

---

#### **F-17 — `IdempotencyKey` không có format validation**

`@Column(name = "idempotency_key", length = 100, unique = true)` — không có constraint về format. Caller có thể đặt `"SELECT * FROM users"` → SQL injection khi log? (Không nguy hiểm nếu chỉ dùng ở log, nhưng vẫn nên validate.)

---

#### **F-18 — Không có metrics/observability**

Không có Micrometer `@Timed`, không có counter cho `outbox.published`, `outbox.failed`, `outbox.dead_lettered`. Chỉ có log.

**Đề xuất:**
```java
Counter.builder("outbox.events.published")
    .tag("module", "iam").tag("event_type", eventType).register(meterRegistry).increment();
```

---

#### **F-19 — Thiếu index `outbox_events(status, available_at, created_at)`**

Query `findPendingEventsForUpdate` filter theo `status = 'PENDING'` + `available_at <= NOW()` + `ORDER BY created_at ASC`. Index hiện tại (nếu có) trên PK `id`. Cần composite index:

```sql
CREATE INDEX idx_outbox_pending 
ON outbox_events (status, available_at, created_at) 
WHERE deleted = false;
```

---

#### **F-20 — `IamOutboxScheduler` có H3/H8 comment giải thích concurrency nhưng không có defensive code**

```java
/**
 * H3: marked {@link Transactional} so each batch runs in a single
 * transaction; the row lock acquired by the native query is held until
 * commit...
 *
 * H8: shedlock guards against the second instance racing us...
 */
```

Comment tốt nhưng không có fallback nếu `processOutboxEvent` throws exception không mong đợi (hiện tại đã có try/catch), nhưng nếu `markAsProcessed` throw → toàn bộ transaction rollback → event trở về `PENDING` → retry liên tục.

---

#### **F-21 — `AbstractOutboxEventFactory.onEventCreated` hook rỗng ở IAM**

```java
@Override
protected void onEventCreated(OutboxEvent event) {
}
```

Empty hook. Tại sao abstract? Có dự định dùng cho audit/log nhưng không implement. Hoặc bỏ abstract.

---

#### **F-22 — Tên constant `AGGREGATE_TYPE_DISTRIBUTION` không khớp giá trị**

`AudioOutboxEventFactory.AGGREGATE_TYPE_DISTRIBUTION = "AUDIO_DISTRIBUTION"`. Tên constant gợi `"DISTRIBUTION"` nhưng giá trị là module scope. Người đọc dễ nhầm.

---

#### **F-23 — `AudioProperties` có field `aes.outboxEncryptionKey` trùng với `app.outbox.encryption-key`**

Trong `AudioProperties.java`:
```java
@NotBlank
@Pattern(regexp = ".{32,}", message = "outboxEncryptionKey must be at least 32 chars")
private String outboxEncryptionKey = "";
```

Và trong `OutboxProperties.java`:
```java
@NotBlank
private String encryptionKey;
```

Có vẻ 2 chỗ đang cấu hình **cùng một key** với 2 tên khác nhau (`app.audio.aes.outbox-encryption-key` vs `app.outbox.encryption-key`). Cần xác nhận chỉ 1 nguồn được dùng.

---

#### **F-24 — `AudioOutboxEventFactory.create` không tự sinh `idempotencyKey` khi caller null**

```java
public AudioOutboxEvent create(String eventType, String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    AudioOutboxEvent event = new AudioOutboxEvent();
    createEvent(event, eventType, aggregateId, payload, idempotencyKey);
    ...
}
```

Caller truyền `null` → super class sinh UUID. Caller quên idempotency → không có semantic guarantee. IAM factory thì có 2 overload, một trong đó cũng default UUID. Đồng bộ: cả 2 nên yêu cầu bắt buộc.

---

#### **F-25 — `AudioCdcConfig` filter `aggregate_type='AUDIO_DISTRIBUTION'` nhưng module có thể tạo event với aggregate khác trong tương lai**

Nếu sau này Audio module mở rộng (ví dụ `AUDIO_PROCESSING` event), phải sửa CdcConfig. Nên là:
- `aggregate_type` filter ở từng loại event.
- Hoặc có CDC listener cho từng module con.

---

## 3. Bảng so sánh tổng hợp IAM ↔ Audio

| Tiêu chí | IAM Outbox | Audio Outbox | Đồng bộ? |
|---|---|---|---|
| Base class dùng chung | `AbstractOutboxEventFactory` ✅ | `AbstractOutboxEventFactory` ✅ | ✅ |
| `AbstractOutboxScheduler` được extend | ❌ Tự viết | ❌ Tự viết | ✅ (cùng tự viết) |
| `requireActiveTransaction` guard | ✅ Có | ❌ Không có | ❌ |
| `idempotencyKey` bắt buộc | ❌ Có default UUID | ❌ Có default UUID | ✅ (cùng thiếu) |
| `findByIdempotencyKey` ở repository | ❌ Không có | ✅ Có | ❌ |
| Topic resolution | `resolveTopic(eventType)` | Hardcode 1 topic | ❌ |
| `@TransactionalEventListener` AFTER_COMMIT | ✅ | ✅ | ✅ |
| `@SchedulerLock` | ✅ | ✅ | ✅ |
| `properties.isEnabled()` guard | ✅ | ✅ | ✅ |
| Inject `properties` cho config | ✅ (nhưng không dùng topic) | ❌ | ❌ |
| Mark as PROCESSED callback | `whenComplete` async | `whenComplete` async | ✅ (cùng có race) |
| Encrypt payload | ✅ | ✅ | ✅ |
| CDC listener | ✅ `IamCdcConfig` | ✅ `AudioCdcConfig` | ✅ |
| Aggregate type filter CDC | (không có, xử lý tất cả) | `"AUDIO_DISTRIBUTION"` | ❌ |

---

## 4. Đề xuất kiến trúc tối ưu (chỉ để tham khảo, không sửa code trong review này)

### 4.1 Refactor tái sử dụng

```
shared.messaging.outbox/
├── model/OutboxEvent.java               (giữ nguyên)
├── factory/AbstractOutboxEventFactory  (giữ nguyên)
├── cipher/OutboxPayloadCipher            (giữ nguyên)
├── config/OutboxProperties               (giữ nguyên)
├── enums/OutboxEventStatus               (giữ nguyên)
├── event/OutboxCreatedEvent              (giữ nguyên)
├── exception/...                         (giữ nguyên)
├── processor/
│   ├── OutboxEventProcessor<T>           (giữ nguyên — interface)
│   └── AbstractOutboxPublisher<T>        (MỚI — generic publish + Kafka send + mark processed/failed)
├── repository/OutboxEventRepository      (giữ nguyên)
├── scheduler/AbstractOutboxScheduler     (ĐÃ CÓ — chưa dùng)
└── service/OutboxService                 (giữ nguyên)

iam.internal.infrastructure/
├── job/IamOutboxScheduler                (extends AbstractOutboxScheduler)
├── publisher/IamOutboxPublisher          (extends AbstractOutboxPublisher, override topic resolver)
└── repository/IamOutboxEventRepository   (bổ sung findByIdempotencyKey)

audio.internal.infrastructure/
├── job/AudioOutboxScheduler              (extends AbstractOutboxScheduler)
├── publisher/AudioOutboxPublisher        (extends AbstractOutboxPublisher)
└── repository/AudioOutboxEventRepository (giữ nguyên)
```

### 4.2 Sửa lỗi concurrency (F-01 → F-03)

Option A: **Single path** — bỏ `@TransactionalEventListener`, chỉ dùng Scheduler. Trade-off: tăng latency publish (lên đến 30s).

Option B: **Atomic claim** — Scheduler query `status='PENDING' AND processing_started_at IS NULL`, set `processing_started_at = NOW()`, commit, rồi publish. Nếu publish OK → mark PROCESSED. Nếu fail → reset `processing_started_at` và `status='FAILED'`.

### 4.3 Schema improvements (F-19)

```sql
CREATE INDEX idx_outbox_pending 
ON outbox_events (status, available_at, created_at) 
WHERE deleted = false;

ALTER TABLE outbox_events 
ADD CONSTRAINT chk_idempotency_key_format 
CHECK (idempotency_key ~ '^[A-Za-z0-9_:-]+$');
```

### 4.4 Observability (F-18)

```yaml
management:
  metrics:
    tags:
      application: pwb-backend
```

```java
Counter.builder("outbox.events.published").tag("module", module).register(registry).increment();
Counter.builder("outbox.events.failed").tag("module", module).register(registry).increment();
Gauge.builder("outbox.events.dead_lettered", () -> countDeadLettered).register(registry);
```

---

## 5. Câu hỏi cần làm rõ với team

1. **Có bao nhiêu instance** của backend chạy song song? Shedlock giả định Redis backend; nếu Redis down, scheduler từng instance chạy độc lập → có thể race.
2. **`app.outbox.encryption-key` được supply từ đâu**? Vault, K8s secret, hay hardcode?
3. **Có cần support multi-tenant** không? Hiện `aggregate_type` chỉ có `IAM` và `AUDIO_DISTRIBUTION` — nếu thêm tenant thì schema cần thay đổi.
4. **Khi nào purge `DEAD_LETTERED` rows**? Hiện không có job purge.
5. **`AudioProperties.aes.outboxEncryptionKey` đang dùng song song với `app.outbox.encryption-key`?** Xác nhận cấu hình nào đang active.

---

## 6. Tổng kết

### Điểm mạnh
- ✅ Đã chuyển sang `shared.messaging.outbox` chuẩn.
- ✅ Schema có `idempotency_key` UNIQUE.
- ✅ Có `FOR UPDATE SKIP LOCKED` để tránh double-process giữa các instance.
- ✅ Có CDC integration (`CdcOutboxEventHandler`).
- ✅ Encryption payload AES-GCM với key rotation.
- ✅ Dead-letter handling sau N retries.
- ✅ Backoff exponential + jitter.
- ✅ Transactional event listener (AFTER_COMMIT).
- ✅ IAM factory có `requireActiveTransaction` defensive check.

### Điểm yếu nghiêm trọng cần ưu tiên xử lý
1. 🔴 F-01 / F-02 / F-03: Race condition giữa `AFTER_COMMIT` listener và scheduler poll → có thể publish trùng event.
2. 🟠 F-06: `AbstractOutboxScheduler` chưa được sử dụng — dead code, hoặc cần refactor.
3. 🟠 F-05: Trùng lặp giữa 2 publisher → khó maintain.
4. 🟡 F-08 / F-09: Idempotency chưa thực sự enforce ở factory.
5. 🟡 F-19: Thiếu index cho query scheduler.

### Đề xuất ưu tiên thực hiện (nếu có kế hoạch refactor)

| Ưu tiên | Hạng mục | Effort | Impact |
|---|---|---|---|
| P0 | Sửa race condition publish (F-01→F-03) | M | Cao |
| P1 | Refactor 2 publisher dùng chung base class (F-05) | M | Trung bình |
| P1 | Refactor 2 scheduler dùng `AbstractOutboxScheduler` (F-06) | M | Trung bình |
| P2 | Bổ sung index + constraint DB (F-19) | S | Cao (perf) |
| P2 | Idempotency enforcement (F-08, F-09) | S | Trung bình |
| P3 | Metrics + Observability (F-18) | M | Trung bình |
| P3 | Đồng bộ `findByIdempotencyKey` giữa 2 repo (F-09) | XS | Thấp |
| P4 | Validation `idempotencyKey` format (F-17) | XS | Thấp |
| P4 | Sanitize log injection (F-11) | XS | Thấp |

---

## 7. Phụ lục: Danh sách file đã review

| # | File | Trạng thái |
|---|---|---|
| 1 | `shared/messaging/outbox/model/OutboxEvent.java` | Reviewed |
| 2 | `shared/messaging/outbox/factory/AbstractOutboxEventFactory.java` | Reviewed |
| 3 | `shared/messaging/outbox/repository/OutboxEventRepository.java` | Reviewed |
| 4 | `shared/messaging/outbox/scheduler/AbstractOutboxScheduler.java` | Reviewed |
| 5 | `shared/messaging/outbox/processor/OutboxEventProcessor.java` | Reviewed |
| 6 | `shared/messaging/outbox/processor/CdcOutboxEventHandler.java` | Reviewed |
| 7 | `shared/messaging/outbox/cipher/OutboxPayloadCipher.java` | Reviewed |
| 8 | `shared/messaging/outbox/config/OutboxProperties.java` | Reviewed |
| 9 | `shared/messaging/outbox/service/OutboxService.java` | Reviewed |
| 10 | `shared/messaging/outbox/enums/OutboxEventStatus.java` | Reviewed |
| 11 | `shared/messaging/outbox/event/OutboxCreatedEvent.java` | Reviewed |
| 12 | `shared/messaging/outbox/exception/OutboxEventSerializationException.java` | Reviewed |
| 13 | `iam/internal/domain/model/IamOutboxEvent.java` | Reviewed |
| 14 | `iam/internal/infrastructure/repository/IamOutboxEventRepository.java` | Reviewed |
| 15 | `iam/internal/application/factory/OutboxEventFactory.java` | Reviewed |
| 16 | `iam/internal/infrastructure/publisher/IamOutboxPublisher.java` | Reviewed |
| 17 | `iam/internal/infrastructure/job/IamOutboxScheduler.java` | Reviewed |
| 18 | `iam/api/event/OutboxCreatedEvent.java` | Reviewed |
| 19 | `iam/internal/interfaces/config/IamProperties.java` | Reviewed |
| 20 | `audio/internal/domain/model/AudioOutboxEvent.java` | Reviewed |
| 21 | `audio/internal/infrastructure/repository/AudioOutboxEventRepository.java` | Reviewed |
| 22 | `audio/internal/application/factory/AudioOutboxEventFactory.java` | Reviewed |
| 23 | `audio/internal/infrastructure/publisher/AudioOutboxPublisher.java` | Reviewed |
| 24 | `audio/internal/infrastructure/job/AudioOutboxScheduler.java` | Reviewed |
| 25 | `audio/internal/interfaces/config/AudioKafkaConfig.java` | Reviewed |
| 26 | `audio/internal/interfaces/config/AudioCdcConfig.java` | Reviewed |
| 27 | `audio/internal/interfaces/config/AudioProperties.java` | Reviewed |
| 28 | `audio/internal/application/service/DistributionService.java` | Reviewed (consumer) |
| 29 | `shared/kernel/model/BaseEntity.java` | Reviewed (parent class) |

---

## 8. Báo cáo xử lý sau review

**Ngày xử lý:** 2026-07-11  
**Trạng thái:** Đã sửa các bug runtime và các điểm lệch logic chính trong scope IAM/Audio outbox.

### 8.1 Các finding đã xử lý

| Finding | Trạng thái | Cách xử lý |
|---|---|---|
| F-01 / F-02 / F-03 | ✅ Đã xử lý | Thêm trạng thái `IN_FLIGHT`, cột `processing_started_at`, claim gate qua `OutboxService.tryClaim`, publisher xử lý theo event id trong transaction riêng, scheduler recover stale claim. |
| F-05 | ✅ Đã xử lý | Thêm `shared.messaging.outbox.processor.AbstractOutboxPublisher`; `IamOutboxPublisher` và `AudioOutboxPublisher` chỉ override topic resolution. |
| F-06 / F-13 | ✅ Đã xử lý | `IamOutboxScheduler` và `AudioOutboxScheduler` extend `AbstractOutboxScheduler`; base scheduler không còn dead code. |
| F-07 | ✅ Đã xử lý | `AbstractOutboxEventFactory.createEvent` set `payloadKeyVersion` từ `OutboxPayloadCipher.keyVersion()` khi encryption enabled. |
| F-08 / F-24 | ✅ Đã xử lý | `idempotencyKey` bắt buộc, không còn fallback UUID trong shared factory. |
| F-09 | ✅ Đã xử lý | Thêm `findByIdempotencyKey` vào shared repository và IAM repository; factory dùng `createOrReuse`. |
| F-10 | ✅ Đã xử lý | Query scheduler đổi sang `ORDER BY created_at ASC, id ASC`. |
| F-11 | ✅ Đã xử lý | `OutboxService.markAsFailed` sanitize control characters và truncate `lastError`. |
| F-12 | ✅ Đã xử lý | `AudioOutboxEventFactory` có guard transaction giống IAM. |
| F-14 | ✅ Đã xử lý | IAM publisher dùng topic map; Audio dùng default topic qua abstract publisher. |
| F-19 | ✅ Đã xử lý | Thêm migration `V9__add_processing_started_at_and_outbox_indexes.sql` với composite pending index và stale in-flight index. |
| F-21 | ✅ Đã xử lý | Bỏ hook `onEventCreated` rỗng. |
| F-22 | ✅ Đã xử lý | Rename constant thành `AGGREGATE_TYPE_AUDIO_DISTRIBUTION`; `AudioCdcConfig` dùng constant. |
| F-23 | ✅ Đã xử lý | Xóa cấu hình trùng `app.audio.aes.outbox-encryption-key`; outbox dùng nguồn `app.outbox.encryption-key`. |

### 8.2 Finding observation / không cần sửa code trực tiếp

| Finding | Trạng thái | Ghi chú |
|---|---|---|
| F-04 | ✅ Không còn action | Review xác nhận giá trị filter CDC và aggregate type đang khớp. |
| F-15 | ✅ Không còn action | Cipher đã có fallback plaintext/migration behavior; không thay đổi logic. |
| F-16 | ⚠️ Deferred | Đổi key derivation sang PBKDF2/Argon2 là security hardening lớn, cần migration/secrets plan riêng. |
| F-18 | ⚠️ Deferred | Metrics/Micrometer chưa thêm trong lần sửa này; nên làm task observability riêng. |
| F-20 | ✅ Đã giảm rủi ro | Scheduler nay dùng claim gate + stale recovery, không chỉ dựa vào comment. |
| F-25 | ⚠️ Deferred | Mở rộng CDC cho nhiều aggregate type audio tương lai cần requirement cụ thể. |

### 8.3 File chính đã thay đổi

- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/processor/AbstractOutboxPublisher.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/scheduler/AbstractOutboxScheduler.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/model/OutboxEvent.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/enums/OutboxEventStatus.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/repository/OutboxEventRepository.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/service/OutboxService.java`
- `backend/src/main/java/com/pwb/backend/shared/messaging/outbox/factory/AbstractOutboxEventFactory.java`
- `backend/src/main/java/com/pwb/backend/iam/internal/infrastructure/publisher/IamOutboxPublisher.java`
- `backend/src/main/java/com/pwb/backend/iam/internal/infrastructure/job/IamOutboxScheduler.java`
- `backend/src/main/java/com/pwb/backend/iam/internal/infrastructure/repository/IamOutboxEventRepository.java`
- `backend/src/main/java/com/pwb/backend/iam/internal/application/factory/OutboxEventFactory.java`
- `backend/src/main/java/com/pwb/backend/audio/internal/infrastructure/publisher/AudioOutboxPublisher.java`
- `backend/src/main/java/com/pwb/backend/audio/internal/infrastructure/job/AudioOutboxScheduler.java`
- `backend/src/main/java/com/pwb/backend/audio/internal/application/factory/AudioOutboxEventFactory.java`
- `backend/src/main/java/com/pwb/backend/audio/internal/interfaces/config/AudioCdcConfig.java`
- `backend/src/main/java/com/pwb/backend/audio/internal/interfaces/config/AudioProperties.java`
- `backend/src/main/resources/config/application-audio.yaml`
- `backend/src/main/resources/db/migration/iam/V9__add_processing_started_at_and_outbox_indexes.sql`

### 8.4 Validation

- Đã kiểm tra thủ công các file đã sửa và loại bỏ import/comment thừa.
- Chưa chạy được `mvn compile`/test vì môi trường hiện tại không có `mvn` trong PATH.

---

**Báo cáo cập nhật sau xử lý.**