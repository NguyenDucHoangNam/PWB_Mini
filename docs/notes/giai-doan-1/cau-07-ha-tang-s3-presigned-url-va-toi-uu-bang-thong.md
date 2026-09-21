# Câu 7: Hạ Tầng AWS S3, Presigned URL & Toàn Bộ Kiến Trúc Xử Lý Lỗi, Bảo Mật Tệp Nặng

### ❓ Câu hỏi:
> *"Tại sao hệ thống lại áp dụng triết lý 'Bypass Backend' (Zero-Payload-Through-Backend) với AWS S3 Presigned URL thay vì cho client tải trực tiếp qua Spring Boot? Cơ chế ký số SigV4 hoạt động như thế nào? Phân tích toàn bộ các giải pháp kỹ thuật và cơ chế phòng thủ chuyên sâu đã triển khai trong mã nguồn PWB_MiNi (chống upload quá dung lượng, chống Path Traversal & IDOR trên S3 key, chống Stored XSS khi download, kiểm tra Magic Bytes qua Ranged GET 16 Bytes, cạm bẫy InputStream khi Retry, và quản trị vòng đời tệp với StorageCleaner + S3 Lifecycle)?"*

---

### 💡 Câu trả lời:

#### 1. Tại sao KHÔNG cho tệp nặng đi qua Backend? (Triết lý Zero-Payload-Through-Backend)

Trong mô hình truyền thống, client gửi HTTP POST Multipart (`multipart/form-data`) lên server Spring Boot, sau đó server mới đẩy tệp sang AWS S3. Với các nền tảng âm thanh chất lượng cao (file từ **50MB đến 200MB**), mô hình này là một thảm họa vận hành vì 3 nguyên nhân:

1. **Gánh nặng Băng thông kép (Double Bandwidth Cost)**:
   - Dữ liệu phải đi 2 chặng: `Client -> Spring Boot Server (Inbound)` rồi `Spring Boot Server -> AWS S3 (Outbound)`. Chi phí truyền dữ liệu và tải mạng của máy chủ tăng gấp đôi.
2. **Cạn kiệt Tomcat Thread Pool (Thread Starvation)**:
   - Mặc định Tomcat có khoảng 200 worker threads. Nếu một client mạng di động 3G/4G chập chờn tải bài hát 200MB mất 2–3 phút, thì **một worker thread bị chiếm giữ (blocked)** suốt 2–3 phút chỉ để ngồi đợi socket I/O đọc byte stream.
   - Chỉ cần 100–200 người dùng upload đồng thời, toàn bộ Thread Pool cạn kiệt. Các API quan trọng như `/login`, `/live-room` bị xếp hàng đợi và sập dịch vụ với mã `HTTP 504 Gateway Timeout`.
3. **Áp lực bộ nhớ RAM (Memory Churn) & Quá tải Disk I/O**:
   - Việc buffering hàng chục stream lớn vào bộ nhớ gây áp lực nặng nề lên bộ thu gom rác (Garbage Collection), kích hoạt hiện tượng "Stop-The-World" làm đơ ứng dụng hoặc làm đầy phân vùng tạm `/tmp`.

##### 👉 Giải pháp: Triết lý "Bypass Backend" (Zero-Payload)
Hệ thống tách bạch triệt để giữa hai mặt phẳng:
- **Control Plane (Mặt phẳng điều khiển - Do Backend đảm nhiệm)**: Nhận metadata, xác thực quyền hạn, thẩm định kích thước và cấp "vé thông hành có chữ ký số" (Presigned URL). Backend chỉ tốn **5–10ms** CPU và vài KB RAM.
- **Data Plane (Mặt phẳng dữ liệu - Do AWS S3 đảm nhiệm)**: Toàn bộ lưu lượng truyền tải byte nặng (50MB–200MB) diễn ra trực tiếp giữa Trình duyệt client và AWS S3. Máy chủ Backend hoàn toàn không phải chạm vào bất kỳ byte âm thanh nào.

```
[MÔ HÌNH CŨ: NGHẼN BACKEND]
Client  ──(Upload 200MB: Khóa luồng Tomcat 2 phút)──> [ Spring Boot ] ──(Upload 200MB)──> [ AWS S3 ]

[MÔ HÌNH ZERO-PAYLOAD: BYPASS BACKEND TRONG PWB_MINI]
Client  ──(1. Xin Upload URL: 5ms, vài KB)───────────> [ Spring Boot ]
Client  <─(2. Trả Presigned PUT URL có chữ ký SigV4)─ [             ]
Client  ────────────────(3. PUT 200MB trực tiếp)───────────────────────────────────────> [ AWS S3 ]
```

