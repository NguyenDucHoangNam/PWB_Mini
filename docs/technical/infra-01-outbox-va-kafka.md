# Hạ tầng — Outbox & Kafka

> `shared-infrastructure`: `infra/outbox`, `infra/kafka`, `infra/mail`

---

## 1. Kafka là gì

Apache Kafka là một **distributed event streaming platform** — nền tảng truyền tải sự kiện phân tán. Khác với hàng đợi truyền thống (RabbitMQ, ActiveMQ), Kafka lưu thông điệp lên đĩa theo thứ tự, cho phép nhiều consumer đọc cùng một luồng dữ liệu mà không xoá nó.

Ba khái niệm cốt lõi:

**Topic** — một kênh chủ đề. Producer ghi vào topic, consumer đọc từ topic. Một topic có thể chia thành nhiều partition để xử lý song song.

**Consumer group** — một nhóm consumer cùng đọc một topic. Kafka đảm bảo mỗi partition chỉ được một consumer trong group xử lý tại một thời điểm. Nếu consumer chết, Kafka tự gán lại partition cho consumer khác trong group (rebalance).

**Dead Letter Topic (DLT)** — khi một thông điệp gặp lỗi và hết số lần thử lại, nó được đẩy sang DLT thay vì chặn hàng đợi. DLT lưu giữ thông điệp hỏng để phân tích sau.

---

## 2. Kafka giữ vai trò gì trong hệ thống này

Kafka là **xương sống bất đồng bộ** — mọi tác vụ không cần trả kết quả ngay cho user đều đi qua Kafka. Hiện tại có hai đường:

| Đường | Topic | Consumer group | Mô tả |
|---|---|---|---|
| **Email** | `notification.email.v1` | `pwb-mail-consumer` | Gửi email xác thực OTP, thông báo qua SMTP. Nội dung email đã được render sẵn trước khi xếp hàng |
| **Audio processing** | `voice.processing.v1` | `audio-song-processor` | Merge watermark/voice tag vào bài hát bằng FFmpeg. Mỗi bài tốn hàng phút |

Mỗi topic có một DLT tương ứng (thêm hậu tố `.DLT`), ví dụ `notification.email.v1.DLT`.

Kafka chạy ở chế độ **KRaft** (không cần Zookeeper), image `confluentinc/cp-kafka:7.6.0`, một node duy nhất vừa là broker vừa là controller. Dữ liệu được lưu trên volume `kafka_data`.

---

## 3. Outbox Pattern là gì

Outbox Pattern giải quyết bài toán **dual write** — khi một thao tác nghiệp vụ cần đồng thời ghi vào database và gửi thông điệp ra hệ thống khác. Hai hành động này thuộc hai hệ thống khác nhau, không có distributed transaction nào bao được cả hai.

Nếu gửi thẳng vào Kafka mà không qua outbox:

- Gửi **trong** transaction → Kafka nhận thông điệp, nhưng nếu transaction rollback thì đó là một sự kiện "ma" — báo chuyện chưa từng xảy ra.
- Gửi **sau** commit → tiến trình chết ở giữa thì chuyện đã xảy ra mà không ai biết — sự kiện "mất".

Outbox Pattern thoát khỏi thế lưỡng nan bằng cách biến việc "gửi ra ngoài" thành một lệnh INSERT vào bảng outbox, nằm gọn trong cùng database transaction với thay đổi nghiệp vụ. Một tiến trình nền riêng (relay) đọc bảng đó rồi gửi thật vào Kafka.

Hai việc — ghi nghiệp vụ và ghi ý định gửi — hoặc cùng thành công, hoặc cùng rollback. Không mất, không thừa.

---

## 4. Vì sao phải dùng Outbox Pattern

