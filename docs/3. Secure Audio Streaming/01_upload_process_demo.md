# 01. Tải lên & Xử lý Nhạc Gốc (Upload & Process Demo)

Tài liệu đặc tả A-Z quy trình tải lên tệp tin nhạc gốc chất lượng cao (.wav, .flac, .mp3), cơ chế cấp quyền tải lên trực tiếp S3 (Pre-signed Upload URL) và quy trình xử lý bất đồng bộ (FFmpeg Async Worker) thực hiện đóng dấu bản quyền, mã hóa AES-128 HLS và trích xuất dữ liệu hình sóng (Waveform).

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Producer (Music Producer - tài khoản nâng cấp `ROLE_USER_PRO`).
*   **Quy trình tóm tắt**:
    1.  Producer kéo thả file âm thanh gốc vào vùng tải lên, nhập tiêu đề và chọn cấu hình Voice Tag đóng dấu bản quyền.
    2.  Frontend yêu cầu Backend sinh liên kết tải lên trực tiếp S3 (S3 Pre-signed Upload URL) nhằm bỏ qua máy chủ Backend (tránh nghẽn băng thông).
    3.  Frontend đẩy tệp trực tiếp lên S3 bucket 100% Private. Sau khi thành công, gọi API xác nhận lên Backend.
    4.  Backend phản hồi ngay lập tức trạng thái `202 Accepted` (PROCESSING) và đẩy tác vụ vào hàng đợi xử lý ngầm (Async Background Worker).
    5.  Tác vụ nền tải file gốc về vùng đệm, ghép Voice Tag bằng thuật toán nén dìm âm lượng (Sidechain Ducking), cắt thành các đoạn luồng HLS mã hóa AES-128, tính toán dữ liệu waveform và tải trả lại S3.
    6.  Hệ thống cập nhật trạng thái bản nhạc thành `ACTIVE` để sẵn sàng chia sẻ.

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Ràng buộc Tệp tải lên (File Constraints)
*   **Định dạng được chấp nhận**: `.wav`, `.flac`, `.mp3` (với file `.mp3` bắt buộc bitrate tối thiểu 320kbps).
*   **Giới hạn dung lượng**: Tối đa **200MB / file** để bảo toàn tài nguyên lưu trữ và giới hạn thời gian xử lý của FFmpeg (phát hiện lỗi tại Frontend trước khi upload).

#### B. Quy trình Tải lên bảo mật S3 (Pre-signed Upload URL)
*   **Không upload qua Backend**: Client tuyệt đối không gửi file nhị phân lớn qua máy chủ Spring Boot. Backend chỉ chịu trách nhiệm sinh Pre-signed URL (thời hạn sống **60 giây**).
*   **Private Bucket**: S3 Bucket lưu trữ file gốc bắt buộc cấu hình block public access 100%. Tên tệp lưu trên S3 được thay thế bằng chuỗi UUID ngẫu nhiên để tránh tấn công rò quét IDOR.
*   **Ép buộc Content-Type (MIME-Type Bypass Prevention)**: Khi client yêu cầu sinh URL, Backend bắt buộc phải cấu hình tham số `ContentType` trong AWS S3 SDK (ví dụ: `audio/wav`, `audio/mpeg` cho mp3, `audio/flac` cho flac). AWS S3 sẽ tự động ký Header này vào chữ ký số của URL. Khi thực hiện tải lên bằng HTTP PUT, Client bắt buộc phải truyền Header `Content-Type` trùng khớp tuyệt đối, nếu không S3 sẽ từ chối tải lên, ngăn ngừa hành vi tải file độc hại (.exe, script) lên Cloud.
*   **Chính sách dọn dẹp tệp mồ côi (S3 Lifecycle Policy)**: Khi client tải tệp lên S3 thành công nhưng bị rớt mạng/crash trình duyệt trước khi gọi API `confirm-upload`, tệp gốc sẽ nằm kẹt lại vĩnh viễn trên S3 gây phình to chi phí. Hệ thống cấu hình quy tắc S3 Lifecycle Policy cho thư mục tạm `original/` tự động xóa vĩnh viễn các Object có tuổi đời quá 24 giờ kể từ thời điểm khởi tạo, bảo vệ hạ tầng khỏi rác lưu trữ.
*   **Ràng buộc Ownership s3Key — chống Path Traversal & IDOR (CRITICAL)**: Khi Backend sinh Pre-signed URL, đồng thời ghi nhận mapping `s3Key → {userId, expectedSizeBytes, expectedContentType, issuedAt}` vào Redis key `demo:upload:claim:{s3Key}` (TTL **2 giờ**). Mục đích:
    *   Tại `confirm-upload`, Backend **bắt buộc** phải kiểm tra mapping còn tồn tại và `userId` trùng khớp với user đang gọi API. Nếu không khớp → trả `INVALID_S3_KEY_OWNER` (403 Forbidden). Ngăn chặn kẻ tấn công gửi `s3Key` của user khác (hoặc brute-force UUID) để chiếm đoạt file hoặc xử lý nhầm.
    *   `expectedSizeBytes` dùng để verify `s3.headObject(s3Key).size()` trùng khớp (xem mục 2.1 bước 2.5).
    *   `expectedContentType` dùng để Worker ffprobe verify định dạng thực sự (xem mục C1 bước 3).
    *   Mapping tự xóa sau khi `confirm-upload` thành công (`DEL demo:upload:claim:{s3Key}`).