---

#### 2. Cơ chế Hoạt Động của Presigned URL (AWS Signature Version 4 - SigV4)

Presigned URL là một đường link HTTP tạm thời mang theo chữ ký mật mã học **AWS Signature Version 4 (HMAC-SHA256)** được sinh ra từ `AWS_SECRET_ACCESS_KEY` của Backend. Nó cấp quyền cho client được thực hiện thao tác định sẵn (`PUT` hoặc `GET`) lên một đối tượng cụ thể trong bucket vốn được đóng kín 100% (`Block All Public Access`).

Quy trình 3 bước chuẩn:
1. **Handshake (Xin vé)**: Client gửi request JSON: `POST /api/v1/songs/upload-url` kèm `sizeBytes` và `format`.
2. **Ký số bảo mật (`S3Presigner`)**:
   - Backend kiểm tra hợp lệ, tạo `PutObjectPresignRequest`.
   - Khóa cứng các thông số vào chuỗi băm SigV4: Bucket, Key, thời hạn (`X-Amz-Expires = 3600s`), và các header bắt buộc trong `X-Amz-SignedHeaders`: `content-length`, `content-type`, `host`.
3. **Thực thi trực tiếp**:
   - Client gửi lệnh HTTP `PUT` kèm binary file trực tiếp lên S3 với đúng các header đã ký. S3 giải mã chữ ký bằng Secret Key đối ứng, nếu khớp và chưa hết hạn thì nhận file (`200 OK`).

---

#### 3. Toàn Bộ Các Lớp Phòng Thủ & Xử Lý Lỗi Chuyên Sâu (Code Walkthrough)

Dự án `PWB_MiNi` đã cài đặt **7 cơ chế phòng thủ kỹ thuật đa tầng** trong mã nguồn để kiểm soát và bảo vệ tuyệt đối hạ tầng S3:

---

##### 🛡️ Lớp 1: Chống Upload Quá Dung Lượng (Oversized File) — 2 Chốt Chặn Kép
- **Nguy cơ**: Người dùng tải file quá lớn làm nghẽn hạ tầng hoặc kẻ tấn công "khai man" xin URL cho file 1KB nhưng khi `PUT` lên S3 lại đẩy file 50GB gây bùng nổ chi phí lưu trữ.
- **Giải pháp 2 chốt chặn**:
  1. *Chốt chặn 1 (Tại Backend)*: `SongUseCaseImpl.assertUploadSizeAllowed` kiểm tra:
     ```java
     if (sizeBytes > uploadProperties.getMaxFileSizeBytes()) { // Trần 200MB
         throw new AudioBusinessException(AudioErrorCode.FILE_TOO_LARGE);
     }
     ```
     Bị từ chối ngay lập tức với mã `AUDIO_005` khi **chưa có byte nào rời khỏi máy client**.
  2. *Chốt chặn 2 (Tại Cổng AWS S3 - SigV4 Signed Header)*:
     Trong `S3StorageServiceImpl.generatePresignedUploadUrl`, giá trị `contentLength` được đưa thẳng vào yêu cầu ký:
     ```java
     put.contentLength(contentLength); // Đưa vào X-Amz-SignedHeaders
     ```
     Nếu client đẩy lên S3 một payload lệch **dù chỉ đúng 1 byte** so với dung lượng đã khai báo, thuật toán SHA-256 trên S3 sẽ không khớp -> **AWS S3 ngắt kết nối và trả về `403 Forbidden` (`SignatureDoesNotMatch`) ngay tại cửa ngõ**.

---

##### 🛡️ Lớp 2: Chống Path Traversal & IDOR trên S3 Key — 2 Tầng Phòng Thủ
- **Nguy cơ**: Kẻ tấn công chèn ký tự `..` vào đường dẫn để ghi đè file hệ thống, hoặc dùng kỹ thuật IDOR kết hợp Path Traversal khi đăng ký bài hát để đánh cắp file nhạc của user khác:
  `audio/staging/<my-user-id>/../<victim-user-id>/stolen-song.mp3`