- **Tính nhất quán**: nghiệp vụ và sự kiện luôn đồng bộ. Không có sự kiện ma, không có sự kiện mất.
- **Khả năng phục hồi**: Kafka chết, SMTP chết — request của user vẫn thành công (201). Khi dịch vụ hồi phục, sự kiện tự động được gửi lại.
- **Tách bạch phụ thuộc**: module nghiệp vụ không biết sự kiện đi đâu. IAM chỉ phát một Spring event "tôi muốn gửi email", việc nó thành outbox → Kafka → SMTP là chuyện của hạ tầng.

---

## 5. Khi nào sử dụng

Trong PWB, **mọi đường bất đồng bộ đều đi qua outbox** — không use case nào gửi thẳng vào Kafka.

Outbox phù hợp khi:

- Cần đảm bảo **at-least-once delivery** cho sự kiện bất đồng bộ.
- Chấp nhận **độ trễ nhỏ** (gần tức thì nhờ cơ chế nudge, fallback tối đa 5 giây nhờ poll định kỳ) để đổi lấy tính nhất quán.

Không phù hợp khi cần realtime tức thì (sub-second latency) — những gì cần như vậy đi đường STOMP/WebSocket.

---

## 6. Triển khai trong dự án

### 6.1. Bảng `outbox_events`

Schema được tạo bởi Flyway migration `V100` + `V101`, gồm 17 cột:

```
id · event_id · event_type · aggregate_type · aggregate_id
topic · payload_key · payload (jsonb) · headers (jsonb)
status · retry_count · next_attempt_at · last_error
created_at · sent_at · version · lease_until
```

Bốn trạng thái: `PENDING` → `PROCESSING` → `SENT` (hoặc `FAILED`).

Bốn index:

| Index | Cột | Mục đích |
|---|---|---|
| `idx_outbox_status_next_attempt` | `(status, next_attempt_at)` | Relay tìm hàng cần gửi |
| `idx_outbox_lease_until` | `(lease_until) WHERE status = 'PROCESSING'` | **Partial index** — chỉ phủ hàng đang xử lý, dùng cho thu hồi lease |
| `idx_outbox_topic` | `(topic)` | Lọc theo topic |
| `uk_outbox_event_id` | `(event_id) UNIQUE` | Đảm bảo idempotency — cùng sự kiện không ghi hai lần |

`version` là khoá lạc quan — chuẩn bị sẵn cho kịch bản nhiều worker cùng chạy.

### 6.2. Ghi vào outbox

Module nghiệp vụ không import outbox. Nó phát một Spring application event, listener ở tầng hạ tầng bắt event đó và ghi hàng vào bảng `outbox_events` trong cùng transaction.

Có hai đường ghi:

**Đường audio** — Audio module gọi `OutboxEnqueueHelper.enqueue()`. Helper tạo một `OutboxEnqueueRequested` event. `OutboxEnqueueListener` dùng `@TransactionalEventListener(BEFORE_COMMIT)` bắt event này và gọi `OutboxWriter` để INSERT hàng trước khi transaction commit.

**Đường email** — IAM module phát `EmailEventRequested` qua `ApplicationEventPublisher`. `OutboxEmailEnqueueListener` ở `infra/mail/api` bắt event, serialize payload thành JSON, rồi gọi `OutboxWriter` trực tiếp. IAM không import bất kỳ class nào từ `infra/outbox`.

`OutboxWriter` có hai cài đặt: `OutboxJpaWriter` dùng trong production — ghi thật vào database; `LoggingOutboxWriter` dùng khi test — chỉ ghi log, cho phép chạy hệ thống mà không cần bảng outbox.

### 6.3. Relay — đọc outbox rồi gửi vào Kafka

`OutboxRelayScheduler` là tiến trình nền đọc bảng outbox và gửi sự kiện thật vào Kafka.

**Claim bằng lease**: Relay dùng `SELECT ... FOR UPDATE SKIP LOCKED` để claim một lô hàng `PENDING`, đổi trạng thái thành `PROCESSING` và gán `lease_until` (mặc định 60 giây). Nếu relay chết sau khi claim, hàng sẽ kẹt ở `PROCESSING` — nhưng nhờ lease, sau 60 giây worker khác (hoặc chính nó sau khi khởi động lại) sẽ nhặt lại hàng hết hạn.