*   **Tag-based Lifecycle — chống xóa nhầm file đã confirm (Tag-based Retention)**: S3 Lifecycle Policy chỉ áp dụng cho các Object **CHƯA** có tag `confirmed=true`. Sau khi `confirm-upload` thành công, Worker đính tag `confirmed=true` lên Object để vô hiệu hóa rule lifecycle 24h. Các file đã confirmed sẽ được di chuyển sang prefix `original/confirmed/{userId}/{uuid}.{ext}` bằng một `COPY` operation nội bộ (không tốn download/upload vì S3 thực hiện server-side), rồi xóa key cũ. Vòng đời của file confirmed do Demo lifecycle (docs 01 mục 1.2.F sắp tới) quản lý, không do S3 auto-delete 24h.
*   **Worker truy cập S3 — bắt buộc IAM Role (CRITICAL)**: Worker pull file gốc từ S3 để xử lý FFmpeg **không được** dùng Pre-signed GET URL (vì pre-signed URL có thể rò rỉ qua log, có TTL dài, và phụ thuộc access key). Worker phải dùng **IAM Role liên kết với instance** (EC2 Instance Profile / EKS IRSA / ECS Task Role) để gọi trực tiếp `S3.GetObject` thông qua AWS SDK default credential provider chain. IAM Policy của role chỉ được phép `s3:GetObject` + `s3:PutObject` trên prefix `original/confirmed/{callerRoleTag}/*` và `stream/*`. Tuyệt đối cấm nhúng `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` vào biến môi trường container — sai phạm dẫn tới rotate key khắp hệ thống.
*   **Quota tổng theo User — chống Storage Cost DoS**:
    *   **Số lượng Demo ACTIVE**: mỗi Producer tối đa **20 demo ở trạng thái `ACTIVE`** cùng lúc. Vượt → trả `DEMO_QUOTA_EXCEEDED` (403 Forbidden).
    *   **Tổng dung lượng Demo ACTIVE**: tối đa **5 GB / user** (tính tổng `original_s3_key` size của các demo ACTIVE). Vượt → trả `AUDIO_QUOTA_EXCEEDED` (403 Forbidden).
    *   Backend check quota **ngay tại bước `confirm-upload`** (sau khi `HeadObject` thành công) — không cho phép tích lũy file mồ côi nhằm vượt quota.
    *   Quota không áp dụng cho `PROCESSING` (chỉ tính khi `ACTIVE`). Cleanup job (`PROCESSING` > 24h → `FAILED`) đảm bảo các job kẹt không chiếm quota vĩnh viễn.
*   **Rate Limit theo User — chống spam sinh URL mồ côi**: Bên cạnh IP rate limit (mục 1.4), Backend còn áp dụng:
    *   `/presigned-upload-url`: tối đa **10 requests / giờ / userId** (ngoài IP limit).
    *   `/confirm-upload`: tối đa **30 requests / phút / userId** (ngoài IP limit).

#### C1. Quyết định triển khai Worker FFmpeg (Java trong cùng Monorepo)

Để đồng bộ dependency (chia sẻ Entity `Demo`, `AudioProcessingJob`, dùng chung cấu hình Redis/S3) và giữ tính nhất quán kiến trúc lớp (Layered Architecture) trong cùng module backend, hệ thống triển khai Worker FFmpeg bằng **Java trong cùng codebase Monorepo Backend**, gọi tới CLI `ffmpeg` qua `ProcessBuilder`. Worker chạy trong cùng JVM với main application và lắng nghe Kafka topic `audio-processing-events` qua annotation `@KafkaListener`.

Lý do chốt Java:
- Chia sẻ JPA Entity `Demo` + `AudioProcessingJob` cùng module Audio (không cần serialization phức tạp).
- Cùng dự án backend, không cần tách codebase → dễ bảo trì và tận dụng chung cấu hình.
- Triển khai đơn giản vì `ffmpeg` binary đã có sẵn trong Docker image `pwb-backend`.
- Không phải bảo trì 2 codebase (Java + Python/Go).

Future expansion: tách Worker thành module Maven riêng (`audio-worker.jar`) chạy độc lập khỏi Main API nhưng vẫn dùng chung source code base vẫn khả thi mà không cần tách codebase.

### C. Quy trình Xử lý Nhạc nền (Async Audio Processing Pipeline) & Tránh Nghẽn CPU
Để tránh thắt nút cổ chai và sập server do CPU chạm ngưỡng 100% khi chạy FFmpeg đồng thời với Live Room (FFmpeg CPU Starvation):
*   **Tách biệt Kiến trúc (Logical Worker Pool)**: Khi Backend nhận yêu cầu xác nhận tải lên (`confirm-upload`), nó ghi nhận trạng thái `PROCESSING` vào bảng `demos` và tạo bản ghi `audio_processing_jobs`, đồng thời phát sự kiện `audio-processing-events` lên **Apache Kafka**. Java Worker trong cùng module nghe sự kiện để xử lý:
    **Bước 0 — xác thực định dạng tệp bằng ffprobe trước khi xử lý (Anti-Spoofing)**: Tệp tải lên có thể đã qua Content-Type signature đúng `audio/wav` nhưng thực chất là file `.exe` hoặc PDF. Ngay khi tải file về bộ đệm cục bộ, Worker bắt buộc phải chạy `ffprobe -v error -show_streams -show_format` để xác minh:
    *   Có đúng 1 audio stream (không có video stream ẩn, không có attached file).
    *   Codec nằm trong whitelist: `pcm_s16le / pcm_s24le / pcm_s32le / flac / mp3`.
    *   `sample_rate` ∈ {22050, 44100, 48000, 88200, 96000}.
    *   `bit_depth` ≥ 16 (nếu PCM).
    *   Duration hợp lệ (1s ≤ duration ≤ 30 phút).
    *   Không có attached cover art/image (tránh payload smuggling).
    *   Nếu bất kỳ check nào fail → set `demos.status = FAILED`, set `error_message = "INVALID_AUDIO_CONTENT: {lý_do}"`, ghi log ERROR, **xóa file S3** (`original/confirmed/{userId}/{uuid}.{ext}`) và cleanup job record. Không lãng phí CPU chạy sidechain trên file độc hại.
    1.  **Dập âm lượng Sidechain (Sidechain Auto-Ducking)**:
        *   Sử dụng bộ lọc nén `sidechaincompress` của FFmpeg. Khi Voice Tag phát đè lên nhạc nền, âm lượng của nhạc nền tại đúng phân khúc đó sẽ tự động giảm xuống `-12dB` (chỉ số nén), sau đó phục hồi ngay lập tức về mức ban đầu sau khi Voice Tag kết thúc. Việc này giúp giữ chất lượng nghe thử cao nhất ở các đoạn nhạc không có tag.
        *   *Câu lệnh FFmpeg Sidechain Ducking tham khảo*:
            ```bash
            ffmpeg -i original.wav -i voicetag.wav -filter_complex "[1]adelay=25000|25000[tag];[0][tag]sidechaincompress=threshold=0.03:ratio=12:attack=5:release=500[out]" -map "[out]" output_watermarked.wav
            ```
            *(Đoạn lệnh chèn tag tại giây thứ 25, dìm âm lượng nhạc nền xuống khi có tag)*.
    2.  **Mã hóa AES-128 & Phân đoạn HLS**:
        *   Cắt file sau khi đóng dấu thành các phân đoạn `.ts` nhỏ dài **6 giây**.
        *   Mỗi phân đoạn được mã hóa bằng thuật toán đối xứng AES-128 sử dụng khóa nhị phân 16-byte sinh ngẫu nhiên cho từng tệp demo. Khóa giải mã được đẩy lên Redis key `demo:key:{demoId}` (TTL 5 phút) với fallback lưu Postgres.
    3.  **Trích xuất Waveform**:
        *   FFmpeg phân tích biên độ đỉnh âm thanh của file để trích xuất **200 điểm số thực** (giá trị từ `0.0` đến `1.0`) đại diện cho biểu đồ hình sóng của tệp nhạc.
        *   Mảng dữ liệu này được lưu trực tiếp dưới dạng **mảng JSON** (`["0.10","0.20",...]`) trong cột Postgres kiểu `JSONB` để truy vấn nhanh và tương thích tự nhiên với response API (`"waveform": [0.12, 0.45, ...]`). Frontend nhận trực tiếp mảng số thực từ backend mà không cần parse CSV.
    4.  **Tải lên S3 & Cập nhật Trạng thái**:
        *   Tải các tệp phân đoạn `.ts` và playlist `.m3u8` lên S3 prefix `stream/{demoId}/`, đồng thời cập nhật `demos` thành `ACTIVE`, `hls_playlist_s3_key` thành key playlist. Nếu thành công, Worker publish sự kiện WebSocket `PROCESSING_COMPLETED` tới user sở hữu.
