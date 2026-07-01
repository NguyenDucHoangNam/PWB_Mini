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

#### C. Quy trình Xử lý Nhạc nền (Async Audio Processing Pipeline) & Tránh Nghẽn CPU
Để tránh thắt nút cổ chai và sập server do CPU chạm ngưỡng 100% khi chạy FFmpeg đồng thời với Live Room (FFmpeg CPU Starvation):
*   **Tách biệt Kiến trúc (Independent Worker Cluster)**: Khi Backend nhận yêu cầu xác nhận tải lên (`confirm-upload`), nó ghi nhận trạng thái `PROCESSING` vào DB, rồi bắn sự kiện `AUDIO_PROCESS_EVENT` sang hàng đợi **Apache Kafka**.
*   **Worker độc lập (Standalone Workers)**: Một cụm các máy chủ chuyên dụng (Worker Nodes viết bằng Python/Go hoặc Java Worker chạy độc lập) sẽ lắng nghe sự kiện từ Kafka để thực thi:
    1.  **Dập âm lượng Sidechain (Sidechain Auto-Ducking)**:
        *   Sử dụng bộ lọc nén `sidechaincompress` của FFmpeg. Khi Voice Tag phát đè lên nhạc nền, âm lượng của nhạc nền tại đúng phân khúc đó sẽ tự động giảm xuống `-12dB` (chỉ số nén), sau đó phục hồi ngay lập tức về mức ban đầu sau khi Voice Tag kết thúc. Việc này giúp giữ chất lượng nghe thử cao nhất ở các đoạn nhạc không có tag.
        *   *Câu lệnh FFmpeg Sidechain Ducking tham khảo*:
            ```bash
            ffmpeg -i original.wav -i voicetag.wav -filter_complex "[1]adelay=25000|25000[tag];[0][tag]sidechaincompress=threshold=0.03:ratio=12:attack=5:release=500[out]" -map "[out]" output_watermarked.wav
            ```
            *(Đoạn lệnh chèn tag tại giây thứ 25, dìm âm lượng nhạc nền xuống khi có tag)*.
    2.  **Mã hóa AES-128 & Phân đoạn HLS**:
        *   Cắt file sau khi đóng dấu thành các phân đoạn `.ts` nhỏ dài **6 giây**.
        *   Mỗi phân đoạn được mã hóa bằng thuật toán đối xứng AES-128 sử dụng khóa nhị phân 16-byte sinh ngẫu nhiên cho từng tệp demo. Khóa giải mã được đẩy lên Redis phục vụ phân phối.
    3.  **Trích xuất Waveform**:
        *   FFmpeg phân tích biên độ đỉnh âm thanh của file để trích xuất **200 điểm số thực** (giá trị từ `0.0` đến `1.0`) đại diện cho biểu đồ hình sóng của tệp nhạc.
        *   Mảng dữ liệu này được lưu trực tiếp dưới dạng chuỗi phân tách bằng dấu phẩy trong PostgreSQL để hiển thị nhanh trên Frontend.
    4.  **Tải lên S3 & Cập nhật Trạng thái**:
        *   Tải các tệp phân đoạn `.ts` và playlist `.m3u8` lên S3, sau đó cập nhật database của Backend sang `ACTIVE` và gửi thông điệp thành công.
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
| | `title` | Bắt buộc, tối đa 100 ký tự | `@NotBlank`, `@Size(max=100)` | Tiêu đề hiển thị của bản nhạc |
| | `watermarkInterval` | Bắt buộc, giá trị từ 10 đến 60 | `@Min(10)`, `@Max(60)` | Tần suất xuất hiện Voice Tag (giây) |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/demos/presigned-upload-url` | **5 requests / phút / IP** | Ngăn chặn hành vi spam sinh URL rác làm quá tải S3 |

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
    participant Worker as Standalone Audio Worker
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
    
    BE->>BE: Gọi S3 HeadObject xác nhận tệp thực sự tồn tại
    alt Tệp không tồn tại trên S3
        BE-->>FE: HTTP 400 Bad Request (FILE_NOT_FOUND_ON_S3)
    else Xác minh thành công
        Note over BE, DB: Bắt đầu Transaction
        BE->>DB: Ghi nhận Demo mới (status='PROCESSING', title, original_s3_key=s3Key)
        Note over BE, DB: Commit Transaction
        
        BE->>Kafka: Publish AUDIO_PROCESS_EVENT (demoId, s3Key, watermarkInterval)
        BE-->>FE: HTTP 202 Accepted (status='PROCESSING', demoId=UUID)
        
        FE->>BE: Subscribe WebSocket topic cá nhân: /user/queue/demos/status
        FE-->>Producer: Hiển thị trạng thái "Đang xử lý âm thanh..." (Waveform Skeleton nhấp nháy)
        
        Note over Worker, Kafka: Tiến trình xử lý độc lập ngầm (Standalone Audio Worker)
        Worker->>Kafka: Lắng nghe và tiêu thụ sự kiện AUDIO_PROCESS_EVENT
        Worker->>S3: Tải file nhạc gốc về bộ đệm cục bộ
        Worker->>Worker: Thực thi chèn Voice Tag đè lên nhạc (Sidechain Ducking)
        Worker->>Worker: Sinh khóa AES-128 ngẫu nhiên (16 bytes) và đẩy lên Redis phục vụ giải mã bảo mật
        Worker->>Worker: Chạy FFmpeg băm luồng HLS (.m3u8 & các phân đoạn .ts đã mã hóa AES)
        Worker->>Worker: Chạy phân tích biên độ đỉnh trích xuất mảng Waveform 200 điểm float
        
        Worker->>S3: Tải các tệp phân đoạn .ts và file playlist.m3u8 lên thư mục 'stream/UUID/'
        
        Note over Worker, DB: Cập nhật cơ sở dữ liệu
        Worker->>DB: Cập nhật demos -> status='ACTIVE', waveform_data='0.1,0.2...', duration, sample_rate
        Worker->>Worker: Xóa sạch tệp tạm cục bộ
        
        Worker->>BE: Gửi tín hiệu hoàn tất xử lý
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
    hls_playlist_s3_key VARCHAR(255) NULL, -- Sẽ được cập nhật sau khi xử lý HLS xong
    status VARCHAR(20) NOT NULL, -- 'PROCESSING', 'ACTIVE', 'FAILED'
    duration DECIMAL(10, 2) NULL,
    sample_rate INT NULL,
    format VARCHAR(10) NULL,
    waveform_data TEXT NULL, -- Lưu danh sách 200 điểm số thực ngăn cách bằng dấu phẩy
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_demos_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- Chỉ mục hỗ trợ tải thư viện demo của Producer nhanh chóng
CREATE INDEX idx_demos_owner_created ON demos(owner_id, created_at DESC);
```

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
    "s3Key": "original/8cf74f51-3a78-43d9-9524-34e803c4f2bb.wav"
  },
  "errors": null,
  "timestamp": "2026-07-01T15:45:00Z"
}
```

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

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | File vượt quá giới hạn 200MB hoặc định dạng không hỗ trợ | `fileSize` / `fileName` |
| `400 Bad Request` | `FILE_NOT_FOUND_ON_S3` | Backend không tìm thấy tệp tin tương ứng trên S3 | `s3Key` |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Quyền không đủ (yêu cầu `ROLE_USER_PRO`) | `null` |

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
| `INFO` | Presigned URL generated | `{"event": "PRESIGNED_UPLOAD_GENERATED", "s3Key": "original/UUID.wav", "userId": "c8b74f51-..."}` |
| `INFO` | Upload confirmed | `{"event": "UPLOAD_CONFIRMED", "s3Key": "original/UUID.wav", "title": "Beat Piano", "userId": "c8b74f51-..."}` |
| `INFO` | Background worker started | `{"event": "AUDIO_WORKER_STARTED", "demoId": "8cf74f51-...", "s3Key": "original/UUID.wav"}` |
| `INFO` | FFmpeg sidechain compress success | `{"event": "FFMPEG_WATERMARK_SUCCESS", "demoId": "8cf74f51-...", "duration": 185.5}` |
| `INFO` | Audio processing completed | `{"event": "AUDIO_PROCESSING_COMPLETED", "demoId": "8cf74f51-...", "status": "ACTIVE"}` |
| `ERROR` | S3 confirm failed (File missing) | `{"event": "CONFIRM_FAILED_S3_MISSING", "s3Key": "original/UUID.wav"}` |
| `ERROR` | FFmpeg execution crashed | `{"event": "FFMPEG_WORKER_CRASHED", "demoId": "8cf74f51-...", "error": "Invalid sample format"}` |

### 6.2. Quy tắc Bảo mật Log
*   Không log thông tin chữ ký signature hay các tham số bảo mật của Pre-signed URL lên hệ thống log. Chỉ log khóa S3 rút gọn dạng đường dẫn lưu trữ.