- **Giải pháp 2 tầng phòng thủ**:
  1. *Tầng Hạ tầng Lưu trữ (`MediaTypeUtils.validateKey`)*:
     Mọi phương thức trong `StorageService` (`readHead`, `upload`, `delete`, `generatePresignedUrl`...) bắt buộc phải gọi thẩm định key:
     ```java
     public void validateKey(String key) {
         if (key == null || key.isBlank() || key.length() > 1024) throw new StorageException(STORAGE_INVALID_KEY);
         if (key.contains("..")) throw new StorageException(STORAGE_INVALID_KEY); // Chặn đứng lùi thư mục
         if (!VALID_KEY_PATTERN.matcher(key).matches()) throw new StorageException(STORAGE_INVALID_KEY); // Regex an toàn
     }
     ```
  2. *Tầng Nghiệp vụ UseCase (`SongUseCaseImpl.assertKeyBelongsToUser`)*:
     Khi client gửi `s3Key` về để đăng ký bài hát vào DB, usecase kiểm tra:
     ```java
     private void assertKeyBelongsToUser(UUID userId, String s3Key) {
         String expectedPrefix = STAGING_KEY_ROOT + userId + "/";
         if (s3Key == null || !s3Key.startsWith(expectedPrefix) || s3Key.contains("..")) {
             throw new AudioBusinessException(AudioErrorCode.UNAUTHORIZED_ACCESS);
         }
     }
     ```
     Khóa cứng quyền sở hữu theo `userId` lấy từ JWT Token, ngăn chặn triệt để tấn công đánh cắp file.

---

##### 🛡️ Lớp 3: Chống Tấn Công Stored XSS khi Download (`Content-Disposition: attachment`)
- **Nguy cơ**: Kẻ tấn công tải lên file HTML chứa mã độc JavaScript đánh cắp session/cookie nhưng đặt tên là `track.mp3`. Khi người dùng khác bấm vào link nghe nhạc, nếu S3 trả về `Content-Disposition: inline`, trình duyệt sẽ thông dịch file HTML đó trong ngữ cảnh origin của S3.
- **Giải pháp trong `S3StorageServiceImpl.generatePresignedUrl`**:
  ```java
  GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .responseContentDisposition("attachment") // Ép buộc tải về, không cho browser thực thi DOM
      .build();
  ```
  Thuộc tính `attachment` vô hiệu hóa hoàn toàn nguy cơ Stored XSS. Các thẻ HTML5 `<audio src="...">` và `<img>` trên ứng dụng web vẫn phát nhạc và hiển thị bình thường vì chúng chỉ nạp binary stream vào decoder chứ không biên dịch HTML.

---

##### 🛡️ Lớp 4: Kiểm Tra File Thật Bằng "Ranged GET 16 Bytes & Magic Bytes" (Zero Bandwidth Inspection)
- **Nguy cơ**: Vì Backend không chạm vào file khi upload, hacker có thể đổi tên `trojan.exe` thành `song.mp3` rồi gọi API đăng ký bài hát vào DB.
- **Giải pháp trong `SongUseCaseImpl.assertReallyAudio` & `MediaTypeUtils`**:
  - Không bao giờ tin extension `.mp3` hay header `Content-Type` do client gửi lên.
  - Backend gọi `storagePort.readHead(storageKey, 16)`.
  - Phương thức này gửi HTTP Request tới S3 với header: `Range: bytes=0-15`.
  - **Tối ưu băng thông 99.9999%**: Backend chỉ kéo đúng **16 byte đầu tiên** của file qua mạng thay vì kéo cả 200MB.
  - `MediaTypeUtils.detectFromBytes` kiểm tra chữ ký nhị phân ("Magic Bytes"):
    - **MP3**: Bắt đầu bằng `49 44 33` (`ID3`) hoặc MPEG frame sync (`FF E0` đến `FF FB`).
    - **WAV**: Bắt đầu bằng `52 49 46 46` (`RIFF`).
    - **FLAC**: Bắt đầu bằng `66 4C 61 43` (`fLaC`).
    - **OGG**: Bắt đầu bằng `4F 67 67 53` (`OggS`).
  - Nếu 16 byte đầu không phải là chữ ký audio, hệ thống ném `INVALID_AUDIO_FILE` và từ chối lưu vào Database.

---

##### 🛡️ Lớp 5: Cạm Bẫy `@Retryable` Với `InputStream` (Stream Cannot Rewind)
- **Vấn đề kỹ thuật**:
  - Trong `S3StorageServiceImpl`, các thao tác giao tiếp mạng (`upload(byte[])`, `uploadFile(Path)`, `downloadToFile`, `readHead`) đều có `@Retryable(retryFor = {S3Exception.class, IOException.class}, maxAttempts = 3)` để tự phục hồi khi mạng gián đoạn.
  - Tuy nhiên, phương thức `upload(String key, InputStream content, ...)` **CỐ TÌNH BỊ LOẠI TRỪ KHỎI `@Retryable`**.