*   **Ràng buộc tài nguyên Node Worker (Concurrency Cap & Poison Pill Protection)**:
    *   **Giới hạn luồng chạy song song (Concurrency Cap)**: Để tránh cạn kiệt dung lượng đĩa tạm thời (Disk Space Starvation) khi tải nhiều file nhạc 200MB về xử lý FFmpeg song song trên 1 worker, hệ thống giới hạn cứng số lượng tác vụ giải mã đồng thời tối đa trên mỗi node Worker (ví dụ: tối đa 2 hoặc 3 luồng chạy FFmpeg đồng thời tùy theo số core CPU/Disk). Các tác vụ vượt ngưỡng sẽ nằm chờ an toàn trên Kafka queue.
    *   **Xử lý tin nhắn độc (Poison Pill via Dead Letter Queue - DLQ)**: Khi file upload bị lỗi cấu trúc nhị phân (Corrupted File) làm FFmpeg crash liên tục, hệ thống dễ rơi vào bẫy lặp vô hạn (Infinite Retry Loop) gây tắc nghẽn queue. Cấu hình Kafka Consumer tự động chuyển tin nhắn sang topic lỗi `audio-process-dlq` (Dead Letter Queue) sau 3 lần thử lại thất bại và ghi log mức `ERROR`.

#### E. Phân phối khóa giải mã an toàn (AES-128 Key Delivery Protection)
*   Để bảo mật nhạc demo chưa phát hành, tệp danh sách phân đoạn `.m3u8` chứa khóa giải mã dạng `#EXT-X-KEY:METHOD=AES-128,URI="..."` tuyệt đối không được trỏ thẳng tới link công khai.
*   **Giải pháp bảo vệ**: URI này bắt buộc phải trỏ về một endpoint an toàn của Backend: `/api/v1/demos/{demoId}/key`.
*   **Thắt chặt kiểm duyệt ngữ cảnh (Context-Based Authorization)**: Khi nhận yêu cầu tải khóa giải mã, Endpoint này trích xuất `userId` từ token JWT (chấp nhận Temporary JWT) và kiểm tra:
    *   *Ngoại lệ*: Nếu `userId` chính là tác giả bản nhạc (`owner_id == userId`), Backend lập tức cấp khóa.
    *   *Kiểm tra chéo ngữ cảnh*: Nếu là thành viên vãng lai, Backend thực hiện kiểm tra chéo $O(1)$ trên Redis Hash:
        1. Người dùng này bắt buộc phải là thành viên online thực tế trong phòng ảo (kiểm tra sự tồn tại của `userId` trong `room:members:{roomCode}`).
        2. Căn phòng đó hiện tại đang phát chính bài hát này (kiểm tra `room:playback:{roomCode} -> activeSourceId == demoId`).
    *   Nếu không thỏa mãn các điều kiện trên, Backend trả về lỗi `HTTP 403 Forbidden` nhằm triệt tiêu nguy cơ rò rỉ khóa giải mã để tải trộm nhạc ra ngoài.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