**Thu hồi trước, nhận mới sau**: Mỗi lần chạy, relay ưu tiên thu hồi lease hết hạn trước, rồi mới claim hàng mới, tổng không vượt `batchSize` (mặc định 20). Nếu không ưu tiên như vậy, một dòng chảy sự kiện liên tục sẽ khiến hàng bị bỏ rơi không bao giờ tới lượt.

**Gửi sau khi commit**: Việc gửi Kafka được đăng ký qua `TransactionSynchronization.afterCommit()`. Nếu gửi trước khi commit, consumer có thể xử lý xong trước khi hàng kịp được đánh dấu `PROCESSING` — hàng đó sẽ được claim lại và gửi lần nữa.

**Cách ly lỗi**: Relay bọc `try/catch` từng sự kiện trong vòng lặp. Một payload hỏng không chặn cả lô.

### 6.4. Nudge — relay tức thì

Relay quét bảng mỗi **5 giây** (poll định kỳ), nhưng đây chỉ là lưới an toàn. Trong trường hợp thường, `OutboxRelayTrigger` kích hoạt relay **ngay lập tức** sau khi writer commit.

Sau khi persist hàng outbox, `OutboxJpaWriter` gọi `OutboxRelayTrigger.requestPublish()`. Trigger đợi transaction commit xong rồi submit relay lên executor riêng — relay chạy ngay, không chờ poll cycle tiếp theo.

Một nudge cho mỗi transaction là đủ — relay sẽ claim cả lô. Nếu nudge thất bại (executor đầy, relay ném lỗi), poll 5 giây vẫn nhặt được. Nudge là tối ưu, poll là bảo đảm.

### 6.5. Kafka producer — gửi vào topic

`KafkaOutboxPublisher` nhận entity từ relay, gửi vào Kafka topic tương ứng (dựa trên trường `topic` trong bảng outbox). Khi Kafka trả kết quả (thành công hoặc lỗi), publisher cập nhật trạng thái hàng:

- **Thành công**: đổi status sang `SENT`, ghi `sent_at`.
- **Lỗi**: tăng `retry_count`, ghi `last_error`, đổi status về `PENDING` để relay nhặt lại ở lần quét tiếp theo.

Producer dùng `StringSerializer` cho cả key và value. Key là `payload_key` trong bảng outbox (thường là aggregate ID), đảm bảo các sự kiện của cùng một entity luôn vào cùng partition — giữ thứ tự.

### 6.6. Kafka consumer — nhận và xử lý

`KafkaConsumerConfig` dựng hai container factory: một cho String deserializer, một cho JSON deserializer. Cả hai đều dùng DLT và `setConcurrency(1)`.

**Chính sách lỗi** (String factory — factory mà cả hai consumer hiện tại đều dùng):

- **Retry theo cấp số nhân**: bắt đầu 1 giây, nhân đôi, tối đa 30 giây mỗi lần, bỏ cuộc sau tổng cộng 60 giây. Đủ cho chập mạng, không giữ partition cả buổi.
- **Danh sách lỗi không thử lại**: `MailPayloadException`, `MailTemplateException`, `IllegalArgumentException`. Payload JSON hỏng không bao giờ parse được — thử lại chỉ tổ chặn partition. Nó đi thẳng DLT.
- **DLT giữ nguyên partition**: thứ tự trong partition được bảo toàn cả ở hàng chết.
- **Commit offset sau khi đẩy DLT**: tránh consumer đọc lại bản ghi hỏng mãi mãi.

**Email consumer** (`MailKafkaConsumer`): làm ba bước — parse JSON thành `EmailPayload`, validate (toEmail, subject, htmlBody không rỗng), gửi qua SMTP. Nội dung email đã được render sẵn trước khi xếp hàng (tại IAM module), nên consumer không cần biết template hay ngôn ngữ. Email gửi cả bản text lẫn HTML — client thư không hiển thị được HTML vẫn đọc được.

