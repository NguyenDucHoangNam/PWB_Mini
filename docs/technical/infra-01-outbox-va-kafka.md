# Hạ tầng — Outbox & Kafka

> `shared-infrastructure`: `infra/outbox`, `infra/kafka`, `infra/mail`
> Đây là **đường B** trong [bản đồ hệ thống](00-ban-do-he-thong.md) — xương sống bất đồng bộ của cả hệ thống.

---

## 1. Bài toán: hai lệnh ghi, một sự thật

Một use case cần làm hai việc: lưu vào database, và báo cho phần khác của hệ thống. Hai việc ấy nằm ở hai nơi khác nhau, và không có transaction nào bao được cả hai.

```
save() rồi gửi Kafka   →  gửi xong mà transaction rollback: đã báo một chuyện chưa từng xảy ra
commit rồi gửi Kafka   →  tiến trình chết ở giữa: chuyện đã xảy ra mà không ai biết
```

Không có thứ tự nào đúng. **Outbox pattern** thoát khỏi thế lưỡng nan bằng cách biến việc thứ hai thành một lệnh ghi database:

> Ghi ý định gửi vào **cùng transaction** với thay đổi nghiệp vụ. Một tiến trình khác đọc bảng đó và gửi thật.

Hai chuyện giờ hoặc cùng xảy ra, hoặc cùng không.

**Không use case nào trong hệ thống này gửi thẳng vào Kafka** — kiểm chứng được ở [bản đồ §6](00-ban-do-he-thong.md): bảng outbox có đúng ba loại sự kiện, phủ kín ba đường bất đồng bộ.

---

## 2. Bảng `outbox_events`

```
id · event_id · event_type · aggregate_type · aggregate_id
topic · payload_key · payload (jsonb) · headers (jsonb)
status · retry_count · next_attempt_at · last_error
created_at · sent_at · version · lease_until
```

Ba index, trong đó một **partial index**:

```
idx_outbox_status_next_attempt   (status, next_attempt_at)
idx_outbox_lease_until           (lease_until) WHERE status = 'PROCESSING'
idx_outbox_topic                 (topic)
```

Partial index chỉ phủ hàng `PROCESSING` — nhóm nhỏ nhất và duy nhất mà truy vấn thu hồi lease quan tâm. Index đầy đủ trên `lease_until` sẽ phải chứa cả những hàng `SENT` đã tích luỹ, mà chúng không bao giờ được hỏi tới.

`version` là khoá lạc quan — chuẩn bị sẵn cho việc nhiều worker cùng chạy.

---

## 3. Relay: nhận việc bằng lease

`OutboxRelayScheduler` chạy mỗi **5 giây**, và đây là phần đáng đọc nhất:

```java
List<UUID> reclaimed = repository.reclaimExpiredLease(batchSize, now, nextAttempt, leaseUntil);
int remaining = Math.max(0, batchSize - reclaimed.size());
List<UUID> fresh = remaining == 0 ? List.of()
        : repository.claimBatch(remaining, now, nextAttempt, leaseUntil);
```

**Thu hồi lease hết hạn trước, nhận việc mới sau** — và tổng không vượt `batchSize`.

Lease (`lease_until`, mặc định **60 giây**) là cách giải bài toán: một worker nhận việc rồi chết thì hàng đó mắc kẹt ở `PROCESSING` mãi mãi. Với lease, sau 60 giây worker khác — hoặc chính nó sau khi khởi động lại — nhặt lại được.

Thứ tự "thu hồi trước" quan trọng: việc cũ bị bỏ rơi được ưu tiên hơn việc mới. Ngược lại thì một dòng chảy sự kiện liên tục sẽ khiến hàng bị bỏ rơi không bao giờ tới lượt.

### 3.1. Gửi sau khi commit — lại là khuôn mẫu ấy

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { submit.run(); }
    });
} else {
    submit.run();
}
```

Bản thân việc nhận lease là một transaction. Gửi Kafka **trước khi** transaction đó commit thì consumer có thể xử lý xong và trả lời trước khi hàng kịp được đánh dấu `PROCESSING` — hàng đó sẽ được nhận lại và gửi lần nữa.

Đây là **lần thứ ba** cùng một khuôn mẫu xuất hiện trong hệ thống: [publisher của Live Room](13-realtime-stomp.md), [sổ session STOMP](13-realtime-stomp.md), và ở đây.

`try/catch` bọc từng sự kiện trong vòng lặp — một payload hỏng không chặn cả lô, cùng nguyên tắc với [scheduler của Live Room](liveroom-07-scheduler.md).

Cấu hình:

```yaml
pwb.outbox:
  relay: { enabled: true, poll-interval-ms: 5000, batch-size: 20 }
  retry: { max-attempts: 3, backoff-seconds: [1, 5, 30] }