| API Request | Trường dữ liệu | Ràng buộc | Annotation (Backend) | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `PresignedUrlRequest` | `fileName` | Bắt buộc, không rỗng | `@NotBlank` | Tên tệp tin gốc (.wav, .flac, .mp3) |
| | `fileSize` | Bắt buộc, > 0 và <= 209,715,200 | `@NotNull`, `@Min(1)`, `@Max(209715200)` | Kích thước file tính bằng byte (Max 200MB) |
| `ConfirmUploadRequest`| `s3Key` | Bắt buộc, không rỗng | `@NotBlank` | Đường dẫn tệp tin gốc đã upload trên S3 |
| | `title` | Bắt buộc, tối đa 100 ký tự; phải qua HTML/SQL escape; KHÔNG chứa `<script>`, `javascript:`, event handlers | `@NotBlank`, `@Size(max=100)`, `@Pattern(regexp = "^[\\P{Cc}]{1,100}$")` (cấm control chars) | Tiêu đề hiển thị của bản nhạc — sanitize trước khi render ở Frontend, đồng thời set `Content-Security-Policy: default-src 'self'; script-src 'self'` để chống XSS khi title hiển thị trong WebSocket payload |
| | `watermarkInterval` | Bắt buộc, giá trị từ 10 đến 60 | `@Min(10)`, `@Max(60)` | Tần suất xuất hiện Voice Tag (giây) |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | IP Limit | User Limit | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST /api/v1/demos/presigned-upload-url` | **5 requests / phút / IP** | **10 requests / giờ / userId** | Ngăn chặn hành vi spam sinh URL rác làm quá tải S3 và vượt quota |
| `POST /api/v1/demos/confirm-upload` | **30 requests / phút / IP** | **30 requests / phút / userId** | Ngăn chặn spam confirm, kể cả khi attacker xoay vòng IP proxy |

> **Lưu ý**: Giới hạn User được áp dụng song song với giới hạn IP. Một request chỉ được coi là hợp lệ khi vượt qua **CẢ HAI** rate limit. Redis key riêng biệt: `rate_limit:ip:{ip}:{endpoint}` và `rate_limit:user:{userId}:{endpoint}`.

### 1.5. Quy tắc S3 Lifecycle chi tiết (Tag-based Retention)

| Object prefix | Tag `confirmed` | Quy tắc xóa | Mục đích |
| :--- | :--- | :--- | :--- |
| `original/{userId}/UUID.{ext}` *(chưa confirm)* | KHÔNG CÓ hoặc ≠ `"true"` | Xóa sau **24 giờ** kể từ LastModified | Dọn rác file user upload dở dang rồi bỏ |
| `original/confirmed/{userId}/UUID.{ext}` *(đã confirm)* | `"true"` | Xóa khi `demos.status` đổi sang `DELETED` (do Producer xóa hoặc cron cleanup) | File nghiệp vụ cần giữ vô thời hạn cho tới khi demo lifecycle kết thúc |
| `stream/{demoId}/` *(HLS segments + .m3u8)* | KHÔNG CÓ | Xóa sau **7 ngày** kể từ LastModified (cross-check với `demos.status='DELETED'` qua cron `DemoSegmentCleanupJob` chạy 04:00 UTC hằng ngày) | Cleanup HLS segments cho các demo đã xóa (orphaned segments từ demo đã DELETED) |

> **Cron Cleanup Job `DemoSegmentCleanupJob`** — chi tiết:
> 1. **Job ID**: `DemoSegmentCleanupJob`, registered qua `@Scheduled(cron = "0 0 4 * * *", zone = "UTC")` (4:00 AM UTC mỗi ngày).
> 2. **Leader election**: Dùng ShedLock với `lockName = "demo-segment-cleanup"`, `lockAtMostFor = 30m`, `lockAtLeastFor = 5m` để chỉ 1 instance chạy trên multi-node cluster.
> 3. **Algorithm**:
>    ```
>    FOR EACH demo WHERE status = 'DELETED' AND deleted_at < now() - 90 days:
>        ListObjectV2(prefix='stream/{demoId}/') → list segments
>        DeleteObject từng segment (batch 1000 keys/request)
>        Update demos.stream_s3_cleanup_at = NOW() WHERE id = demoId
>        Log INFO STREAM_SEGMENT_CLEANED {demoId, count, totalBytes}
>    ```
> 4. **Safety**: WHERE `deleted_at < now() - 90 days` (audit window 90 ngày theo docs 06 §B).
> 5. **Failure handling**: Nếu S3 trả AccessDenied hoặc RateLimit → log ERROR + retry lần sau (idempotent — chỉ xóa segment đã được processed nếu job restart).
> 6. **Monitoring**: Metric `orphaned_stream_segments_count` qua Micrometer → alert nếu > 1000 segments/slot.

Cấu hình Lifecycle rule (Terraform/CDK):

```json
{
  "Rules": [
    {
      "ID": "CleanupUnconfirmedUploads",
      "Status": "Enabled",
      "Filter": {
        "And": {
          "Prefix": "original/",
          "Tags": [{ "Key": "confirmed", "Value": "NOT_CONFIRMED" }]
        }
      },
      "Expiration": { "Days": 1 }
    },
    {
      "ID": "ExpireConfirmedObjects",
      "Status": "Enabled",
      "Filter": {
        "And": {
          "Prefix": "original/confirmed/",
          "Tags": [{ "Key": "confirmed", "Value": "true" }]
        }
      },
      "Expiration": { "Days": 365 }
    }
  ]
}
```

> **Quy trình confirm**: Tại `confirm-upload`, Backend thực hiện `PUT Object Tagging` với `{confirmed: "true"}` ngay sau khi `HeadObject` pass, rồi `COPY` sang prefix `original/confirmed/` và xóa object gốc trong cùng transaction logic. Lifecycle cũ còn dùng tag `confirmed=NOT_CONFIRMED` cho object chưa confirm (viết lúc `presigned-upload-url` để override sau).

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Tải lên & Xử lý Bất đồng bộ (Upload & Async Processing Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Producer
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Kafka as Kafka Broker
    participant S3 as S3 Private Bucket
    participant Worker as Java Audio Worker (Same JVM)
    participant DB as PostgreSQL

    Producer->>FE: Kéo thả file 'track.wav' & điền thông tin cấu hình
    FE->>FE: Xác thực Client: Kích thước < 200MB & Đuôi file hợp lệ
    
    FE->>BE: POST /api/v1/demos/presigned-upload-url (fileName, fileSize, contentType)
    BE->>BE: Xác thực quyền ROLE_USER_PRO
    BE->>BE: Gọi S3 SDK tạo Pre-signed URL (PUT, TTL 60s, Kèm Content-Type signature)
    BE-->>FE: HTTP 200 OK (uploadUrl, s3Key)
    
    Note over FE, S3: Frontend tải trực tiếp lên S3
    FE->>S3: HTTP PUT file 'track.wav' với Header Content-Type trùng khớp
    S3-->>FE: HTTP 200 OK (Tải lên S3 thành công)
    
    FE->>BE: POST /api/v1/demos/confirm-upload (ConfirmUploadRequest: s3Key, title, watermarkInterval)

    Note over BE, Redis: Bước 2.1 — Kiểm tra ownership & file size
    BE->>Redis: GET 'demo:upload:claim:{s3Key}' -> {userId, expectedSizeBytes, expectedContentType}
    alt Mapping không tồn tại hoặc userId không khớp
        BE-->>FE: HTTP 403 Forbidden (INVALID_S3_KEY_OWNER)
    else Mapping hợp lệ
        BE->>S3: HeadObject(s3Key) lấy Content-Length + Content-Type + ETag
        Note right of S3: Reject sớm nếu size thực tế khác expectedSizeBytes
        BE->>BE: Verify actualSize == expectedSizeBytes (1 ≤ size ≤ 209715200)
        alt Size không khớp hoặc vượt 200MB
            BE->>S3: DeleteObject(s3Key) (cleanup upload rogue)
            BE-->>FE: HTTP 400 Bad Request (FILE_SIZE_MISMATCH)
        else Size hợp lệ
            Note over BE, DB: Bước 2.2 — Bắt đầu Transaction (Serializable isolation) trước khi check quota
            Note right of BE: Quota check + insert Demo phải nằm CÙNG transaction để chống TOCTOU race condition (2 request song song cùng pass quota check trước khi insert)
            BE->>DB: BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE
            BE->>DB: SELECT COUNT(*) FROM demos WHERE owner_id = :uid AND status = 'ACTIVE' FOR UPDATE
            BE->>DB: SELECT COALESCE(SUM(file_size), 0) FROM demos WHERE owner_id = :uid AND status = 'ACTIVE' FOR UPDATE
            alt Vượt 20 demo ACTIVE hoặc tổng > 5GB
                BE->>DB: ROLLBACK
                BE->>S3: DeleteObject(s3Key)
                BE-->>FE: HTTP 403 Forbidden (DEMO_QUOTA_EXCEEDED / AUDIO_QUOTA_EXCEEDED)
            else Còn quota
                BE->>DB: INSERT INTO demos (status='PROCESSING', title, original_s3_key=s3Key, file_size=:size, ...)
                BE->>DB: INSERT INTO audio_processing_jobs (status='PENDING', attempt_count=0)
                BE->>DB: COMMIT
            end
                BE->>S3: TagObject(s3Key, {confirmed: "true"}) — vô hiệu hóa Lifecycle 24h
                BE->>S3: CopyObject(s3Key -> original/confirmed/{userId}/{uuid}.{ext})
                BE->>S3: DeleteObject(s3Key)
                BE->>Redis: DEL 'demo:upload:claim:{s3Key}'
                BE->>Kafka: Publish 'audio-processing-events' (demoId, s3Key=confirmed, voiceTagId, watermarkInterval)
                BE-->>FE: HTTP 202 Accepted (status='PROCESSING', demoId=UUID)
        
        FE->>BE: Subscribe WebSocket topic cá nhân: /user/queue/demos/status
        FE-->>Producer: Hiển thị trạng thái "Đang xử lý âm thanh..." (Waveform Skeleton nhấp nháy)
        
        Note over Worker, Kafka: Tiến trình xử lý Java Worker trong cùng JVM (lắng nghe Kafka)
        Worker->>Kafka: Consume 'audio-processing-events'
        Worker->>DB: Update audio_processing_jobs SET status='RUNNING', started_at=NOW()
        Worker->>Redis: SETNX 'audio:job:lock:{demoId}' = 'locked' (TTL 10m)
        Note over Worker, S3: Worker dùng IAM Role của instance (IRSA/EC2 Instance Profile) — KHÔNG dùng static AWS access key, KHÔNG dùng presigned URL nội bộ
        Worker->>S3: GetObject(original/confirmed/{userId}/{uuid}.{ext}) via IAM Role credentials (X-Ray trace)
        Note over Worker: Bước 3 — Xác thực định dạng tệp bằng ffprobe (Anti-Spoofing)
        Worker->>Worker: ffprobe -show_streams -show_format (verify audio stream + whitelist codec + sample rate + duration + no attached image)
        alt ffprobe fail (codec không hợp lệ / file không phải audio)
            Worker->>DB: UPDATE demos SET status='FAILED', error_message='INVALID_AUDIO_CONTENT: {reason}', updated_at=NOW() WHERE id=:demoId
            Worker->>DB: UPDATE audio_processing_jobs SET status='FAILED', completed_at=NOW(), last_error=:err WHERE id=:jobId
            Worker->>S3: DeleteObject(confirmed s3 key)
            Worker->>Redis: DEL 'audio:job:lock:{demoId}'
            Worker->>BE: Push WebSocket PROCESSING_FAILED (demoId, reason='INVALID_AUDIO_CONTENT')
        else ffprobe pass
            Worker->>Worker: Thực thi chèn Voice Tag đè lên nhạc (Sidechain Ducking)
            Worker->>Worker: Sinh khóa AES-128 ngẫu nhiên (16 bytes) và đẩy lên Redis 'demo:key:{demoId}'
        Worker->>Worker: Chạy FFmpeg băm luồng HLS (.m3u8 & các phân đoạn .ts đã mã hóa AES)
        Worker->>Worker: Chạy phân tích biên độ đỉnh trích xuất mảng Waveform 200 điểm float
        
        Worker->>S3: Tải các tệp phân đoạn .ts và file playlist.m3u8 lên thư mục 'stream/{demoId}/'
        
        Note over Worker, DB: Cập nhật cơ sở dữ liệu trong Transaction
        Worker->>DB: UPDATE demos SET status='ACTIVE', waveform_data=:wf JSONB, duration, sample_rate, hls_playlist_s3_key=:key, updated_at=NOW() WHERE id=:demoId
        Worker->>DB: UPDATE audio_processing_jobs SET status='COMPLETED', completed_at=NOW() WHERE id=:jobId
        Worker->>Worker: Xóa sạch tệp tạm cục bộ
        Worker->>Redis: DEL 'audio:job:lock:{demoId}'
        
        Worker->>BE: Gửi tín hiệu hoàn tất xử lý qua WebSocket
        BE->>FE: Broadcast WebSocket event: PROCESSING_COMPLETED (demoId, status='ACTIVE', waveform_data)
        FE-->>Producer: Ẩn Skeleton, vẽ hình sóng Waveform thật & hiện nút "Chia sẻ"
    end
```