- **Nguyên nhân cốt lõi**:
  - `InputStream` là một luồng đọc một chiều (forward-only). Nó không hỗ trợ việc tua lại từ đầu (`markSupported() == false`).
  - Nếu lần thử 1 bị đứt mạng khi đã đọc được 2MB/5MB, con trỏ luồng đã nằm ở byte thứ 2 triệu.
  - Nếu Spring AOP tự động retry lần 2 với cùng `InputStream` đó, nó sẽ đọc từ vị trí dở dang hoặc EOF. Payload gửi đi bị thiếu hụt nghiêm trọng so với `contentLength` đã khai báo lúc đầu.
  - Hậu quả: AWS S3 ném lỗi `Request Entity Incomplete` hoặc `Length Mismatch`, che lấp lỗi mạng gốc và lần retry thứ 2, thứ 3 chắc chắn thất bại 100%.
- **Quy tắc Senior**: Muốn Retry an toàn, bắt buộc truyền vào `byte[]` hoặc `Path` (tệp trên đĩa), vì mỗi lần retry có thể tạo lại một luồng đọc mới tinh từ byte 0 (`new ByteArrayInputStream(content)` hoặc `Files.newInputStream(path)`).

---

##### 🛡️ Lớp 6: Quản Lý Vòng Đời Tệp & Đồng Bộ Transaction (`StorageCleaner` & S3 Lifecycle)
Xử lý bài toán hóc búa: **Giao dịch Database (ACID) không thể rollback được đối tượng đã đẩy lên S3 (External Storage)**.

1. **Chiến lược Tách tiền tố Staging**:
   - File tải lên luôn nằm ở `audio/staging/{userId}/{uuid}.mp3`.
   - Khi client gọi đăng ký bài hát: S3 thực hiện lệnh `copy` nội bộ sang `audio/originals/{userId}/{uuid}.mp3` (copy diễn ra trên hạ tầng AWS, không tốn 1 byte băng thông backend).
2. **Nguyên tắc "Thà sinh rác còn hơn mất dữ liệu" (`StorageCleaner.deleteAfterCommit`)**:
   - Khi xóa bài hát hoặc dọn file staging, `StorageCleaner` sử dụng `TransactionSynchronizationManager.registerSynchronization`:
     ```java
     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
         @Override
         public void afterCommit() {
             keys.forEach(StorageCleaner.this::deleteQuietly);
         }
     });
     ```
   - **Bắt buộc commit DB xong mới được xóa file trên S3**. Nếu xóa file trước mà commit DB bị lỗi rollback -> DB còn bài hát mà S3 mất file (Hỏng dữ liệu nghiêm trọng). Ngược lại, commit xong mới xóa file S3, nếu xóa S3 thất bại thì chỉ sinh ra file rác vô hại (Orphaned Object).
3. **Nếu việc ghi DB thất bại ngay sau khi copy**:
   - `SongUseCaseImpl` bắt exception và gọi `storageCleaner.deleteNow(promotedKey)` để dọn dẹp ngay file vừa copy sang `originals`.
4. **Tự động dọn rác tệp mồ côi (Zero-Cronjob)**:
   - Nếu client xin URL, tải file lên `audio/staging/` nhưng tắt trình duyệt và không bao giờ gọi API đăng ký: Toàn bộ thư mục `audio/staging/` được cấu hình **AWS S3 Lifecycle Rule tự động xóa vĩnh viễn các đối tượng sau 24 giờ**. Backend không cần viết bất kỳ cronjob dọn rác nào.

---

##### 🛡️ Lớp 7: Bảo Mật Cấu Hình & Triết Lý "Zero-Secret" với AWS IAM Role
- **Bucket hoàn toàn đóng kín**: Bật `Block All Public Access`, database chỉ lưu Storage Key (`audio/originals/...`), không lưu URL cứng.
- **Triết lý Zero-Secret (`StorageConfig.credentialsProvider`)**:
  ```java
  private AwsCredentialsProvider credentialsProvider(StorageProperties.S3 s3) {
      if (s3.getAccessKey() != null && !s3.getAccessKey().isBlank()) {
          return StaticCredentialsProvider.create(
              AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
      }
      return DefaultCredentialsProvider.create(); // Nhận diện IAM Role của EC2/ECS/EKS
  }
  ```
  Trên môi trường Production, hệ thống sử dụng `DefaultCredentialsProvider` để tự động nhận IAM Instance Profile / Pod Identity từ máy chủ AWS mà không cần lưu cứng bất kỳ Access Key / Secret Key nào trong file cấu hình hay biến môi trường.
