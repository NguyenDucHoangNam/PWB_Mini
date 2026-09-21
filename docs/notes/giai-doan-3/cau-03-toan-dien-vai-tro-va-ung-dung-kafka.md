# Câu 03: Toàn Diện Vai Trò, Kiến Trúc & 2 Đường Ống Xử Lý Thực Chiến Của Apache Kafka Trong Dự Án

### ❓ Câu hỏi:
> *"Trong dự án `PWB_MiNi`, bạn sử dụng Apache Kafka cho những mục đích gì? Hãy phân tích chi tiết 2 đường ống sự kiện (Event Pipelines) trong hệ thống: xử lý âm thanh FFmpeg nặng và gửi email/thông báo. Bạn đã cấu hình Kafka Producer và Consumer như thế nào để đảm bảo tính sẵn sàng cao, xử lý lỗi với Dead Letter Topic (DLT), thiết lập cơ chế thử lại lũy tiến (Exponential Backoff), và giải quyết triệt để thảm họa Rebalance Storm khi worker chạy tác vụ dài hàng chục phút?"*

---

### 💡 Câu trả lời:

Trong dự án `PWB_MiNi`, Apache Kafka (phiên bản **Confluent Platform 7.6.0** chạy chế độ **KRaft**) không được sử dụng dàn trải, mà đóng vai trò là **Xương Sống Giao Vận Sự Kiện Bất Đồng Bộ (Event-Driven Asynchronous Backbone)**.

Kafka giải quyết 2 bài toán sống còn trong kiến trúc hệ thống:
1. **Tách rời luồng xử lý (Decoupling)**: Giải phóng ngay lập tức luồng HTTP của người dùng, không để các tác vụ nặng giam cầm Tomcat Worker Thread.
2. **Bộ đệm chịu tải và phân phối việc (Buffering & Work Distribution)**: Lưu trữ bền vững các yêu cầu xử lý khi tải tăng cao đột biến và phân phối công việc đồng đều cho các Worker chạy ngầm.

---

### 1. Hai (02) Đường Ống Sự Kiện Cốt Lõi Trong Mã Nguồn

```
[ HTTP Request từ Client ]
            │
            ▼ (Commit DB + Transactional Outbox)
     [ Outbox Relay ]
            │
            ▼ (Bắn message qua KafkaTemplate)
┌────────────────────────────────────────────────────────────────────────┐
│                        APACHE KAFKA BROKER                             │
├───────────────────────────────────┬────────────────────────────────────┤
│   TOPIC: voice.processing.v1      │   TOPIC: notification.email.v1     │
│   (Xử lý âm thanh nặng FFmpeg)    │   (Gửi Email & Thông báo ngầm)     │
└─────────────────┬─────────────────┴──────────────────┬─────────────────┘
                  │                                    │
                  ▼                                    ▼
       [ SongProcessingConsumer ]             [ MailKafkaConsumer ]
                  │                                    │
                  ▼                                    ▼
         [ FFmpeg Pipeline ]                  [ JavaMailSender ]
   (LUFS, Voice Tag, Render MP3)             (Gửi OTP, Reset Pass)
```

---

#### 🎵 Đường Ống 1: Xử Lý Âm Thanh Nặng Ngầm (Heavy Audio Processing Pipeline)

Đây là đường ống tiêu tốn nhiều năng lực tính toán CPU/RAM nhất của toàn bộ nền tảng:

* **Tên Topic**: `voice.processing.v1` (Cấu hình: **3 partitions**, **1 replica**; Topic lỗi đi kèm: `voice.processing.v1.DLT`).
* **Đầu phát (Producer)**:
  - Khi người dùng tạo bài hát và cấu hình ghép Voice Tag (nhãn âm thanh bản quyền):
  - Usecase [`SongUseCaseImpl.createSong`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/audio/src/main/java/com/pwb/audio/application/usecase/impl/SongUseCaseImpl.java#L451) lưu bài hát vào PostgreSQL với trạng thái ban đầu là `PROCESSING`, đồng thời chèn sự kiện vào bảng `outbox_events`.
  - Tiến trình Outbox Relay nhặt sự kiện và đẩy sang Kafka với:
    - **Message Key**: `songId` (được băm bằng thuật toán MurmurHash2 của Kafka, đưa toàn bộ sự kiện của cùng 1 bài hát vào **cùng 1 Partition duy nhất** để giữ thứ tự tuần tự tuyệt đối).
    - **Payload**: `SongProcessingRequested` (chứa `songId`, `userId`...).
* **Đầu nhận (Consumer)** — [`SongProcessingConsumer`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/audio/src/main/java/com/pwb/audio/infrastructure/processor/SongProcessingConsumer.java):
  - Lắng nghe topic với Group ID: `audio-song-processor`.
  - Gọi tiếp sang [`SongProcessorWorker`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/audio/src/main/java/com/pwb/audio/infrastructure/processor/SongProcessorWorker.java) để thực hiện quy trình 4 bước:
    1. Tải file âm thanh gốc từ AWS S3 về thư mục tạm trên đĩa.
    2. Chạy tiến trình **FFmpeg**: giải mã, đo đạc năng lượng âm thanh chuẩn LUFS, hạ âm lượng nhạc nền (Audio Ducking) và lồng ghép file Voice Tag tại các mốc thời gian đã định cấu hình.
    3. Nén file thành phẩm MP3 chất lượng cao và tải ngược lên AWS S3 (`audio/originals/`).
    4. Cập nhật bài hát sang trạng thái `READY` trong Database và phát thông báo realtime qua WebSocket STOMP tới trình duyệt của người dùng.

##### ⚡ Cấu hình "Sống Còn" chống thảm họa Rebalance Storm:
```java
@KafkaListener(
    topics = "${pwb.audio.processor.kafka.topic:voice.processing.v1}",
    groupId = "${pwb.audio.processor.kafka.group-id:audio-song-processor}",
    containerFactory = "kafkaListenerContainerFactory",
    properties = {
        "max.poll.records=1",                          // 👈 Chỉ nhặt ĐÚNG 1 bản ghi mỗi lần poll
        "max.poll.interval.ms=${...max-poll-ms:1800000}" // 👈 Nới lỏng thời gian chờ lên tới 30 PHÚT
    }
)
```
> **Tại sao bắt buộc phải có cấu hình này? (Câu hỏi phỏng vấn hóc búa)**:  
> - Mặc định của Kafka, `max.poll.interval.ms` là **5 phút (300.000ms)**. Nếu Consumer nhận một mẻ nhiều bản ghi hoặc bài hát quá dài, tiến trình FFmpeg chạy mất 6 phút mà Consumer Thread chưa kịp gọi lại hàm `poll()` tiếp theo.  
> - Kafka Broker sẽ phán đoán rằng: *"Worker này bị đơ/chết rồi!"* và lập tức kích hoạt **Rebalance Storm**: tước quyền sở hữu partition của Worker này giao cho một Worker khác chạy lại từ đầu!  
> - Hậu quả: Hai Worker cùng tải file, cùng render FFmpeg, tranh chấp CPU làm máy chủ sập vì cạn kiệt tài nguyên.  
> - **Giải pháp của chúng ta**: Đặt `max.poll.records = 1` (chỉ gánh 1 bài mỗi lần) và kéo giãn `max.poll.interval.ms` lên **30 phút (1.800.000ms)**, vượt xa thời gian timeout tối đa của tiến trình FFmpeg, triệt tiêu 100% nguy cơ Rebalance Storm!

---

#### 📧 Đường Ống 2: Gửi Email & Thông Báo Bất Đồng Bộ (Asynchronous Mail Pipeline)

Giải quyết bài toán trải nghiệm người dùng: Triệt tiêu hoàn toàn thời gian chờ đợi khi tương tác với máy chủ thư điện tử (SMTP):

* **Tên Topic**: `notification.email.v1` (Cấu hình: **3 partitions**, **1 replica**; Topic lỗi: `notification.email.v1.DLT`).
* **Đầu phát (Producer)**:
  - Khi người dùng Đăng ký tài khoản, Quên mật khẩu hoặc Yêu cầu gửi lại mã xác thực OTP:
  - Module `iam` kích hoạt [`OutboxEmailEnqueueListener`](file:///d:/Learning/Project/PWB_MiNi/Backend/shared/shared-infrastructure/src/main/java/com/pwb/infra/mail/api/OutboxEmailEnqueueListener.java) để đưa email vào bảng Outbox.
  - Outbox Relay đẩy sang Kafka với Message Key là địa chỉ `toEmail`.
  - **Lợi ích**: API Đăng ký tài khoản phản hồi thành công về cho người dùng chỉ sau **10 mili-giây**, hoàn toàn không bị chặn luồng bởi độ trễ kết nối mạng SMTP (vốn mất từ 1 đến 3 giây).
* **Đầu nhận (Consumer)** — [`MailKafkaConsumer`](file:///d:/Learning/Project/PWB_MiNi/Backend/shared/shared-infrastructure/src/main/java/com/pwb/infra/mail/consumer/MailKafkaConsumer.java):
  - Lắng nghe với Group ID: `pwb-mail-consumer`.
  - Đọc `EmailPayload`, chuẩn hóa định dạng HTML, nhúng inline logo CID `pwb-logo` và thực hiện gửi mail an toàn qua `JavaMailSender` (kết nối với SMTP Server hoặc AWS SES).

---

### 2. Ba (03) Cơ Chế Phòng Thủ & Khả Dụng Cao Của Kafka Trong Dự Án

Cấu hình chi tiết tại [`KafkaConsumerConfig`](file:///d:/Learning/Project/PWB_MiNi/Backend/shared/shared-infrastructure/src/main/java/com/pwb/infra/kafka/config/KafkaConsumerConfig.java):

```
[ Message lỗi khi xử lý ]
           │
           ▼ (Thử lại: Exponential Backoff)
┌─────────────────────────────────────────┐
│ Retry: 1s, 2s, 4s, 8s... (Tối đa 60s)  │
└────────────────────┬────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
 [ Thử lại thành công ]    [ Thất bại sau 60s HOẶC Lỗi Non-retryable ]
        │                         │
     (Commit)                     ▼
                    [ DeadLetterPublishingRecoverer ]
                                  │
                                  ▼
                    [ Đẩy sang Topic *.DLT ]
                    (Tránh nghẽn hàng đợi)
```

#### 2.1. Dead Letter Topic (DLT) — Chống Tắc Hàng Đợi (Poison Pill Protection)
- **Vấn đề**: Nếu có một message bị lỗi cấu trúc dữ liệu hoặc file âm thanh hỏng, Consumer đọc bị lỗi và ném Exception. Nếu cứ retry vô hạn tại chỗ, toàn bộ các message hợp lệ nằm phía sau sẽ bị **mắc kẹt vĩnh viễn (Poison Pill)**.
- **Giải pháp**:
  - Tích hợp `DeadLetterPublishingRecoverer`: Khi một message vượt quá số lần thử lại cho phép, hệ thống tự động bốc tách và xuất bản message đó sang **Dead Letter Topic** đối ứng với hậu tố `.DLT`:
    `voice.processing.v1.DLT` và `notification.email.v1.DLT`.
  - Message lỗi được lưu lại an toàn kèm toàn bộ Stack Trace để đội ngũ kỹ thuật điều tra sau (Post-mortem), trong khi hàng đợi chính tiếp tục xử lý các message khác mượt mà.

#### 2.2. Cơ Chế Thử Lại Lũy Tiến (Exponential Backoff Retry)
- Khi gặp sự cố mạng tạm thời (ví dụ SMTP server nghẽn mạng ngắn hạn):
  - Hệ thống sử dụng `ExponentialBackOff` với cấu hình: độ trễ ban đầu `1000ms`, hệ số nhân `2.0`, khoảng cách tối đa giữa các lần thử `30.000ms`, và tổng thời gian thử tối đa `60.000ms`.
  - Giúp hệ thống tự phục hồi mà không làm bão request (Retry Storm) lên các dịch vụ phụ trợ.

#### 2.3. Phân Loại Lỗi Thông Minh (Non-Retryable Exceptions)
- Trong `KafkaConsumerConfig`:
  ```java
  handler.addNotRetryableExceptions(
      com.pwb.infra.mail.consumer.MailPayloadException.class,
      com.pwb.infra.mail.consumer.MailTemplateException.class,
      IllegalArgumentException.class
  );
  ```
- Nếu message bị lỗi do cú pháp JSON hỏng (`MailPayloadException`) hoặc thiếu địa chỉ người nhận / thiếu template (`MailTemplateException`), việc thử lại 100 lần cũng chắc chắn sẽ thất bại.
- Do đó, hệ thống cấu hình để **bỏ qua việc retry và đẩy thẳng sang DLT ngay lập tức**, tiết kiệm 100% tài nguyên xử lý vô ích.

---

### 3. Bảng Tổng Hợp Thông Số & Cấu Hình Kafka Trong Toàn Dự Án

| Thuộc Tính | Topic Âm Thanh (`voice.processing.v1`) | Topic Email (`notification.email.v1`) |
| :--- | :--- | :--- |
| **Nhiệm vụ** | Render âm thanh FFmpeg & ghép Voice Tag | Gửi email kích hoạt, OTP, đặt lại mật khẩu |
| **Số Partitions** | 3 partitions | 3 partitions |
| **Replication Factor**| 1 replica (môi trường single VPS) | 1 replica |
| **Message Key** | `songId` (UUID của bài hát) | `toEmail` (Địa chỉ email người nhận) |
| **Consumer Group** | `audio-song-processor` | `pwb-mail-consumer` |
| **Tần suất Poll** | `max.poll.records = 1` | Mặc định (Batch poll) |
| **Thời gian Poll Timeout** | `max.poll.interval.ms = 1.800.000ms` (30 phút) | Mặc định (300.000ms = 5 phút) |
| **Topic Lỗi (DLT)** | `voice.processing.v1.DLT` | `notification.email.v1.DLT` |
| **Chiến lược Retry** | Exponential Backoff (1s $\rightarrow$ 60s) | Exponential Backoff (1s $\rightarrow$ 60s) |

---

### 4. Thực Tế Triển Khai vs Khả Năng Scale-out Mở Rộng

- **Thực tế Production hiện tại**:
  - Chạy image `confluentinc/cp-kafka:7.6.0` ở chế độ **KRaft (không cần ZooKeeper)** trên 1 máy chủ VPS duy nhất qua Docker Compose với giới hạn bộ nhớ cứng: `mem_limit: 1g`.
  - Cấu hình này giúp tiết kiệm tối đa RAM trên VPS 7.6GB nhưng vẫn đảm bảo độ bền bỉ dữ liệu qua volume `kafka_data`.
- **Sẵn sàng mở rộng (Cloud & Scale-out Ready)**:
  - Cả 2 topic đều được cấu hình sẵn **3 Partitions**.
  - Khi lưu lượng xử lý bài hát tăng cao, chúng ta chỉ việc bật thêm 2 worker instance nữa (thành 3 instances) trong Consumer Group `audio-song-processor`. Kafka sẽ tự động giao mỗi partition cho 1 worker xử lý độc lập, **nâng thông lượng xử lý FFmpeg lên gấp 3 lần ngay lập tức mà không cần cấu hình lại Topic!**

---

### 💡 Tóm tắt 30 giây để trả lời phỏng vấn:
> *"Trong dự án PWB_MiNi, Apache Kafka là xương sống giao vận sự kiện bất đồng bộ phục vụ 2 đường ống cốt lõi:
> 1. **Đường ống âm thanh (`voice.processing.v1`)**: Chuyển các tác vụ render FFmpeg nặng ra khỏi luồng HTTP; cấu hình đặc biệt `max.poll.records=1` và `max.poll.interval.ms=30 phút` để triệt tiêu hoàn toàn thảm họa Rebalance Storm.
> 2. **Đường ống email (`notification.email.v1`)**: Tách rời việc gửi thư SMTP, giúp API đăng ký và cấp OTP phản hồi tức thì trong 10 mili-giây.
> Toàn bộ sự kiện được xuất bản an toàn qua **Transactional Outbox**, bảo đảm thứ tự tuần tự **Strict FIFO** theo Aggregate ID, và có lưới an toàn **Dead Letter Topic (DLT)** kết hợp **Exponential Backoff Retry** chống nghẽn hàng đợi."*