```

---

## 4. Ghi vào outbox: qua sự kiện Spring, không gọi thẳng

`infra/outbox/api` có `OutboxEnqueueHelper`, `OutboxEnqueueListener`, `OutboxEnqueueRequested`, `OutboxWriter`.

Người gọi phát một **sự kiện ứng dụng của Spring**; listener biến nó thành một hàng. Thấy rõ ở đường email ([iam-01 §7](iam-01-dang-ky-va-otp.md)):

```java
applicationEventPublisher.publishEvent(new EmailEventRequested(payload));
```

Vì sao thêm một tầng gián tiếp: module nghiệp vụ **không phải phụ thuộc vào outbox**. IAM chỉ biết "tôi muốn gửi email"; việc điều đó được thực hiện qua outbox → Kafka → SMTP là chuyện của hạ tầng.

Có **hai** cài đặt của `OutboxWriter`: `OutboxJpaWriter` (thật) và `LoggingOutboxWriter` (chỉ ghi log). Cái sau cho phép chạy hệ thống mà không cần bảng outbox — hữu ích khi test.

---

## 5. Kafka: một cấu hình, ba consumer

`KafkaConsumerConfig` dựng hai container factory (một cho `String`, một cho JSON), cả hai cùng chính sách lỗi:

```java
DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
backOff.setMaxInterval(30_000L);
backOff.setMaxElapsedTime(60_000L);

DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
handler.addNotRetryableExceptions(
        MailPayloadException.class,
        MailTemplateException.class,
        SearchIndexPayloadException.class,
        IllegalArgumentException.class);
handler.setCommitRecovered(true);
factory.setConcurrency(1);
```

Năm quyết định trong khối này:

**Thử lại theo cấp số nhân, tổng cộng 60 giây.** Bắt đầu 1 giây, nhân đôi, tối đa 30 giây mỗi lần, bỏ cuộc sau 60 giây. Đủ để vượt qua một lần chập mạng, không đủ để giữ một partition cả buổi.

**Danh sách lỗi không thử lại.** Đây là phần quan trọng nhất. Một payload JSON hỏng sẽ **không bao giờ** parse được, nên thử lại 60 giây chỉ tổ chặn partition. Nó đi thẳng DLT.

Chú ý: danh sách này **liệt kê tên lớp cụ thể của từng module** — `MailPayloadException`, `SearchIndexPayloadException`. Cấu hình chung phải biết tên ngoại lệ của từng consumer. Thêm consumer mới có lỗi không-thử-lại-được thì phải sửa file này, và **quên là lỗi đó sẽ được thử lại vô ích rồi mới vào DLT sau 60 giây**.

**`.DLT` giữ nguyên partition.** Thứ tự trong một partition được bảo toàn cả ở hàng chết.

**`setCommitRecovered(true)`** — sau khi đẩy vào DLT thì commit offset. Không có nó, consumer đọc lại đúng bản ghi đó mãi mãi.

**`setConcurrency(1)`** — một luồng cho mỗi listener. Với `voice.processing.v1` thì bắt buộc (FFmpeg ăn CPU, xem [audio-04 §3](audio-04-pipeline-xu-ly.md)); với email và index thì đây là giới hạn thông lượng chưa cần gỡ.

### 5.1. Ba consumer, ba tính cách

| Topic | Consumer group | Đặc thù |
|---|---|---|
| `notification.email.v1` | `pwb-mail-consumer` | Gửi SMTP; nội dung đã kết xuất sẵn |
| `search.index.v1` | `pwb-search-indexer` | Ghi Elasticsearch; trễ là chấp nhận được |
| `voice.processing.v1` | `audio-song-processor` | **Chạy hàng phút**; phải nới `max.poll.interval.ms` lên 30 phút |

Cái thứ ba là ngoại lệ duy nhất không dùng mặc định — lý do ở [audio-04 §3](audio-04-pipeline-xu-ly.md).

---

## 6. Consumer email — ví dụ đầy đủ nhất

`MailKafkaConsumer` làm đúng ba bước: parse → validate → gửi, và mỗi bước ném một loại lỗi khác nhau:

```java
private void validatePayload(EmailPayload payload) {
    if (payload == null) throw new MailPayloadException("Mail payload is null");
    if (payload.toEmail() == null || payload.toEmail().isBlank())
        throw new MailTemplateException("Mail payload toEmail is blank");
    if (payload.subject() == null || payload.subject().isBlank())
        throw new MailTemplateException("Mail payload subject is blank (template not rendered?)");
    if (payload.htmlBody() == null || payload.htmlBody().isBlank())
        throw new MailTemplateException("Mail payload htmlBody is blank (template not rendered?)");
}
```

Hai câu `(template not rendered?)` chỉ thẳng vào nguyên nhân thường gặp. Nội dung email được **kết xuất trước khi xếp hàng** ([iam-01 §7](iam-01-dang-ky-va-otp.md)), nên `subject` rỗng nghĩa là việc kết xuất đã hỏng từ lúc gửi đi, không phải consumer hỏng.

Cả hai lỗi đều nằm trong danh sách không-thử-lại: một payload thiếu tiêu đề sẽ không tự mọc tiêu đề ở lần thử sau.

Email gửi **cả bản text lẫn HTML** (`helper.setText(text, html)`) — client thư nào không hiển thị được HTML vẫn đọc được.

### 6.1. Hệ quả cần biết: người dùng không bao giờ biết email hỏng

Chuỗi `register` → 201 → outbox → Kafka → SMTP hỏng → DLT hoàn toàn **không có đường phản hồi ngược**. Người dùng nhận `201 Created` và câu "Mã xác thực đã được gửi", rồi ngồi chờ một email không bao giờ tới.

Đây là cái giá cố hữu của việc tách bất đồng bộ. Chữa được bằng cách theo dõi DLT và cảnh báo — hiện chưa có.

---

## 7. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Outbox cho **mọi** đường bất đồng bộ | Gửi thẳng Kafka | Không có sự kiện ma, không mất sự kiện | Trễ tới 5 giây; thêm một bảng lớn dần |
| Ghi qua sự kiện Spring | Gọi thẳng `OutboxWriter` | Module nghiệp vụ không phụ thuộc outbox | Thêm một tầng gián tiếp khi đọc code |
| Lease 60 giây | Không có lease | Worker chết không làm kẹt hàng vĩnh viễn | Hàng có thể gửi hai lần nếu worker chỉ chậm |
| Thu hồi lease trước, nhận mới sau | Ngược lại | Việc bị bỏ rơi không bị bỏ đói | Lô có thể toàn việc cũ |
| Gửi sau khi commit lease | Gửi ngay | Không gửi trùng do consumer chạy trước khi lease được ghi | Thêm một lớp hoãn |
| Partial index cho `lease_until` | Index đầy đủ | Chỉ phủ nhóm hàng thật sự được hỏi | Phải nhớ điều kiện khi đổi truy vấn |
| Backoff 1s→30s, bỏ cuộc sau 60s | Thử lại lâu hơn | Chập mạng thì qua được; hỏng thật thì không giữ partition | Sự cố dài hơn 60 giây đẩy mọi thứ vào DLT |
| Danh sách lỗi không thử lại | Thử lại mọi lỗi | Payload hỏng không chặn partition | Cấu hình chung phải biết tên lớp của từng module |
| `.DLT` giữ nguyên partition | Một partition | Thứ tự được bảo toàn ở hàng chết | — |
| `concurrency = 1` | Nhiều luồng | Bắt buộc cho FFmpeg | Email và index cũng bị giới hạn theo |
| Kết xuất email trước khi xếp hàng | Kết xuất lúc gửi | Consumer không cần biết template hay ngôn ngữ | Payload lớn; sửa template không ảnh hưởng thư đang chờ |

---

## 8. Tự kiểm chứng

**Xem ba đường bất đồng bộ trong một truy vấn:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT event_type, aggregate_type, topic, status, count(*) FROM outbox_events GROUP BY 1,2,3,4 ORDER BY 5 DESC;"
```