---

## 💾 3. Database Schema (Thiết kế Cơ sở Dữ liệu)

### 3.1. Sơ đồ thực thể Bảng `demos` (PostgreSQL)

Bảng `demos` lưu trữ cấu hình file gốc và liên kết truyền phát của tệp demo:

```sql
CREATE TABLE demos (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    owner_id UUID NOT NULL,
    original_s3_key VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,                 -- Size file gốc (bytes) từ HeadObject lúc confirm-upload, dùng tính AUDIO_QUOTA và rate cost
    hls_playlist_s3_key VARCHAR(255) NULL, -- Sẽ được cập nhật sau khi xử lý HLS xong
    voice_tag_id UUID NULL,                -- FK tới bảng voice_tags.id (tham khảo docs 02)
    status VARCHAR(20) NOT NULL,           -- 'PROCESSING', 'ACTIVE', 'FAILED', 'DELETED' (DELETED = soft-delete sau khi Cascade Revoke hoàn tất, xem docs 06 §B mục Cascade)
    duration DECIMAL(10, 2) NULL,
    sample_rate INT NULL,
    format VARCHAR(10) NULL,
    waveform_data JSONB NULL,              -- Mảng 200 số thực dạng JSON (0.0..1.0)
    aes_key_encrypted BYTEA NULL,          -- Khóa AES-128 mã hoá bằng data key
    error_message TEXT NULL,               -- Lý do thất bại khi status='FAILED'
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_demos_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- Chỉ mục hỗ trợ tải thư viện demo của Producer nhanh chóng
CREATE INDEX idx_demos_owner_created ON demos(owner_id, created_at DESC);

-- Chỉ mục cho các query theo trạng thái (Worker quét PROCESSING khi khởi động lại)
CREATE INDEX idx_demos_status_updated ON demos(status, updated_at) WHERE status IN ('PROCESSING', 'FAILED');

-- Chỉ mục hỗ trợ quota check tại confirm-upload: WHERE owner_id = :uid AND status = 'ACTIVE'
CREATE INDEX idx_demos_owner_status ON demos(owner_id, status) WHERE status = 'ACTIVE';
```