**Audio consumer** (`SongProcessingConsumer`): nhận yêu cầu merge watermark/voice tag vào bài hát. FFmpeg chạy hàng phút mỗi bài, nên consumer nới `max.poll.interval.ms` lên 30 phút (mặc định 5 phút). Nếu không, Kafka sẽ coi consumer đã chết và rebalance partition — bài hát bị merge hai lần.

---

## 7. Luồng end-to-end — ví dụ: gửi email OTP khi đăng ký

```
   IAM Module                     shared-infrastructure                    Kafka
  ┌──────────┐    ┌──────────────────┐    ┌──────────────┐    ┌──────────────────┐
  │ register │───→│ OutboxEmail      │───→│ OutboxJpa    │───→│  outbox_events   │
  │ (201)    │    │ EnqueueListener  │    │ Writer       │    │  status=PENDING  │
  └──────────┘    └──────────────────┘    └──────┬───────┘    └────────┬─────────┘
                                                 │ nudge               │
                                          ┌──────▼───────┐            │
                                          │ OutboxRelay  │◄───────────┘
                                          │ Trigger /    │  poll 5s (fallback)
                                          │ Scheduler    │
                                          └──────┬───────┘
                                                 │ claim + send
                                          ┌──────▼───────┐    ┌──────────────────┐
                                          │ KafkaOutbox  │───→│ notification     │
                                          │ Publisher    │    │ .email.v1        │
                                          └──────────────┘    └────────┬─────────┘
                                                                       │
                                                                ┌──────▼───────┐
                                                                │ MailKafka    │
                                                                │ Consumer     │───→ SMTP
                                                                └──────────────┘
```

1. User đăng ký → IAM render email (subject, HTML, text) → phát `EmailEventRequested`.
2. `OutboxEmailEnqueueListener` serialize payload thành JSON → gọi `OutboxWriter` → INSERT vào `outbox_events` trong cùng transaction với user creation.
3. `OutboxJpaWriter` gọi `OutboxRelayTrigger.requestPublish()` → sau khi transaction commit, relay chạy ngay.
4. `OutboxRelayScheduler` claim hàng (lease 60 giây) → `KafkaOutboxPublisher` gửi vào topic `notification.email.v1`.
5. `MailKafkaConsumer` nhận message → parse → validate → gửi SMTP.
6. Nếu SMTP lỗi → retry (exponential backoff, tổng 60 giây) → nếu vẫn lỗi → DLT.
7. User nhận `201 Created` từ bước 1. Toàn bộ bước 2–6 là bất đồng bộ.

Hệ quả: nếu SMTP hỏng, user đã nhận `201` và thông báo "mã xác thực đã gửi", nhưng email không bao giờ tới. Đây là cái giá cố hữu của việc tách bất đồng bộ.

---

## 8. Cấu hình

```yaml
pwb.outbox:
  relay: { enabled: true, poll-interval-ms: 5000, batch-size: 20 }
  retry: { max-attempts: 3, backoff-seconds: [1, 5, 30] }
```

Kafka consumer (String factory):

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `ExponentialBackOff` initial interval | 1 giây | Khoảng cách retry đầu tiên |
| `ExponentialBackOff` multiplier | 2.0 | Nhân đôi mỗi lần |
| `maxInterval` | 30 giây | Trần mỗi lần retry |
| `maxElapsedTime` | 60 giây | Bỏ cuộc sau tổng cộng 60 giây |
| `concurrency` | 1 | Một luồng cho mỗi listener |
| `commitRecovered` | true | Commit offset sau khi đẩy DLT |

Audio consumer override:

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `max.poll.records` | 1 | Lấy một record mỗi lần poll |
| `max.poll.interval.ms` | 1.800.000 (30 phút) | Cho phép FFmpeg chạy lâu mà không bị Kafka coi là chết |