**Bắt một hàng trong lúc đang xử lý** — chạy liên tục trong khi tạo một bài hát có voice tag:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT event_type, status, retry_count, lease_until, next_attempt_at FROM outbox_events WHERE status <> 'SENT';"
```

Trong tối đa 5 giây sẽ thấy hàng đi từ `PENDING` → `PROCESSING` (có `lease_until`) → `SENT`.

**Thấy outbox cứu sự kiện khi Kafka chết** — thí nghiệm thuyết phục nhất của file:

```bash
docker stop pwb-kafka
```

Đăng ký một tài khoản mới. Request vẫn **thành công** (201). Xem bảng: hàng `EmailPersisted` ở `PENDING`/`PROCESSING` với `retry_count` tăng dần và `last_error` có nội dung. Bật lại:

```bash
docker start pwb-kafka
```

Trong vài chục giây hàng chuyển `SENT` — **sự kiện không mất**.

**Xem hàng chết:**

```bash
docker exec pwb-kafka kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic notification.email.v1.DLT
```

**Xem độ trễ của từng consumer group:**

```bash
docker exec pwb-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups | head -20
```

Cột `LAG` là số bản ghi chưa xử lý.

---

## 9. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Không ai theo dõi DLT** | Sự kiện vào hàng chết là mất hẳn, không cảnh báo, không có công cụ phát lại — mục 6.1 |
| Bảng `outbox_events` không được dọn | Hàng `SENT` giữ vĩnh viễn; chưa có scheduler nào xoá |
| Trễ tối thiểu 5 giây | Chu kỳ quét; sự kiện cần nhanh hơn phải đi đường realtime |
| Thông lượng 20 hàng / 5 giây | Khoảng 4 sự kiện/giây; đủ hiện tại, không đủ cho tải lớn |
| `concurrency = 1` cho mọi consumer | Email và index bị giới hạn theo nhu cầu của FFmpeg — mục 5 |
| Danh sách lỗi không-thử-lại phải cập nhật thủ công | Quên là chịu 60 giây thử lại vô ích — mục 5 |
| Nhiều instance thì relay chạy trùng | Lease giảm nhẹ nhưng chưa có test; cùng vấn đề với [scheduler Live Room](liveroom-07-scheduler.md) |
| Không đo được | Không có chỉ số cho độ sâu hàng đợi, tỉ lệ thử lại, tuổi hàng cũ nhất |