### 3.2. Bảng `audio_processing_jobs` (Theo dõi xử lý bất đồng bộ)

```sql
CREATE TABLE audio_processing_jobs (
    id UUID PRIMARY KEY,
    demo_id UUID NOT NULL UNIQUE,         -- 1 demo chỉ có 1 active job tại 1 thời điểm
    status VARCHAR(20) NOT NULL,          -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    attempt_count INT NOT NULL DEFAULT 0, -- Số lần Worker retry (max=3)
    last_error TEXT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_apj_demo FOREIGN KEY (demo_id) REFERENCES demos(id)
);

CREATE INDEX idx_apj_status ON audio_processing_jobs(status, updated_at);
```

**Mục đích của bảng**:
- Worker có thể quét lại các job `RUNNING` quá 30 phút khi khởi động lại (khắc phục sự cố worker crash giữa chừng).
- Lưu vết attempt_count + last_error để cronjob cleanup tự set `demos.status = FAILED`.
- Cho phép DLQ hoặc Admin Console truy vết lịch sử xử lý.

### 3.3. Cache Redis (tham chiếu nhanh)

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `demo:key:{demoId}` | `String` | Khóa AES-128 16 bytes nhị phân (base64) | **5 phút** | Cache khóa giải mã để Worker truy vấn ngay khi cần thiết. Đặc tả chi tiết ở docs 04. |
| `audio:job:lock:{demoId}` | `String` | `"locked"` | **10 phút** | Distributed lock chống 2 worker cùng xử lý 1 demo (SETNX + Lua release). |

> Tham chiếu đầy đủ Redis key cho toàn module Audio xem [`docs/09_infrastructure_config.md`](./../09_infrastructure_config.md) mục **3.5. Redis Key Catalog — Phân hệ Secure Audio Streaming**.

### 3.4. Kafka Topic Registry (tham chiếu nhanh)

| Topic Name | Partition | Mô tả | Module sử dụng |
| :--- | :--- | :--- | :--- |
| `audio-processing-events` | 1 (dev) / 3+ (prod) | Phát sự kiện upload Demo đã xác nhận, Worker consume để bắt đầu xử lý HLS | Module 3 |
| `audio-processing-events-dlq` | 1 | Dead Letter Queue cho job xử lý thất bại ≥ 3 lần (Poison Pill) | Module 3 |

> [!IMPORTANT]
> **Bắt buộc bảo mật Kafka**: Kết nối Producer/Consumer tới Kafka broker **phải dùng SASL/SCRAM-SHA-512 + TLS 1.3** (port 9093). Không bao giờ để PLAINTEXT ở môi trường production. ACL của topic `audio-processing-events` chỉ cho phép service account `pwb-audio-producer` WRITE và `pwb-audio-worker` READ. Cấu hình chi tiết xem [`docs/09_infrastructure_config.md`](./../09_infrastructure_config.md) mục **1.2. Kafka — Authentication & Encryption**.

> Quy tắc đặt tên topic theo pattern `{domain}-events` / `{domain}-events-dlq` (xem chi tiết tại [`docs/09_infrastructure_config.md`](./../09_infrastructure_config.md) mục **1.3. Danh mục Topics**).

---

## 🔌 4. API Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>`
*   **Format Phản Hồi**: Cấu trúc `ApiResponse<T>` đồng nhất toàn hệ thống.

---

### 4.1. API Yêu cầu cấp Pre-signed Upload URL
*   **Method**: `POST`
*   **Path**: `/api/v1/demos/presigned-upload-url`
*   **Auth Level**: `Requires ROLE_USER_PRO`
*   **Side Effect (Redis)**: Backend ghi `demo:upload:claim:{s3Key}` → `{userId, expectedSizeBytes, expectedContentType, issuedAt}` (TTL 2h) để `confirm-upload` xác thực ownership & size.

#### Request Body (`PresignedUrlRequest`):
```json
{
  "fileName": "my_new_beat.wav",
  "fileSize": 85400200,
  "contentType": "audio/wav"
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Cấp liên kết tải lên S3 thành công",
  "data": {
    "uploadUrl": "https://pwb-private-bucket.s3.amazonaws.com/original/8cf74f51-3a78-43d9-9524-34e803c4f2bb.wav?Content-Type=audio%2Fwav&AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE&Signature=vjbyPxybdZaNmGa%2ByT272YEAiv4%3D&Expires=1782928560",
    "s3Key": "original/8cf74f51-3a78-43d9-9524-34e803c4f2bb.wav",
    "expiresInSeconds": 60,
    "issuedAt": "2026-07-01T15:44:00Z",
    "maxSizeBytes": 209715200
  },
  "errors": null,
  "timestamp": "2026-07-01T15:45:00Z"
}
```

#### Hướng dẫn vệ sinh (Client Hygiene Notes):
*   `expiresInSeconds: 60` — Client **phải** bắt đầu PUT trước khi hết hạn; nếu hết hạn, gọi lại API để cấp URL mới.
*   `s3Key` là URL path đầy đủ — Frontend có thể lưu tạm trong memory React state, **không persist vào localStorage / cookie / log** vì lý do bảo mật và vì TTL quá ngắn.
*   URL tuyệt đối không được log ra DevTools console production, không đính kèm trong analytics event, không gửi qua email hỗ trợ.
*   Nếu upload fail giữa chừng (mất mạng, refresh tab), Client phải gọi lại API để cấp URL mới — `s3Key` cũ sẽ bị S3 Lifecycle xóa sau 24h nếu không `confirm-upload`.

---

### 4.2. API Xác nhận tải lên thành công & Kích hoạt xử lý (Confirm Upload)
*   **Method**: `POST`
*   **Path**: `/api/v1/demos/confirm-upload`
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Request Body (`ConfirmUploadRequest`):
```json
{
  "s3Key": "original/8cf74f51-3a78-43d9-9524-34e803c4f2bb.wav",
  "title": "Beat Ballad Piano Buồn 2026",
  "voiceTagId": "e5b84f32-3a78-43d9-9524-34e803c4f2aa",
  "watermarkInterval": 25
}
```

#### Response Thành công (202 Accepted):
```json
{
  "success": true,
  "message": "Tệp tin đã được ghi nhận và đang đưa vào tiến trình xử lý âm thanh",
  "data": {
    "demoId": "8cf74f51-3a78-43d9-9524-34e803c4f2bb",
    "status": "PROCESSING"
  },
  "errors": null,
  "timestamp": "2026-07-01T15:46:00Z"
}
```

---

### 4.3. API Truy vấn trạng thái xử lý (Fallback Status Query)
*   **Method**: `GET`
*   **Path**: `/api/v1/demos/{demoId}/status`
*   **Auth Level**: `Requires ROLE_USER_PRO`
*   **Mô tả**: Đây là API fallback phục vụ truy vấn thủ công khi kết nối WebSocket gặp sự cố.

#### Response Thành công (200 OK - Khi xử lý xong):
```json
{
  "success": true,
  "message": "Lấy trạng thái xử lý thành công",
  "data": {
    "demoId": "8cf74f51-3a78-43d9-9524-34e803c4f2bb",
    "status": "ACTIVE",
    "duration": 185.50,
    "sampleRate": 44100,
    "format": "WAV",
    "waveform": [0.12, 0.45, 0.78, 0.90, 0.65, 0.30, 0.85, 0.95, 0.10]
  },
  "errors": null,
  "timestamp": "2026-07-01T15:48:00Z"
}
```

---

### 4.4. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

Bộ ErrorCode dưới đây sẽ được bổ sung vào enum `ErrorCode` (file `shared/exception/ErrorCode.java`):

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | File vượt quá giới hạn 200MB, định dạng không hỗ trợ hoặc `watermarkInterval` ngoài khoảng 10-60 | `fileSize` / `fileName` / `watermarkInterval` |
| `400 Bad Request` | `UNSUPPORTED_AUDIO_FORMAT` | Định dạng tệp không nằm trong whitelist (.wav, .flac, .mp3 ≥ 320kbps) | `fileName` |
| `400 Bad Request` | `FILE_NOT_FOUND_ON_S3` | Backend không tìm thấy tệp tin tương ứng trên S3 (HeadObject trả 404) | `s3Key` |
| `400 Bad Request` | `FILE_SIZE_MISMATCH` | File upload thực tế vượt `expectedSizeBytes` hoặc > 200MB (xác minh sau khi PUT xong) | `s3Key` / `actualSize` |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Quyền không đủ (yêu cầu `ROLE_USER_PRO`) | `null` |
| `403 Forbidden` | `INVALID_S3_KEY_OWNER` | `s3Key` không thuộc user hiện tại (mapping Redis `demo:upload:claim:{s3Key}` không khớp userId) | `s3Key` |
| `403 Forbidden` | `DEMO_QUOTA_EXCEEDED` | User đã có ≥ 20 demo ở trạng thái `ACTIVE` | `null` |
| `403 Forbidden` | `AUDIO_QUOTA_EXCEEDED` | Tổng dung lượng file gốc của các demo ACTIVE của user vượt 5GB | `null` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt quá giới hạn `5/phút/IP` + `10/giờ/user` cho `/presigned-upload-url`, hoặc `30/phút/IP + 30/phút/user` cho `/confirm-upload` | `null` |

#### Error Codes phát sinh từ Worker (Async)

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `500 Internal Server Error` | `INVALID_AUDIO_CONTENT` | File tải lên đã qua Content-Type signature nhưng ffprobe phát hiện không phải audio hợp lệ (codec/sample rate/duration ngoài whitelist, có attached image, v.v.). Worker set `demos.status=FAILED` + `error_message` chứa chi tiết. Đẩy vào DLQ. | `demoId` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Vùng tải lên & Trạng thái Đang xử lý (Grayscale Theme & A11y)
*   **Vùng tải lên (Upload Zone Dropzone)**: 
    *   Thiết kế dạng hộp lớn tối giản nét đứt màu xám đậm (`border-dashed border-neutral-300`). Khi người dùng rê tệp tin vào, đường viền chuyển thành đen đậm (`border-black`).
    *   Biểu tượng icon tải lên phẳng tối giản nét mỏng màu đen.
*   **Trạng thái loading âm thanh (Waveform Skeleton)**:
    *   Trong khi trạng thái là `PROCESSING`, khu vực biểu diễn nhạc sẽ hiển thị một hình sóng giả lập mờ (Skeleton Waveform) nhấp nháy xám nhẹ để người dùng không cảm thấy ứng dụng bị đơ.
*   **Accessibility (A11y)**:
    *   Dropzone khai báo thuộc tính `role="button"` và hỗ trợ kích hoạt bằng phím cách/phím Enter để mở hộp thoại chọn tệp cục bộ.
    *   Khai báo `aria-label="Vùng kéo thả tệp âm thanh WAV hoặc MP3 chất lượng cao tối đa 200 megabytes"`.

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **Xác thực kích thước và định dạng ngay tại Client**:
    *   Frontend thực hiện kiểm tra định dạng đuôi file và thuộc tính `file.size` trước khi thực hiện gọi API sinh Pre-signed URL. Nếu file > 200MB, hiển thị cảnh báo đỏ inline ngay lập tức, tiết kiệm tài nguyên mạng.
*   **Chống Spam xác nhận**:
    *   Vô hiệu hóa form cấu hình (Tiêu đề, Voice Tag) ngay khi người dùng nhấn "Xác nhận và Xử lý", hiển thị Spinner xoay.
*   **Thông báo trạng thái qua WebSocket (WebSocket State Notification)**:
    *   Thay vì gọi Polling liên tục gây tốn tài nguyên mạng (Nghịch lý Polling), Frontend thực hiện subscribe vào topic cá nhân `/user/queue/demos/status` ngay khi nhận được phản hồi HTTP 202 từ cổng `confirm-upload`.
    *   Khi cụm Worker hoàn thành xử lý, Backend tự động push tin nhắn WebSocket `PROCESSING_COMPLETED` hoặc `PROCESSING_FAILED` tới client. Giao diện nhận được tin sẽ lập tức ẩn Skeleton, vẽ Waveform thật và hiện nút "Chia sẻ" ngay lập tức, đem lại trải nghiệm thời gian thực tối ưu.

### 5.3. Trạng thái Kết nối & Trải nghiệm Reconnection (WebSocket/Offline UX)
*   Nếu người dùng bị mất mạng trong lúc file đang được tải lên S3 trực tiếp, Frontend hiển thị thanh trạng thái màu đỏ: *"Mất kết nối mạng. Đang tạm dừng tải lên..."*. 
*   Ứng dụng sử dụng cơ chế **Resumable Upload** của S3 để tiếp tục tải lên các phân đoạn còn lại sau khi có mạng trở lại thay vì phải upload từ đầu.

### 5.4. Sơ đồ Luồng Màn hình (Screen Flow)

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    UploadPage["Màn hình Thư viện Demo <br> /dashboard/demos"]:::screen -->|Click Tải lên Demo| OpenDialog["Hộp thoại kéo thả file nhạc"]:::screen
    
    OpenDialog -->|Chọn file hợp lệ| RequestPresigned{Gọi API POST /presigned-upload-url}:::action
    
    RequestPresigned -->|Nhận uploadUrl| UploadS3{FE PUT trực tiếp file lên S3}:::action
    
    UploadS3 -->|Thành công| ConfirmAction{Gọi API POST /confirm-upload}:::action
    
    ConfirmAction -->|Nhận HTTP 202| ProcessingState["Hiển thị Waveform Skeleton nhấp nháy <br> Trạng thái: Xử lý âm thanh..."]:::screen
    
    ProcessingState -->|Đăng ký lắng nghe| SubWS{Subscribe WS /user/queue/demos/status}:::action
    
    SubWS -->|Nhận PROCESSING_COMPLETED| ActiveState["Hiện Waveform thật & Nút chia sẻ"]:::screen
    SubWS -->|Nhận PROCESSING_FAILED| FailState["Hiển thị lỗi xử lý nhạc"]:::screen
    
    ActiveState --> UploadPage
    FailState --> OpenDialog
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Presigned URL generated | `{"event": "PRESIGNED_UPLOAD_GENERATED", "s3Key": "original/UUID.wav", "userId": "c8b74f51-...", "ip": "203.0.113.4", "ua": "Chrome/126.0", "expectedSizeBytes": 85400200, "expectedContentType": "audio/wav"}` |
| `INFO` | Upload claim saved | `{"event": "UPLOAD_CLAIM_SAVED", "s3Key": "original/UUID.wav", "userId": "...", "ttlSeconds": 7200}` |
| `WARN` | Ownership mismatch | `{"event": "UPLOAD_OWNERSHIP_MISMATCH", "s3Key": "original/UUID.wav", "expectedUserId": "...", "actualUserId": "...", "ip": "..."}` |
| `WARN` | File size mismatch | `{"event": "UPLOAD_SIZE_MISMATCH", "s3Key": "original/UUID.wav", "expectedBytes": 100000000, "actualBytes": 524288000}` |
| `WARN` | Demo quota exceeded | `{"event": "DEMO_QUOTA_EXCEEDED", "userId": "...", "activeDemos": 21, "limit": 20}` |
| `WARN` | Audio quota exceeded | `{"event": "AUDIO_QUOTA_EXCEEDED", "userId": "...", "currentBytes": 5368709120, "limit": 5368709120, "newFileBytes": 85400200}` |
| `INFO` | Upload claimed & tagged | `{"event": "UPLOAD_CONFIRMED_TAGGED", "s3Key": "original/confirmed/{userId}/UUID.wav", "tags": {"confirmed": "true"}}` |
| `INFO` | Upload confirmed | `{"event": "UPLOAD_CONFIRMED", "s3Key": "original/confirmed/{userId}/UUID.wav", "title": "Beat Piano", "userId": "c8b74f51-..."}` |
| `INFO` | Background worker started | `{"event": "AUDIO_WORKER_STARTED", "demoId": "8cf74f51-...", "s3Key": "original/confirmed/.../UUID.wav", "jobId": "..."}` |
| `INFO` | Job lock acquired | `{"event": "AUDIO_JOB_LOCKED", "demoId": "8cf74f51-..."}` (Worker SETNX thành công) |
| `WARN` | ffprobe validation failed (Anti-Spoofing) | `{"event": "FFPROBE_VALIDATION_FAILED", "demoId": "...", "s3Key": "original/confirmed/...", "reason": "codec_not_whitelisted: h264", "expectedContentType": "audio/wav"}` — `expectedContentType` lookup path: Worker query DB `SELECT original_s3_key FROM demos WHERE id=:demoId`, sau đó `GET demo:upload:claim:{original_s3_key}` từ Redis (key tồn tại 2h sau confirm-upload, sau đó fallback log chỉ ghi `demoId` + `s3Key`). |
| `INFO` | FFmpeg sidechain compress success | `{"event": "FFMPEG_WATERMARK_SUCCESS", "demoId": "8cf74f51-...", "duration": 185.5}` |
| `INFO` | HLS mux + AES encryption success | `{"event": "FFMPEG_HLS_SUCCESS", "demoId": "8cf74f51-...", "segments": 31}` |
| `INFO` | Audio processing completed | `{"event": "AUDIO_PROCESSING_COMPLETED", "demoId": "8cf74f51-...", "status": "ACTIVE"}` |
| `WARN` | Worker retry attempt | `{"event": "AUDIO_WORKER_RETRY", "demoId": "...", "attempt": 1, "maxAttempts": 3, "error": "FFmpeg exit code 139"}` |
| `ERROR` | S3 confirm failed (File missing) | `{"event": "CONFIRM_FAILED_S3_MISSING", "s3Key": "original/UUID.wav"}` |
| `ERROR` | FFmpeg execution crashed | `{"event": "FFMPEG_WORKER_CRASHED", "demoId": "8cf74f51-...", "attempt": 2, "error": "Invalid sample format"}` |
| `ERROR` | Job exhausted retries, sent to DLQ | `{"event": "AUDIO_JOB_DLQ", "demoId": "...", "jobId": "...", "attempts": 3}` |
| `ERROR` | DB transition to FAILED | `{"event": "AUDIO_PROCESSING_FAILED", "demoId": "...", "errorMessage": "INVALID_AUDIO_CONTENT"}` |

### 6.2. Quy tắc Bảo mật Log
*   Không log thông tin chữ ký signature hay các tham số bảo mật của Pre-signed URL lên hệ thống log. Chỉ log khóa S3 rút gọn dạng đường dẫn lưu trữ.
