# Giai đoạn 3: Bất Đồng Bộ & Xử Lý File Nặng — Module Audio

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 2: Module IAM](02-giai-doan-2-module-iam.md) | [Giai đoạn 4: Module Live Room ➡️](04-giai-doan-4-module-liveroom.md)

---

- **Domain**:
  - `Song.java` (`com.pwb.audio.domain.model`): Quản lý vòng đời bài hát qua máy trạng thái `SongStatus` (`UPLOADED`, `PROCESSING`, `PROCESSED`, `FAILED`).
  - `VoiceTag.java` & `SongTagConfig.java` (`com.pwb.audio.domain.model`): Cấu hình lồng tiếng (vị trí giây, âm lượng, ducking).
- **Cấp Quyền Tải Lên & Kích Hoạt Xử Lý**:
  - `SongController.java` (`com.pwb.audio.api.controller`): Endpoint `POST /upload-url` và `POST /songs` (đăng ký bài hát) — chỉ yêu cầu đã đăng nhập.
  - `SongUseCaseImpl.java` (`com.pwb.audio.application.usecase.impl`): Toàn bộ điều phối cấp URL, xác thực an ninh, promotion file và outbox event.
- **Pipeline Xử Lý Bất Đồng Bộ (Kafka → FFmpeg)**:
  - `SongProcessingConsumer.java` (`com.pwb.audio.infrastructure.processor`): Lắng nghe event từ topic Kafka `voice.processing.v1`.
  - `SongProcessorWorker.java` (`com.pwb.audio.infrastructure.processor`): Worker tải file từ S3 và điều phối tiến trình ghép nhạc.
  - `JaffreeAudioProcessorAdapter.java` (`com.pwb.audio.infrastructure.audio`): Sử dụng Jaffree/FFmpeg dập voice tag vào bài hát và chuẩn hóa âm lượng LUFS.
- **Frontend Quản Lý**:
  - `features/voice/` (`Frontend/src/features/voice`): Quản lý tạo voice tag bằng Text-to-Speech Google.
  - `songs/` (`Frontend/src/app/(dashboard)/dashboard/songs`): Giao diện danh sách bài hát và thanh tiến trình upload trực tiếp lên S3.

- **Chi Tiết Phần 1: Kiến Trúc Bypass Cloud Storage & Quản Lý Vòng Đời Tệp (Staging Lifecycle)**:

  Khi phỏng vấn về phần này, sếp có thể dẫn dắt câu chuyện qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Tại Sao Phải Bypass Backend? (Data Plane vs Control Plane)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Tệp âm thanh chất lượng cao (WAV, FLAC, MP3) có dung lượng lớn, từ **50MB đến 200MB**.
    - Nếu cho file đi qua Server Backend:
      - **Nghẽn mạng**: Băng thông của server bị "nhân đôi" (vừa nhận dữ liệu từ client, vừa phải đẩy dữ liệu lên S3).
      - **Tràn bộ nhớ (OOM)**: Bộ đệm RAM của JVM bị phình to khi nhiều người cùng tải file.
      - **Cạn kiệt Thread (Thread Pool Starvation)**: Mỗi kết nối tải file nặng mất từ 30 giây đến vài phút tùy vào tốc độ mạng của người dùng. Trong suốt thời gian đó, một Worker Thread của Tomcat bị giam cầm hoàn toàn, khiến server nhanh chóng hết sạch thread để phục vụ các API nhẹ khác (như đăng nhập, lấy danh sách).
  * **Cơ chế chúng ta xử lý**:
    - Tách đôi hệ thống thành 2 luồng: **Luồng điều khiển (Control Plane)** và **Luồng dữ liệu (Data Plane)**.
    - Backend chỉ nắm quyền điều khiển: Cấp một "vé thông hành có chữ ký số" (**Presigned PUT URL** của AWS S3) với thời hạn ngắn (1 giờ).
    - Trình duyệt sẽ dùng vé này để truyền dữ liệu nhị phân **thẳng lên AWS S3**. Server Backend của chúng ta hoàn toàn rảnh tay, không tốn 1 byte RAM, không tốn băng thông và giải phóng Worker Thread ngay trong vài chục mili-giây.

  ---

  #### 2. Cơ Chế Khóa Chữ Ký SigV4 — Làm Sao S3 Biết Để Chặn 403 Forbidden Ngay Tại Cửa?

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi client đẩy file thẳng lên S3 mà không qua Backend, làm sao ngăn được kẻ xấu gian lận? 
    - Ví dụ: Kẻ xấu xin cấp URL cho file 5MB, nhưng lại lén tải lên một tệp rác 50GB làm ngập lụt bộ nhớ S3 và tiêu tốn hàng nghìn USD (tấn công Denial-of-Wallet).
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Khóa cứng thông số vào thuật toán chữ ký AWS SigV4**:
      - Khi Backend sinh Presigned URL, chúng ta không sinh một URL mở. Chúng ta đưa trực tiếp hai thông số: **Dung lượng chính xác (`Content-Length`)** (chặn trần tối đa 200MB) và **Định dạng MIME (`Content-Type`)** vào yêu cầu ký của AWS SDK.
      - Thuật toán SigV4 lấy toàn bộ thông tin này, kết hợp với Secret Key của hệ thống, băm bằng thuật toán **HMAC-SHA256** để tạo ra một chữ ký số gắn trên URL, đồng thời ghi nhận 2 header này vào danh sách bắt buộc phải kiểm tra (`SignedHeaders`).
    - **S3 tự động xác thực và chặn đứng bằng 403**:
      - Khi trình duyệt gửi file lên S3, trình duyệt bắt buộc phải đính kèm các header tương ứng.
      - Cổng đón của AWS S3 nhận request sẽ tự băm lại dữ liệu dựa trên các header thực tế mà client gửi:
        - Nếu người dùng upload file quá dung lượng đã xin, header kích thước sẽ khác với chữ ký $\rightarrow$ Chuỗi băm không khớp $\rightarrow$ AWS S3 lập tức ngắt kết nối và trả về **`403 Forbidden (SignatureDoesNotMatch)`** ngay tại rìa mạng đám mây. Không một byte dữ liệu rác nào có thể lọt vào lưu trữ S3, và toàn bộ việc xác thực này do AWS tự xử lý, server chúng ta không tốn một chút tài nguyên nào.
        - Nếu người dùng gửi sai định dạng MIME $\rightarrow$ S3 cũng lập tức trả về 403.
      - **Kết quả**: Không một byte dữ liệu rác nào có thể lọt vào lưu trữ S3, và toàn bộ việc xác thực này do AWS tự xử lý, server chúng ta không tốn một chút tài nguyên nào.
    - **Phối hợp với CORS**: Để trình duyệt gửi được các header này sang S3 mà không bị lỗi mạng, ở tầng bucket S3 chúng ta cấu hình CORS mở quyền cho phép gửi 2 header `Content-Type` và `Content-Length`.

  ---

  #### 3. Nguyên Tắc Zero-Trust: Đọc Ranged Read & Soi "Magic Bytes"

  * **Vấn đề / Thách thức kỹ thuật**:
    - Sau khi upload lên S3 thành công, Client quay về Backend gọi API đăng ký bài hát và gửi kèm đường dẫn S3 Key.
    - Nguyên tắc thiết kế: **Tuyệt đối không tin Client**. 
      - *Nguy cơ 1 (IDOR)*: Client gửi key file của người dùng khác để cướp bài hát.
- *Nguy cơ 2 (Mã độc giả dạng)*: Kẻ xấu đổi tên file virus `trojan.exe` thành `nhac.mp3` rồi upload lên. Nếu sau này hệ thống phát bài hát đó cho người dùng khác tải về, hậu quả an ninh sẽ rất nghiêm trọng.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Chống IDOR & Path Traversal**: Backend kiểm tra nghiêm ngặt đường dẫn key: bắt buộc phải bắt đầu bằng tiền tố `audio/staging/{userId}/` của chính người dùng đang đăng nhập và cấm tuyệt đối ký tự lùi thư mục `..`.
    - **Xác thực dữ liệu thật từ S3**: Backend không tin các con số client khai báo. Backend chủ động hỏi trực tiếp S3 để kiểm tra file có tồn tại thật hay không và dung lượng thực tế là bao nhiêu.
    - **Kỹ thuật Đọc Ranged Read (bytes=0-15)**:
      - Để kiểm tra đúng là âm thanh hay không mà không phải tải cả file 200MB về server, Backend gửi một lệnh đọc dải (**HTTP Range Request**) lên S3, yêu cầu **chỉ lấy đúng 16 byte đầu tiên** của file (S3 trả về HTTP `206 Partial Content`).
    - **Giải mã chữ ký nhị phân (Magic Bytes)**:
      - Đem 16 byte đầu này đối chiếu với cấu trúc nhị phân chuẩn quốc tế:
        - File MP3: Bắt buộc phải có chữ ký thẻ dữ liệu `ID3` hoặc dấu đồng bộ khung `0xFF 0xE0`.
        - File WAV: Bắt buộc phải bắt đầu bằng 4 byte `RIFF`.
        - File FLAC: Bắt buộc phải bắt đầu bằng 4 byte `fLaC`.
      - Nếu là file `.exe` giả mạo, 2 byte đầu tiên của nó sẽ là `MZ`. Thuật toán phát hiện không khớp định dạng âm thanh $\rightarrow$ Lập tức ném lỗi từ chối và chặn đứng nguy cơ phát tán mã độc.

  ---

  #### 4. Thăng Hạng Tệp (Staging Promotion) & Quản Lý Transaction Đồng Bộ

  * **Vấn đề / Thách thức kỹ thuật**:
    - File mới tải lên đang nằm ở thư mục tạm thời `audio/staging/`. Làm sao để chuyển sang thư mục nhạc chính thức `audio/originals/` mà không kéo file qua server?
    - Nếu quá trình lưu thông tin bài hát vào Database bị lỗi (rollback), làm sao không làm mất file của người dùng và không để lại tệp rác mồ côi?
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **S3 Server-side Copy**: Backend phát lệnh yêu cầu AWS S3 copy nội bộ tệp từ `staging/` sang `originals/`. Thao tác này là lệnh điều khiển thuần túy, việc nhân bản dữ liệu diễn ra hoàn toàn nội bộ giữa các ổ cứng trong trung tâm dữ liệu của Amazon trong tích tắc.
    - **Đồng bộ hóa với vòng đời Database Transaction**:
      - Chúng ta tận dụng công cụ quản lý giao dịch của Spring (`TransactionSynchronizationManager`) để móc nối hành động:
        - **Kịch bản thành công**: Đăng ký một hook `afterCommit`. Chỉ sau khi lệnh lưu bài hát vào Database đã chốt sổ thành công 100%, hook này mới phát lệnh xóa file tạm ở `staging/`.
        - **Kịch bản Database bị Rollback**: Vì transaction bị hủy nên hook `afterCommit` không bao giờ được kích hoạt. File tạm ở `staging/` vẫn còn nguyên vẹn, đảm bảo an toàn dữ liệu cho người dùng nếu muốn thử lại.
        - **Kịch bản ngoại lệ Runtime**: Nếu câu lệnh INSERT vào Database bị lỗi, khối xử lý ngoại lệ lập tức gọi lệnh xóa ngay bản copy vừa tạo ở `originals/`, đảm bảo không bao giờ để lại file mồ côi chiếm dụng ổ đĩa.

  ---

  #### 5. Giải Quyết Triệt Để Tệp Bị Bỏ Rơi Bằng S3 Lifecycle Rule (Abandoned Uploads)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trường hợp người dùng xin URL, tải file 200MB lên S3 thành công, nhưng sau đó **tắt trình duyệt, rớt mạng hoặc đổi ý không bấm nút lưu bài hát**.
    - Lúc này file nằm trên S3 nhưng không hề có dòng nào trong Database trỏ tới. Backend không thể biết file này bị bỏ rơi lúc nào để viết code xóa. Nếu để nguyên thì hệ thống phải trả tiền lưu trữ cho tệp rác này vĩnh viễn.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Tách biệt ranh giới tiền tố (Prefix Isolation)**:
      - Thư mục `audio/originals/`: Chỉ chứa các bài hát đã đăng ký thành công trong Database, tuyệt đối không bị xóa tự động.
      - Thư mục `audio/staging/`: Chỉ chứa các tệp tải lên tạm thời chờ đăng ký.
    - **Tận dụng quy tắc vòng đời S3 (S3 Lifecycle Rule)**:
      - Thay vì phải dựng thêm bảng tạm trong Database và chạy Cronjob quét rác cực kỳ tốn CPU và I/O, chúng ta cấu hình trực tiếp trên hạ tầng AWS S3 một quy tắc: **Tự động xóa vĩnh viễn mọi đối tượng nằm trong tiền tố `audio/staging/` sau đúng 1 ngày (24 giờ)**.
      - Vì tất cả bài hát đăng ký thành công đều đã được thăng hạng sang `originals/`, nên bất kỳ tệp nào còn tồn tại ở `staging/` quá 1 ngày chắc chắn 100% là tệp bỏ dở.
      - Hạ tầng đám mây của AWS tự động dọn dẹp định kỳ mà không tốn một dòng code nghiệp vụ hay tài nguyên CPU nào của server.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Hệ thống của bạn xử lý việc tải lên các tệp âm thanh lớn như thế nào?"*, sếp có thể trả lời gãy gọn như sau:

  > *"Để tối ưu tài nguyên, bọn em áp dụng kiến trúc **Bypass Backend**:*
  > *1. Backend chỉ đóng vai trò cấp **Presigned PUT URL** với chữ ký số **AWS SigV4**, khóa cứng dung lượng tối đa và định dạng MIME. Nếu client gửi sai dung lượng hoặc định dạng, AWS S3 sẽ tự động từ chối với mã 403 ngay tại cửa, không tốn tài nguyên server.*
  > *2. Trình duyệt truyền thẳng dữ liệu nhị phân lên S3 vào vùng đệm tạm thời (`staging`).*
  > *3. Khi đăng ký bài hát, Backend áp dụng nguyên tắc **Zero-Trust**: chỉ đọc dải **16 byte đầu tiên (Ranged Read)** để soi chữ ký nhị phân (**Magic Bytes**), ngăn chặn mã độc giả mạo đuôi file mà không tốn băng thông kéo cả file về.*
  > *4. Sau khi kiểm định, Backend dùng **S3 Server-side Copy** để thăng hạng file sang thư mục chính thức, kết hợp với móc nối **Transaction Synchronization** để chỉ xóa file tạm sau khi Database đã commit an toàn.*
  > *5. Cuối cùng, để xử lý các file rác do người dùng tải lên rồi bỏ dở, bọn em cấu hình **S3 Lifecycle Rule** ở tầng hạ tầng để tự động dọn sạch thư mục staging sau 1 ngày mà không cần chạy bất kỳ cronjob tốn kém nào."*

- **Chi Tiết Phần 2: Transactional Outbox Trong Module Audio (Đảm Bảo Tính Nguyên Tử & Bắn Event Tức Thì)**:

  Khi phỏng vấn về việc phối hợp giữa Database và Message Queue (Kafka) trong xử lý tác vụ nặng, sếp có thể dẫn dắt câu chuyện qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Bài Toán Dual-Write & Triệt Tiêu Nguy Cơ Lệch Dữ Liệu (Atomic Consistency)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi người dùng đăng ký một bài hát có kèm Voice Tag, hệ thống cần làm 2 việc: (1) Lưu bài hát vào Database, và (2) Phát lệnh sang Kafka để Worker chạy ngầm (FFmpeg, LUFS) xử lý ghép nhạc.
    - **Cái bẫy Dual-Write (Ghi vào 2 hệ thống độc lập)**:
      - *Nếu gửi Kafka trước, ghi DB sau*: Lệnh gửi Kafka thành công nhưng DB bị đứt mạng hoặc lỗi ràng buộc dữ liệu nên rollback $\rightarrow$ Worker nhặt được event từ Kafka, nhảy vào DB tìm bài hát nhưng không thấy $\rightarrow$ Sinh ra lỗi "bài hát ma".
      - *Nếu ghi DB trước, gửi Kafka sau*: Ghi bài hát vào DB thành công (ở trạng thái `PROCESSING`), nhưng mạng sang Kafka Broker chập chờn đúng lúc bắn event $\rightarrow$ DB ghi nhận bài hát đang xử lý, nhưng Kafka không hề nhận được lệnh $\rightarrow$ Bài hát bị "treo vĩnh viễn" ở trạng thái `PROCESSING`, khách hàng chờ cả đời không thấy xong.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Quy tắc vàng**: Tuyệt đối không bao giờ gọi Kafka trực tiếp bên trong luồng xử lý API của Controller hay UseCase.
    - **Đóng gói nguyên tử (Single Database Transaction)**:
      - Chuyển trạng thái bài hát thành `PROCESSING`.
      - Ghi thông tin bài hát vào bảng `songs`.
      - Ghi thông số lồng tiếng vào bảng `song_tag_configs`.
      - Đóng gói sự kiện `SongProcessingRequested` thành chuỗi dữ liệu và ghi một dòng mới vào bảng `outbox_events` với trạng thái `PENDING`.
      - **Bản chất kỹ thuật**: Toàn bộ các thao tác trên đều dùng chung **một kết nối Database** và nằm chung trong **một Transaction duy nhất**. Hệ cơ sở dữ liệu quan hệ (PostgreSQL) đảm bảo tính toàn vẹn tuyệt đối (ACID): hoặc tất cả cùng được ghi nhận vào đĩa cứng, hoặc nếu có sự cố thì tất cả cùng rollback, triệt tiêu 100% nguy cơ lệch dữ liệu.

  ---

  #### 2. Kỹ Thuật "In-Memory Nudge" — Triệt Tiêu Độ Trễ Polling Của Outbox Truyền Thống

  * **Vấn đề / Thách thức kỹ thuật**:
    - Nhược điểm lớn nhất của Transactional Outbox truyền thống là dựa vào một tiến trình quét định kỳ (Scheduler Polling) mỗi 3 đến 5 giây để kiểm tra bảng `outbox_events`.
    - Điều này tạo ra một "độ trễ chết" (Dead Latency): Người dùng bấm tạo bài hát xong, dù hệ thống hoàn toàn rảnh rỗi nhưng bài hát vẫn phải nằm im trong DB mất vài giây chờ Scheduler thức dậy mới được gửi sang Kafka, gây ức chế trải nghiệm người dùng.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Móc nối vòng đời Transaction (Spring Transaction Synchronization)**:
      - Ngay khi bản ghi được thêm vào bảng `outbox_events`, hệ thống đăng ký một bộ lắng nghe sự kiện cam kết giao dịch (`afterCompletion`).
      - Bộ lắng nghe này kiên nhẫn chờ đợi: Chỉ đúng vào khoảnh khắc PostgreSQL phát tín hiệu `COMMITTED` (dữ liệu đã an toàn trên đĩa), nó lập tức phát ra một tín hiệu đánh thức nội bộ trong bộ nhớ (**In-Memory Nudge**).
    - **Bắn Kafka tức thì**:
      - Tín hiệu Nudge này ném một nhiệm vụ vào một Thread Pool riêng biệt (`outboxPublishExecutor`), lập tức gọi tiến trình Relay nhặt bản ghi vừa commit và bắn thẳng sang Kafka.
      - **Kết quả**: Độ trễ từ lúc người dùng bấm nút đến khi Kafka nhận được event giảm từ **5 giây xuống chỉ còn vài mili-giây** (Zero Polling Delay), trong khi vẫn giữ trọn vẹn sự an toàn tuyệt đối của Outbox. Tiến trình quét định kỳ 5 giây vẫn được duy trì ngầm, nhưng chỉ đóng vai trò là "lưới đỡ an toàn" phòng khi có sự cố.

  ---

  #### 3. Xử Lý Tranh Chấp Đa Tiến Trình Khi Scale Ngang (`FOR UPDATE SKIP LOCKED`)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong môi trường Production thực tế, hệ thống Backend chạy song song từ 3 đến 10 máy chủ (Replicas/Instances).
    - Nếu tất cả các máy chủ cùng chạy tiến trình quét Outbox để đẩy sang Kafka:
      - Làm sao để 2 máy chủ không nhặt trùng một event để tránh bắn lặp tin nhắn lên Kafka?
      - Nếu dùng cơ chế khóa dòng thông thường (`FOR UPDATE`), Máy chủ B sẽ bị "đứng hình" (chờ đợi khóa của Máy chủ A giải phóng), gây nghẽn cổ chai cơ sở dữ liệu và làm sụt giảm nghiêm trọng thông lượng xử lý.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Kỹ thuật `FOR UPDATE SKIP LOCKED` ở tầng Database**:
      - Khi một máy chủ thực hiện nhặt một mẻ (batch) sự kiện `PENDING`, câu truy vấn SQL của chúng ta áp dụng cơ chế khóa dòng nâng cao `FOR UPDATE SKIP LOCKED`.
      - **Cách vận hành**: Máy chủ A chọn 50 dòng đầu tiên và khóa lại. Khi Máy chủ B chạy câu truy vấn cùng lúc, thay vì phải dừng lại chờ đợi, hệ quản trị cơ sở dữ liệu PostgreSQL sẽ **tự động nhảy cóc qua 50 dòng đang bị Máy chủ A khóa** để nhặt ngay 50 dòng tiếp theo còn rảnh rỗi.
      - Không có bất kỳ tiến trình nào phải chờ đợi, không có nghẽn khóa (Deadlock), các máy chủ chia việc song song hoàn hảo và tăng tốc độ xử lý theo cấp số nhân khi mở rộng hạ tầng.

  ---

  #### 4. Cơ Chế Thu Hồi Khóa Hết Hạn Khi Server Đột Tử (Distributed Lease / Crash Recovery)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Giả sử Máy chủ A nhặt được một mẻ event, cập nhật trạng thái các event đó từ `PENDING` sang `PROCESSING` để chuẩn bị gửi sang Kafka.
    - Đột nhiên, Máy chủ A bị "chết bất đắc kỳ tử" (sập nguồn, tràn RAM bị hệ điều hành tắt tiến trình, hoặc mất mạng nội bộ) trước khi kịp gửi event sang Kafka.
    - Lúc này, các event đã bị đổi sang `PROCESSING` nên không máy chủ nào thèm nhặt nữa. Chúng sẽ bị "mắc kẹt vĩnh viễn" ở trạng thái đang xử lý và bài hát của khách hàng sẽ không bao giờ được ghép nhạc.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Mô hình Khóa thuê có thời hạn (Distributed Lease Pattern)**:
      - Khi một máy chủ nhận việc, ngoài việc đổi trạng thái sang `PROCESSING`, nó bắt buộc phải đóng dấu thêm một mốc thời gian hết hạn thuê: `lease_until = Hiện tại + 60 giây`.
      - Trong điều kiện bình thường, việc bắn sang Kafka chỉ tốn vài mili-giây, sau đó event lập tức được cập nhật thành công (`SENT`).
    - **Tự động phục hồi sự cố (Crash Recovery)**:
      - Nếu máy chủ bị sập và quá 60 giây mà event vẫn kẹt ở `PROCESSING`, mốc thời gian `lease_until` sẽ trở thành quá khứ.
      - Tiến trình quét định kỳ của các máy chủ còn sống có một tác vụ riêng biệt: **Tìm và thu hồi các sự kiện hết hạn thuê (`reclaimExpiredLease`)**. Nó sẽ tự động tước lại quyền xử lý của các event đã quá hạn và giao cho máy chủ khỏe mạnh gửi lại lên Kafka.
      - Đảm bảo cam kết kỹ thuật quan trọng nhất của hệ phân tán: **Không bao giờ làm rơi rớt sự kiện (At-Least-Once Delivery Guarantee)**.

  ---

  #### 5. Đảm Bảo Thứ Tự Xử Lý Tuyệt Đối Qua Kafka Partition Key (`songId`)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong quá trình vận hành, người dùng tạo bài hát $\rightarrow$ hệ thống bắn sự kiện yêu cầu xử lý. Nếu bài hát bị lỗi, người dùng bấm nút "Thử lại" (`retryProcessing`) $\rightarrow$ hệ thống bắn tiếp một sự kiện xử lý lần 2.
    - Trong mô hình nhiều Worker chạy bất đồng bộ, nếu không kiểm soát thứ tự, lệnh "Thử lại" có thể bị Worker khác nhặt và chạy trước lệnh "Tạo bài hát" ban đầu, dẫn đến tình trạng tranh chấp tài nguyên hoặc xung đột trạng thái dữ liệu.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Định tuyến phân vùng có chủ đích (Partition Routing)**:
      - Khi đẩy sự kiện từ bảng Outbox sang topic Kafka `voice-processing-requests`, chúng ta bắt buộc sử dụng **Message Key chính là `songId`**.
      - **Cơ chế băm của Kafka**: Kafka sử dụng thuật toán băm (MurmurHash2) trên Message Key để quyết định message đó rơi vào Partition nào của topic. Vì tất cả các sự kiện của cùng một bài hát đều mang chung một `songId`, chúng sẽ **100% rơi vào cùng một Partition duy nhất**.
      - **Kết quả**: Bên trong một Partition, Kafka bảo đảm cấu trúc hàng đợi tuần tự nghiêm ngặt (FIFO — First-In First-Out). Mọi hành động liên quan đến bài hát đó bắt buộc phải được xử lý đúng thứ tự thời gian, triệt tiêu hoàn toàn nguy cơ chạy lộn xộn giữa lệnh cũ và lệnh mới.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào để hệ thống của bạn đảm bảo việc phát sinh sự kiện sang Kafka không bị mất mát dữ liệu và không bị chậm trễ?"*, sếp có thể trả lời trôi chảy như sau:

  > *"Để giải quyết bài toán Dual-Write giữa Database và Kafka, bọn em áp dụng **Transactional Outbox Pattern** với các cải tiến nâng cao:*
  > *1. Khi tạo bài hát, trạng thái bài hát và sự kiện Outbox được ghi đồng thời vào PostgreSQL trong **cùng một Transaction nguyên tử**. Tuyệt đối không bắn Kafka trực tiếp từ API để tránh rủi ro mất đồng bộ dữ liệu.*
  > *2. Để triệt tiêu độ trễ 5 giây của việc quét polling truyền thống, bọn em dùng kỹ thuật **In-Memory Nudge** móc nối với hook `afterCompletion` của Spring. Ngay khi Database vừa commit xong, một tín hiệu nội bộ lập tức kích hoạt luồng riêng đẩy event sang Kafka trong vài mili-giây.*
  > *3. Khi hệ thống mở rộng nhiều server, câu truy vấn nhặt event sử dụng cơ chế **`FOR UPDATE SKIP LOCKED`**, giúp các server chia sẻ công việc song song mà không tranh chấp hay nghẽn khóa.*
  > *4. Bọn em triển khai mô hình **Distributed Lease (khóa thuê 60s)**: Nếu server đang xử lý bị sập giữa chừng, server khác sẽ tự động thu hồi event quá hạn để gửi lại, đảm bảo cam kết **At-Least-Once Delivery**.*
  > *5. Cuối cùng, mọi event của bài hát đều được gắn Partition Key là **`songId`**, đảm bảo Kafka luôn xử lý các thông điệp của cùng một bài hát theo đúng thứ tự tuần tự tuyệt đối."*

- **Chi Tiết Phần 3: Kafka Event Streaming & Kỹ Thuật Chống Rebalance (Xử Lý Tác Vụ Âm Thanh Nặng Trên Kafka)**:

  Khi phỏng vấn về việc dùng Message Queue (Kafka/RabbitMQ) cho các tác vụ tốn CPU và thời gian dài (như xử lý media, encode video, render âm thanh), sếp có thể làm chủ cuộc đối thoại bằng **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Thảm Họa "Rebalance Storm" Khi Tác Vụ Chạy Quá Lâu & Cách Triệt Tiêu

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khác với tác vụ gửi email hay thông báo chỉ tốn vài mili-giây, việc xử lý âm thanh (tải file, chạy FFmpeg lồng tiếng, chuẩn hóa âm lượng LUFS) tốn từ **vài chục giây đến vài phút**.
    - **Cơ chế mặc định của Kafka**:
      - Kafka Consumer định kỳ kéo tin nhắn bằng hàm `poll()`.
      - Kafka có một thông số sống còn là `max.poll.interval.ms` (mặc định là 5 phút). Nếu luồng Consumer bận chạy FFmpeg và không gọi lại `poll()` trước thời hạn này, Kafka Broker sẽ coi Worker này **đã bị treo hoặc đột tử (Dead/Stuck)**.
    - **Hậu quả dây chuyền (Rebalance Storm)**:
      - Broker lập tức đá Worker đó ra khỏi Consumer Group và kích hoạt **Rebalance** (tái phân bổ Partition) để giao Partition đó cho Worker thứ hai.
      - Worker thứ hai lại nhặt đúng message đó và bắt đầu chạy lại FFmpeg từ đầu. Trong khi đó, Worker thứ nhất thực ra **vẫn đang sống** và vẫn đang ngốn 100% CPU để render bài hát.
      - Hai Worker cùng tranh chấp CPU cho cùng một bài hát. Khi Worker thứ hai cũng bị quá giờ, Broker lại kích hoạt Rebalance sang Worker thứ ba... Toàn bộ cụm Consumer bị cuốn vào vòng xoáy Rebalance liên tục, sập toàn bộ hệ thống xử lý ngầm.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Chốt trần một thông điệp (`max.poll.records = 1`)**:
      - Mặc định Kafka rút 500 bản ghi mỗi lần poll. Nếu rút 500 bài hát, Worker sẽ bị kẹt hàng tiếng đồng hồ.
      - Chúng ta ép cứng cấu hình mỗi lần poll chỉ lấy đúng **1 thông điệp duy nhất**. Luồng xử lý dành trọn 100% tài nguyên và thời gian cho duy nhất một bài hát.
    - **Nới rộng trần thời gian (`max.poll.interval.ms = 30 phút`)**:
      - Chúng ta nâng ngưỡng kiểm tra thời gian chờ của Kafka lên **30 phút (1,800,000 mili-giây)**, vượt xa thời gian chạy tối đa của bất kỳ bài hát dài nào (kể cả file nhạc dài 15–20 phút).
      - **Kết quả**: Kafka Broker hoàn toàn yên tâm chờ đợi Worker hoàn thành việc ghép nhạc mà không bao giờ kích hoạt Rebalance nhầm, triệt tiêu 100% thảm họa Rebalance Storm.

  ---

  #### 2. Bảo Tồn Connection Pool: Tuyệt Đối KHÔNG Dùng `@Transactional` Bọc Worker

  * **Vấn đề / Thách thức kỹ thuật**:
    - Một lỗi kiến trúc rất phổ biến của lập trình viên là gắn `@Transactional` lên toàn bộ phương thức xử lý `process()` của Worker để "cho an toàn".
    - Việc tải file từ S3, chạy FFmpeg trên đĩa, và upload lại file kết quả lên S3 mất từ 30 giây đến 2 phút.
    - Nếu bọc `@Transactional`, một kết nối Database từ Connection Pool (HikariCP) sẽ bị **chiếm giữ và giam lỏng trong suốt 2 phút đó** dù bản chất lúc đó CPU và ổ đĩa đang làm việc chứ Database hoàn toàn không làm gì!
    - Chỉ cần 10 bài hát được xử lý đồng thời là toàn bộ 10 kết nối trong Connection Pool bị cạn kiệt (Connection Pool Starvation). Toàn bộ hệ thống web, từ API đăng nhập, xem danh sách bài hát cho đến thanh toán, sẽ bị nghẽn cứng vì không còn kết nối nào để truy vấn Database.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Mô hình Giao dịch Vi mô (Micro-Transactions)**:
      - Worker được thiết kế **hoàn toàn KHÔNG mang `@Transactional`**.
      - Thay vào đó, quy trình được bẻ nhỏ thành 3 bước độc lập:
        - **Bước 1 (Đọc dữ liệu nhanh)**: Mở một transaction chỉ đọc (`loadPending`) kéo dài đúng **2 mili-giây** để lấy thông tin bài hát và cấu hình Voice Tag, sau đó **nhả kết nối trả về HikariCP ngay lập tức**.
        - **Bước 2 (Xử lý tốn thời gian)**: Tải file từ S3 về đĩa tạm, gọi tiến trình FFmpeg ngoài hệ điều hành ghép nhạc, upload file thành phẩm lên S3 $\rightarrow$ **Hoàn toàn KHÔNG chiếm giữ bất kỳ kết nối Database nào**.
        - **Bước 3 (Chốt kết quả)**: Mở một transaction cực ngắn (`markProcessed`) trong **2 mili-giây** để cập nhật trạng thái bài hát sang `PROCESSED` và lưu đường dẫn file thành phẩm, rồi nhả kết nối ngay.
      - **Kết quả**: Cho dù hàng trăm Worker có render nhạc hàng tiếng đồng hồ thì Database Connection Pool vẫn luôn rảnh rỗi và hệ thống API phục vụ người dùng vẫn mượt mà 100%.

  ---

  #### 3. Chống Xử Lý Trùng Lặp (Idempotent Consumer)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Kafka chỉ bảo đảm cam kết gửi **Ít nhất một lần (At-Least-Once Delivery)**. Khi mạng nội bộ chập chờn hoặc offset commit bị chậm, việc một message bị gửi lặp lại lần thứ hai là điều chắc chắn xảy ra trong hệ phân tán.
    - Nếu hệ thống nhận lại message cũ mà không có cơ chế bảo vệ, Worker sẽ ngây thơ tải file và chạy lại FFmpeg một lần nữa, gây lãng phí CPU khổng lồ và nguy cơ ghi đè dữ liệu đang phát của người dùng.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Chốt chặn trạng thái 2 tầng (Two-Phase State Guard)**:
      - **Chốt chặn đầu vào (Trước khi tốn CPU)**: Ở Bước 1, khi nạp bài hát từ Database, Worker kiểm tra trạng thái: nếu bài hát không còn ở trạng thái `PROCESSING` (ví dụ: đã được một Worker khác xử lý xong, hoặc đã bị người dùng hủy), Worker lập tức bỏ qua và thoát ngay, không tốn 1 chu kỳ CPU nào cho việc chạy FFmpeg.
      - **Chốt chặn đầu ra (Trước khi ghi đè dữ liệu)**: Ở Bước 3, sau khi render xong, hàm chốt kết quả kiểm tra lại một lần nữa: nếu bài hát đã ở trạng thái `PROCESSED`, hệ thống nhận diện đây là kết quả xử lý trùng lặp và nhẹ nhàng bỏ qua, giữ nguyên vẹn tệp âm thanh chuẩn đang phát, không ghi đè bậy bạ.

  ---

  #### 4. Xử Lý Tình Huống Biên: Người Dùng Xóa Bài Hát Khi FFmpeg Đang Chạy

  * **Vấn đề / Thách thức kỹ thuật**:
    - Người dùng bấm tạo bài hát $\rightarrow$ Worker nhặt task và bắt đầu chạy FFmpeg (dự kiến mất 45 giây).
    - Ở giây thứ 10, người dùng đổi ý và bấm nút "Xóa bài hát" trên giao diện. API Xóa đã xóa sạch bản ghi bài hát trong Database và xóa file gốc trên S3.
    - Đến giây thứ 45, tiến trình FFmpeg của Worker hoàn thành và đẩy tệp đã ghép lên S3 (`audio/processed/...`).
    - Lúc này, bài hát trong Database đã biến mất từ lâu. Tệp âm thanh thành phẩm vừa upload lên S3 không có ai trỏ tới và sẽ trở thành **"tệp rác mồ côi"** nằm vĩnh viễn trên đám mây, tiêu tốn tiền lưu trữ hàng tháng mà không code nào tìm ra để xóa.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Cơ chế phát hiện và dọn rác mồ côi tức thì (Orphan Detection & Disposal)**:
      - Khi Worker hoàn tất việc ghép nhạc và gọi hàm `markProcessed` để lưu kết quả vào Database:
        - Nếu kiểm tra thấy bài hát **không còn tồn tại trong Database** (đã bị người dùng xóa mất trong lúc render), hàm lập tức trả về cờ cảnh báo: `orphaned = true`.
      - Ngay khi nhận được cờ này, Worker hiểu rằng đây là một sản phẩm vô thừa nhận. Nó lập tức gọi bộ dọn dẹp hạ tầng (`storageCleaner.deleteNow(outputKey)`) để **phát lệnh xóa ngay lập tức tệp vừa upload lên S3**.
      - **Kết quả**: Kho lưu trữ S3 luôn sạch bóng, không bao giờ để lọt tệp mồ côi kể cả trong các tình huống người dùng thao tác bất thường nhất.

  ---

  #### 5. Cách Ly Thất Bại Độc Lập Bằng `Propagation.REQUIRES_NEW`

  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu quá trình ghép nhạc bị thất bại (ví dụ: file âm thanh của người dùng bị lỗi định dạng ở giữa bài khiến FFmpeg crash, hoặc S3 bị ngắt kết nối giữa chừng):
    - Làm thế nào để lưu lại thông tin lỗi chi tiết vào Database để người dùng nhìn thấy trên giao diện (và có thể bấm nút Thử lại), mà việc ghi lỗi này không bị rollback theo tiến trình chính bị hỏng?
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Giao dịch độc lập cách ly (`Propagation.REQUIRES_NEW`)**:
      - Khi Worker bắt được bất kỳ ngoại lệ Runtime nào, nó chuyển sang khối xử lý thất bại `markFailed`.
      - Phương thức này được thiết lập mức độ lan truyền giao dịch độc lập (`REQUIRES_NEW`): Nó tự động tách rời khỏi mọi ngữ cảnh cũ, mượn một kết nối mới và thực hiện một giao dịch riêng biệt để chuyển trạng thái bài hát sang `FAILED`, đồng thời lưu nguyên văn lý do lỗi vào cột mô tả lỗi.
      - Giao dịch này cam kết (`COMMIT`) ngay lập tức vào Database. Nhờ đó, người dùng mở trang web lên sẽ thấy ngay thông báo bài hát bị lỗi gì và nút "Thử lại" (`retryProcessing`) sáng lên.
      - Sau khi đã ghi nhận lỗi an toàn vào Database, Worker mới ném tiếp ngoại lệ ra ngoài để hệ thống giám sát (Monitoring/Logging) ghi nhận cảnh báo cho đội ngũ kỹ thuật.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế Kafka Consumer cho các tác vụ xử lý tốn nhiều phút như render âm thanh hoặc video mà không bị lỗi hệ thống?"*, sếp có thể trả lời đầy tự tin và khúc chiết như sau:

  > *"Khi tích hợp Kafka với các tác vụ nặng chạy FFmpeg kéo dài nhiều phút, bọn em áp dụng 4 nguyên tắc kỹ thuật cốt lõi:*
  > *1. **Chống Rebalance Storm**: Bọn em cấu hình `max.poll.records = 1` để mỗi lần poll Worker chỉ nhận đúng 1 bài hát, kết hợp nới rộng `max.poll.interval.ms` lên **30 phút** để Kafka Broker không bao giờ hiểu nhầm Worker bị chết khi FFmpeg đang bận render.*
  > *2. **Bảo tồn Connection Pool**: Hàm Worker tuyệt đối **không gắn `@Transactional`** để tránh giam giữ kết nối Database trong suốt thời gian render. Thay vào đó, bọn em chia thành các Transaction vi mô: chỉ mượn kết nối vài mili-giây để đọc và ghi kết quả, còn toàn bộ thời gian chạy FFmpeg thì nhả kết nối hoàn toàn.*
  > *3. **Đảm bảo Idempotency**: Kiểm tra trạng thái bài hát ở cả 2 đầu vào và đầu ra để bỏ qua ngay các message gửi lặp của Kafka, không lãng phí CPU.*
  > *4. **Xử lý tệp mồ côi**: Nếu người dùng xóa bài hát trong lúc FFmpeg đang chạy, khi render xong hệ thống sẽ phát hiện bản ghi trong DB đã mất và lập tức xóa ngay tệp thành phẩm vừa upload lên S3 để chống rác ổ đĩa."*

- **Chi Tiết Phần 4: Quản Trị Tài Nguyên (Connection Pool & Ổ Cứng Tạm)**:

  Khi phỏng vấn về việc vận hành các công cụ dòng lệnh native (như FFmpeg, ImageMagick, LibreOffice) trên server ứng dụng, rủi ro lớn nhất không nằm ở thuật toán mà nằm ở **tràn ổ cứng, treo tiến trình (zombie process) và nghẽn tài nguyên hệ điều hành**. Sếp có thể dẫn dắt câu chuyện qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Bài Toán Rò Rỉ Ổ Cứng (Disk Leaks) & Cơ Chế Không Gian Làm Việc Cô Lập

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi xử lý một bài hát: Worker phải tải file gốc từ S3 về, tải file voice tag về, chạy FFmpeg sinh ra file output và các file bộ đệm trung gian. Tổng dung lượng đĩa tạm tiêu tốn thường **gấp 2.5 đến 3 lần dung lượng file gốc** (một bài 100MB sẽ ngốn khoảng 250MB – 300MB đĩa).
    - Nếu xử lý hàng nghìn bài hát mỗi ngày mà chỉ cần một vài tác vụ bị lỗi không dọn dẹp sạch sẽ, ổ cứng của máy chủ sẽ bị lấp đầy chỉ sau vài ngày $\rightarrow$ Hệ điều hành không còn chỗ ghi log/swap, dẫn đến sập toàn bộ máy chủ.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Không gian làm việc cô lập theo Job (Isolated Job Directory)**:
      - Mỗi tác vụ xử lý bài hát được cấp một thư mục tạm hoàn toàn riêng biệt theo định dạng: `job-{songId}-{randomId}` bên trong thư mục làm việc gốc.
      - Toàn bộ file tải về, file trung gian và file xuất ra đều bị "nhốt" bên trong thư mục riêng biệt này, không dùng chung với bất kỳ tiến trình nào khác.
    - **Dọn dẹp tuyệt đối qua khối Bảo đảm (`finally`)**:
      - Toàn bộ quy trình xử lý được bọc trong khối điều khiển vòng đời. Bất kể việc ghép nhạc thành công, thất bại, hay bị ném ngoại lệ bất ngờ, khối `finally` luôn luôn được kích hoạt để gọi bộ giải phóng không gian (`workspace.release(...)`), duyệt cây thư mục và xóa sạch đệ quy 100% các file tạm trong thư mục job đó.

  ---

  #### 2. Cơ Chế Tiên Đoán & Thẩm Định Ổ Đĩa Trước Khi Chạy (Pre-Flight Disk Check)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu ổ cứng máy chủ chỉ còn lại 50MB trống, nhưng Worker vẫn nhắm mắt nhặt một bài hát 100MB về để xử lý:
    - Tiến trình FFmpeg đang chạy dở sẽ bị hệ điều hành bắn hạ vì lỗi `No space left on device` (Hết dung lượng đĩa). Hậu quả là tệp âm thanh bị hỏng, CPU bị lãng phí vô ích, và tình trạng cạn kiệt đĩa có thể kéo theo các dịch vụ khác trên máy chủ (như Nginx, Docker) bị dừng đột ngột.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Công thức tiên đoán dung lượng cần thiết**:
      - Trước khi tải bất kỳ byte dữ liệu nào về máy, Worker đọc metadata trên S3 để lấy dung lượng file gốc và tính toán lượng đĩa cọ xát (scratch space) ước tính: **`Dung lượng cần = Dung lượng file gốc * 2.5`**.
    - **Thẩm định trực tiếp với hệ điều hành (OS Usable Space Check)**:
      - Hệ thống gọi trực tiếp API tầng hệ điều hành để kiểm tra dung lượng thực tế còn khả dụng (`getUsableSpace`) trên phân vùng ổ đĩa chứa thư mục làm việc.
      - Nếu dung lượng khả dụng nhỏ hơn dung lượng ước tính, hệ thống **lập tức từ chối chạy ngay từ cửa** và ném lỗi `INSUFFICIENT_DISK_SPACE`.
      - **Ý nghĩa kiến trúc**: Chặn đứng sự cố từ sớm (Fail-Fast), bảo vệ an toàn cho hệ điều hành và nhường tài nguyên cho các tác vụ nhẹ hơn tiếp tục vận hành.

  ---

  #### 3. Xử Lý Tiến Trình Treo & Khóa File Hệ Điều Hành (Process Timeout & File Locks)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Một file âm thanh bị lỗi định dạng sâu hoặc có cấu trúc phân mảnh độc hại có thể khiến thuật toán giải mã của FFmpeg bị rơi vào **vòng lặp vô tận (Infinite Loop)** hoặc bị treo đơ.
    - Nếu gọi FFmpeg theo cách đồng bộ thông thường, Worker Thread của Java sẽ bị "đóng băng" vĩnh viễn, không bao giờ nhả luồng.
    - Đặc biệt, trên các hệ điều hành (nhất là Windows và một số file system Linux), khi tiến trình FFmpeg còn sống, nó nắm giữ **khóa tập tin (File Lock)** trên file output. Nếu Java vội vàng xóa thư mục tạm trong khối `finally`, hệ điều hành sẽ từ chối xóa và báo lỗi `AccessDeniedException` (File đang được sử dụng bởi tiến trình khác).
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Thực thi bất đồng bộ có kiểm soát thời gian**:
      - Lệnh gọi FFmpeg được bọc trong một Future bất đồng bộ (`executeAsync()`).
      - Java áp đặt một trần thời gian Timeout cứng (mặc định là 15 phút). Luồng Java chỉ chờ tối đa 15 phút.
    - **Quy trình "Cưỡng chế Dừng & Chờ nhả khóa" (Force Stop & Grace Period)**:
      - Nếu quá 15 phút mà FFmpeg chưa xong, Java kích hoạt quy trình cưỡng chế dừng: gọi lệnh `forceStop()` để yêu cầu hệ điều hành tiêu diệt tiến trình FFmpeg.
      - Quan trọng nhất: Hệ thống cấp thêm một **khoảng thời gian ân hạn 10 giây (`ABORT_GRACE_SECONDS`)** để chờ đợi hệ điều hành thực sự thu hồi tiến trình và giải phóng toàn bộ File Handle.
      - Sau khi File Handle đã được nhả hoàn toàn, khối `finally` mới tiến hành xóa sạch thư mục job, đảm bảo việc dọn dẹp luôn thành công 100% mà không bao giờ bị lỗi khóa file.

  ---

  #### 4. Cơ Chế Quét Dọn Tệp Mồ Côi Khi Khởi Động (Startup Orphan Sweep)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong thực tế, máy chủ có thể bị mất điện đột ngột, server bị khởi động lại, hoặc tiến trình Java bị hệ điều hành "bắn hạ" tức thì bằng tín hiệu `SIGKILL` (do Out-Of-Memory Killer) ngay lúc FFmpeg đang chạy dở.
    - Khi bị `SIGKILL`, khối `finally` của Java **hoàn toàn không có cơ hội được thực thi**.
    - Kết quả là các thư mục `job-*` đang chạy dở sẽ bị bỏ lại trên ổ đĩa. Nếu máy chủ khởi động lại nhiều lần, các tệp rác này sẽ tích tụ dần thành hàng chục GB rác vô thừa nhận.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Quét rác tự động ở vòng đời khởi động (`@PostConstruct`)**:
      - Chúng ta tận dụng hook khởi tạo ứng dụng (`sweepOrphans()`) chạy ngay khi ứng dụng Spring Boot vừa bật lên.
      - Hệ thống tự động quét toàn bộ thư mục làm việc, nhận diện tất cả các thư mục có tiền tố `job-*` và kiểm tra mốc thời gian sửa đổi cuối cùng (`LastModifiedTime`).
      - Nếu thư mục nào có tuổi thọ vượt quá ngưỡng quy định (ví dụ: đã nằm đó hơn 2 giờ), hệ thống xác định 100% đây là tệp rác bỏ dở từ các phiên chạy bị sập trước đó và **tự động xóa sổ đệ quy ngay lập tức**.
      - **Kết quả**: Mỗi lần ứng dụng khởi động lại, ổ đĩa luôn được tự động làm sạch tinh khôi mà không cần con người phải SSH vào server để dọn dẹp thủ công.

  ---

  #### 5. Chiến Lược Tách Rời Tài Nguyên: Connection Pool vs CPU Throttling

  * **Vấn đề / Thách thức kỹ thuật**:
    - Xử lý âm thanh là tác vụ tiêu tốn 100% công suất CPU (CPU-bound) và đọc/ghi đĩa (Disk I/O-bound). Trong khi đó, việc phục vụ Web/API lại là tác vụ tiêu tốn kết nối mạng và Database Connection Pool (Network/DB-bound).
    - Nếu lập trình viên cấu hình Worker chạy song song quá nhiều luồng (ví dụ cho 20 Worker cùng chạy FFmpeg một lúc), CPU của server sẽ chạm ngưỡng 100%, gây nghẽn toàn bộ hệ điều hành, làm chậm các truy vấn Database của người dùng bình thường.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Điều tiết số luồng xử lý theo năng lực phần cứng (Concurrency Throttling)**:
      - Số lượng Worker chạy FFmpeg đồng thời được khống chế chặt chẽ dựa trên số lõi CPU thực tế của máy chủ (thường để lại ít nhất 2 lõi CPU cho các tác vụ hệ thống và phục vụ API).
    - **Tách bạch hoàn toàn khỏi Database Connection Pool**:
      - Kết hợp với kiến trúc ở Phần 3: Toàn bộ quá trình FFmpeg cày xới CPU và ổ cứng đều **hoàn toàn không giữ bất kỳ kết nối nào trong HikariCP**.
      - Nhờ đó, dù CPU của máy chủ có đang tập trung cao độ để render nhạc thì các truy vấn nghiệp vụ của người dùng khác (đăng nhập, lướt web, nghe nhạc) vẫn có sẵn kết nối Database để phản hồi trong vài mili-giây, không bị ảnh hưởng chéo lẫn nhau.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào để hệ thống của bạn quản trị tài nguyên máy chủ (ổ đĩa, tiến trình, DB Connection) an toàn khi chạy các công cụ nặng như FFmpeg?"*, sếp có thể trả lời đầy chuyên nghiệp như sau:

  > *"Để đảm bảo máy chủ không bao giờ bị sập vì tràn đĩa hay treo tiến trình khi chạy FFmpeg, bọn em triển khai 4 tầng bảo vệ tài nguyên:*
  > *1. **Thẩm định đĩa trước khi chạy**: Bọn em ước lượng dung lượng cần thiết bằng 2.5 lần dung lượng file gốc và kiểm tra trực tiếp với dung lượng khả dụng của hệ điều hành. Nếu thiếu đĩa, hệ thống từ chối chạy ngay từ đầu (Fail-Fast).*
  > *2. **Cô lập không gian làm việc**: Mỗi bài hát được cấp một thư mục tạm riêng biệt và luôn được dọn dẹp đệ quy trong khối `finally` ngay sau khi render xong.*
  > *3. **Kiểm soát Timeout và Khóa file**: Áp trần thời gian chạy tối đa 15 phút. Nếu quá giờ, hệ thống gọi `forceStop()` và cấp thời gian ân hạn 10 giây để OS nhả hoàn toàn File Handle rồi mới xóa tệp, tránh lỗi khóa file.*
  > *4. **Dọn rác tự động khi khởi động**: Mỗi khi server khởi động lại, hệ thống tự động quét và xóa sạch các thư mục job cũ còn sót lại từ các vụ sập nguồn trước đó.*
  > *5. **Cách ly Connection Pool**: Tuyệt đối không giữ kết nối Database trong suốt thời gian FFmpeg cày đĩa và CPU, giúp hệ thống web luôn mượt mà."*

- **Chi Tiết Phần 5: Xử Lý Tín Hiệu Âm Thanh FFmpeg & LUFS Loudness Matching (Công Nghệ Ghép Nhạc Bản Quyền Chuyên Nghiệp)**:

  Khi phỏng vấn về việc xử lý tín hiệu âm thanh thực tế với FFmpeg, sự khác biệt giữa một lập trình viên thông thường và một kỹ sư Senior/Audio Specialist nằm ở chỗ: **Hiểu rõ bản chất âm học (Acoustics), hiện tượng vỡ tiếng (Clipping), và tiêu chuẩn đo lường năng lượng âm thanh (LUFS)**. Sếp có thể làm chủ chủ đề này qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Bài Toán "Voice Tag Bị Chìm Nghỉm" & Chuẩn Hóa Năng Lượng LUFS (EBU R128)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi người dùng ghép một lời đọc bản quyền ("Bản quyền thuộc về...") vào bài hát, dù họ có kéo âm lượng Voice Tag lên 100% thì khi nghe lại, tiếng nói vẫn bị tiếng nhạc đè bẹp dí, gần như không thể nghe rõ.
    - **Bản chất âm học (Peak Level vs Perceived Loudness)**:
      - Một bài hát thương mại đã qua khâu Master của các phòng thu chuyên nghiệp luôn được nén động lực học (Dynamic Compression) rất chặt, năng lượng âm thanh trung bình rất lớn, đạt mức từ **-9 đến -12 LUFS** (Loudness Units Full Scale).
      - Trong khi đó, file giọng đọc Voice Tag (thu bằng mic thường hoặc sinh ra từ AI Text-to-Speech) chỉ có năng lượng quanh mức **-20 đến -27 LUFS**.
      - Khoảng cách giữa 2 file là từ **10 dB đến 15 dB**. Trong âm học, cứ chênh lệch 10 dB là tai người cảm nhận âm lượng bị nhỏ đi một nửa! Nếu cứ thế trộn 2 file vào nhau, thanh trượt âm lượng 100% của người dùng chỉ có nghĩa là "giữ nguyên mức thu âm", tiếng đọc chắc chắn bị tiếng nhạc nuốt chửng.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Đo lường năng lượng cảm thụ thực tế theo tiêu chuẩn EBU R128**:
      - Trước khi ghép, hệ thống thực hiện một lượt quét phân tích bằng bộ lọc `ebur128` để đo chỉ số Loudness tích hợp (Integrated Loudness) của cả bài hát và tệp Voice Tag.
    - **Tính toán mức bù âm lượng tự động (Loudness Matching)**:
      - Hệ thống tính toán độ lợi (Gain) cần bù: **`Mức bù (dB) = (Loudness của bài hát + 3.0 dB) - Loudness của Voice Tag`**. Con số +3.0 dB là khoảng đệm an toàn (Headroom) để giọng nói luôn có năng lượng cao hơn bài hát một chút.
      - Áp trần an toàn: Tối đa chỉ tăng +18 dB (để tránh khuếch đại tiếng xì/noise sàn nếu file thu âm quá nhỏ) và giảm tối đa -12 dB.
      - **Kết quả**: Sau khi được nâng lên ngang tầm năng lượng của bài hát, thanh chỉnh âm lượng của người dùng (ví dụ: 80%, 100%) mới thực sự có ý nghĩa — điều chỉnh trên nền một giọng đọc đã rõ ràng và nổi bật!

  ---

  #### 2. Kỹ Thuật Audio Ducking Mượt Mà Qua Biểu Thức Toán Học Thời Gian Thực

  * **Vấn đề / Thách thức kỹ thuật**:
    - Để giọng đọc bản quyền nổi bật, bài hát cần phải tự động giảm âm lượng xuống đúng vào khoảnh khắc giọng đọc cất lên, và tự động nâng âm lượng trở lại khi giọng đọc dứt (**Audio Ducking**).
    - Nếu dùng các bộ nén Sidechain truyền thống trong FFmpeg, cấu hình thường rất cồng kềnh, dễ bị trễ nhịp (Attack/Release time) khiến tiếng nhạc bị giật cục hoặc bị hụt hơi.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Biểu thức toán học động theo thời gian thực (Mathematical Dynamic Ducking)**:
      - Thay vì dùng sidechain, hệ thống xây dựng một công thức toán học điều khiển bộ lọc âm lượng (`volume`) của bài hát:
        - Tại thời điểm chưa tới lượt đọc tag: Giữ nguyên âm lượng bài hát ở mức 1 (100%).
        - Tại thời điểm bắt đầu đọc tag: Dùng phép toán lấy phần dư thời gian (`modulo`) để nhận diện đúng khoảng thời gian giọng đọc đang phát $\rightarrow$ Lập tức hạ âm lượng bài hát xuống đúng tỷ lệ cấu hình của người dùng (ví dụ: 50% = 0.5).
        - Ngay khi câu nói dứt: Âm lượng bài hát lập tức trở về 1 (100%).
    - **Đánh giá trên từng khung mẫu âm thanh (`eval=frame`)**:
      - Biểu thức này được cấu hình tính toán liên tục trên từng khung mẫu âm thanh (44,100 mẫu mỗi giây). Quá trình chuyển đổi âm lượng diễn ra vô cùng chính xác đến từng mili-giây, mượt mà và không hề có hiện tượng trễ nhịp hay giật tiếng.

  ---

  #### 3. Tối Ưu FilterGraph & Chống Tràn RAM Khi Lặp Lại Voice Tag

  * **Vấn đề / Thách thức kỹ thuật**:
    - Một bài hát dài 5 phút (300 giây) chèn Voice Tag định kỳ mỗi 30 giây $\rightarrow$ Cần lặp lại câu nói 10 lần trong suốt bài hát.
    - **Cách làm ngây thơ**: Tách file voice tag thành 10 bản sao, dùng bộ lọc làm trễ (`adelay`) cho từng bản rồi trộn lại bằng `amix`. Cách này buộc FFmpeg phải duy trì cùng lúc 10 luồng stream âm thanh song song chứa toàn khoảng lặng (silence), làm tăng gấp 10 lần bộ nhớ RAM và ngốn sạch CPU của máy chủ.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Kỹ thuật Đệm chu kỳ & Vòng lặp đơn luồng (`apad` + `aloop`)**:
      - Hệ thống tính toán số mẫu âm thanh của một chu kỳ (ví dụ: 30 giây x 44,100 mẫu/giây = 1,323,000 mẫu).
      - Chỉ dùng **duy nhất 1 luồng stream**: Đầu tiên dùng bộ lọc đệm khoảng lặng (`apad`) để kéo dài tệp Voice Tag cho tròn đúng 30 giây, sau đó dùng bộ lọc vòng lặp mẫu (`aloop`) để nhân bản chu kỳ này liên tục.
      - **Kiểm soát bộ nhớ đệm (Memory Threshold)**: Đặt trần an toàn 64MB RAM. Nếu chu kỳ lặp vừa trong 64MB, hệ thống chạy cơ chế `aloop` giúp tiết kiệm đến **90% RAM và CPU** cho FFmpeg! Nếu chu kỳ quá dài vượt 64MB, hệ thống mới thông minh chuyển hướng về cơ chế chia luồng (`asplit`) để bảo vệ bộ nhớ.

  ---

  #### 4. Chống Vỡ Tiếng / Méo Tiếng Tuyệt Đối Bằng Bộ Hạn Chế Đỉnh (Limiter)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi trộn 2 tín hiệu âm thanh vào nhau (Bài hát + Voice Tag), việc cộng dồn các sóng âm sẽ khiến biên độ tín hiệu vượt qua ngưỡng tối đa cho phép của âm thanh kỹ thuật số: **0 dBFS (Full Scale)**.
    - Khi xuất ra file MP3, bộ mã hóa (`libmp3lame`) sẽ "chém phẳng" các đỉnh sóng vượt trần (hiện tượng **Hard Clipping**).
    - Hậu quả: Âm thanh bị rè, méo tiếng kinh khủng (Distortion). Clipping không làm câu nói to hơn, mà nó biến cả tiếng nhạc lẫn tiếng nói thành một bức tường âm thanh vỡ vụn, gây chói tai và phá nát bản nhạc của khách hàng.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Khóa trần âm lượng đầu ra bằng `alimiter`**:
      - Ngay tại cửa ra cuối cùng của chuỗi xử lý, hệ thống đặt một bộ hạn chế đỉnh âm thanh chuyên nghiệp (`alimiter`).
      - Trần biên độ an toàn: Khóa cứng ở mức **0.950 (-0.45 dBFS)**. Khoảng đệm này đảm bảo khi nén sang định dạng MP3, các đỉnh sóng nội suy (Inter-sample Peaks) không bao giờ chạm tới 0 dBFS, loại bỏ 100% hiện tượng méo tiếng.
    - **Tắt tính năng tự bù âm (`level=disabled`)**:
      - Mặc định, bộ lọc `alimiter` sẽ tự động kích âm lượng của toàn bộ bài hát lên lại mức 0 dBFS. Nếu không tắt tính năng này, bộ limiter sẽ vô tình kích âm lượng của những đoạn nhạc vừa bị Ducking lên to trở lại, phá hỏng toàn bộ công sức Ducking của chúng ta! Chúng ta ép cờ `level=disabled` để giữ nguyên vẹn độ êm ái của hiệu ứng Ducking.

  ---

  #### 5. Đo Đạc Thời Lượng Siêu Chính Xác Không Làm Tròn (Sub-Second Precision)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Một câu Voice Tag bản quyền thường có thời lượng rất ngắn, lẻ đến phần trăm giây (ví dụ: 2.65 giây).
    - Nếu hệ thống dùng các hàm làm tròn thời lượng thông thường về số nguyên giây (`int`):
      - File 2.65 giây bị làm tròn xuống 2 giây $\rightarrow$ Nhạc chỉ lùi âm lượng trong 2 giây đầu, 0.65 giây cuối bài nhạc đã bật to trở lại và đè bẹp phần đuôi của câu nói!
      - File ngắn dưới 1 giây (như tiếng chuông hoặc tiếng tít) bị làm tròn về 0 giây $\rightarrow$ Hệ thống báo lỗi file rỗng và từ chối xử lý!
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Trích xuất thời lượng số thực qua `ffprobe`**:
      - Sử dụng `ffprobe` bóc tách luồng âm thanh stream-by-stream với độ chính xác số thực (`Double`) đến hàng mili-giây.
      - Con số lẻ chính xác này được nạp trực tiếp vào biểu thức toán học của bộ Ducking và chu kỳ lặp `aloop`.
      - **Kết quả**: Từng mili-giây của tiếng nói và nhịp lùi/nổi của bài hát ăn khớp với nhau hoàn hảo như được dựng thủ công bởi các kỹ sư âm thanh chuyên nghiệp.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào hệ thống của bạn lồng ghép Voice Tag vào bài hát vừa rõ lời vừa không làm hỏng chất lượng âm nhạc?"*, sếp có thể trả lời đầy đẳng cấp như sau:

  > *"Bọn em xây dựng một chuỗi xử lý âm thanh kỹ thuật số (DSP Pipeline) trên FFmpeg với 4 tiêu chuẩn phòng thu:*
  > *1. **Chuẩn hóa âm lượng LUFS (EBU R128)**: Bài hát master có năng lượng rất lớn (-9 đến -12 LUFS), trong khi voice tag chỉ quanh -24 LUFS. Bọn em dùng filter `ebur128` đo độ lớn cảm thụ thực tế và tự động bù gain cho voice tag nổi lên trên bài hát trước khi áp dụng tỷ lệ âm lượng người dùng.*
  > *2. **Audio Ducking bằng biểu thức toán học**: Bọn em viết biểu thức thời gian thực trên từng khung mẫu (`eval=frame`) để khi câu nói vang lên thì nhạc tự động lùi xuống, nói xong nhạc lập tức nổi lên mượt mà mà không bị trễ nhịp.*
  > *3. **Tối ưu bộ đệm đơn luồng**: Dùng kỹ thuật `apad` kết hợp `aloop` lặp chu kỳ mẫu trong giới hạn 64MB RAM, tiết kiệm 90% CPU và RAM so với việc chia nhiều luồng stream.*
  > *4. **Chống méo tiếng (Anti-Clipping)**: Đặt bộ `alimiter` với trần 0.950 (-0.45 dBFS) và tắt cờ auto-level để triệt tiêu hoàn toàn hiện tượng vỡ tiếng khi xuất file MP3."*

- **Chi Tiết Phần 6: Voice Tag Google TTS & Cache (Tạo Voice Tag AI & Bộ Đệm)**:

  Khi phỏng vấn về việc tích hợp dịch vụ Cloud bên thứ ba (Third-Party AI Services như Google Cloud TTS) vào hệ thống sản xuất thực tế, các câu hỏi hóc búa nhất thường xoay quanh **chi phí vận hành (API billing), quản trị tài nguyên mạng/bộ nhớ và tính nhất quán dữ liệu giữa Cloud Storage với Database**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Bài Toán Chi Phí Cloud API & Cơ Chế Bộ Đệm Nhị Phân Trên Redis (TtsPreviewCache)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong luồng sáng tạo Voice Tag, hành vi tự nhiên của Producer là thử nghiệm liên tục: gõ một câu slogan, chọn giọng nam, đổi sang giọng nữ, nghe lại câu cũ, rồi thử tốc độ đọc khác nhau.
    - Mỗi request gửi lên Google Cloud Text-to-Speech (TTS) đều bị tính phí trực tiếp theo số lượng ký tự (Billed API Call). Nếu mỗi lần người dùng bấm "Nghe thử" (Preview) hệ thống đều bắn request sang Google, chi phí hạ tầng Cloud sẽ tăng đột biến theo cấp số nhân (Burn Budget) chỉ vì những câu từ trùng lặp vô bổ.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Tầng đệm chuyên dụng `RedisTtsPreviewCache`**:
      - Hệ thống triển khai một tầng đệm `RedisTtsPreviewCache` đứng trước Google Cloud TTS.
    - **Quy tắc sinh Cache Key chuẩn hóa**:
      - Kết hợp băm chuỗi chuẩn hóa từ `text (trim + lowercase) + languageCode + voiceName`.
    - **Lưu trữ nhị phân trực tiếp (Binary Audio Caching)**:
      - Redis không chỉ lưu metadata dạng JSON mà lưu trực tiếp mảng byte nhị phân của file âm thanh (Audio Bytes) dưới dạng Binary String / Byte Array, kèm thời gian sống (TTL ngắn khoảng vài chục phút đến vài giờ).
      - Khi Producer bấm nghe thử một câu khẩu hiệu đã từng có người nghe hoặc chính họ vừa nghe lại cách đó 30 giây: Hệ thống intercept ở tầng Application Use Case, kiểm tra Redis Cache Hit và trả thẳng luồng byte âm thanh về cho Frontend phát ngay lập tức với độ trễ cực thấp (< 5ms), tiết kiệm 100% chi phí gọi Google Cloud API và triệt tiêu tải mạng ra ngoài Internet.

  ---

  #### 2. Triết Lý Tách Biệt: Nghe Thử Tạm Thời (Preview) vs Ghi Nhận Chính Thức (Persist)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Hơn 90% các lần bấm tạo âm thanh trong phòng thu thực chất chỉ là "nghe thử cho biết" rồi bỏ.
    - Nếu áp dụng tư duy thiết kế ngây thơ: cứ nhận được file âm thanh từ Google là lập tức upload lên Amazon S3 / MinIO và ghi một dòng vào bảng `voice_tags` trong Database, thì sau 1 tháng hệ thống sẽ ngập tràn hàng triệu "file rác mồ côi" trên Cloud Storage và làm phình to chỉ mục (B-Tree Index) của Database mà không mang lại giá trị kinh doanh nào.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Kiến trúc phân định rạch ròi 2 Use Case độc lập**: `previewVoiceTagTts` và `createVoiceTagTts`.
    - **Ở Use Case Preview**:
      - File âm thanh sinh ra từ Google TTS chỉ tồn tại trên RAM của ứng dụng và được đệm tạm vào Redis. Tuyệt đối không chạm vào S3, không mở Database Connection, không ghi Database Record. Nếu người dùng nghe xong không thích và đóng tab, dữ liệu sẽ tự động bốc hơi khỏi Redis sau khi hết TTL mà không để lại một byte rác nào trên hệ thống.
    - **Ở Use Case Create (Persist)**:
      - Chỉ khi Producer chính thức bấm nút "Lưu Voice Tag vào Kho", hệ thống mới lấy mảng byte âm thanh (ưu tiên bốc từ Redis Cache ra để không gọi lại Google lần nữa), đưa qua kiểm duyệt thời lượng, đẩy lên S3 theo đường dẫn phân cấp `voice-tags/{userId}/{tagId}.mp3`, và thực hiện ghi bản ghi sở hữu vào Database.

  ---

  #### 3. Kỹ Thuật Bảo Tồn Transaction: Tránh Treo Database Connection Khi Gọi Third-Party API

  * **Vấn đề / Thách thức kỹ thuật**:
    - Google Cloud TTS là một dịch vụ bên thứ ba (Third-Party Network Call). Độ trễ của nó hoàn toàn phụ thuộc vào đường truyền quốc tế và tải của Google, dao động từ 500ms đến 3-5 giây.
    - Lỗi thiết kế chí mạng mà các lập trình viên thường mắc phải là đặt annotation `@Transactional` ở mức method của Service/Use Case bao trọn toàn bộ luồng từ lúc gọi Google API đến khi lưu Database. Khi đó, Spring Boot sẽ mượn 1 kết nối từ HikariCP Pool ngay từ đầu method và **giữ khư khư kết nối đó trong suốt 3 giây chờ Google trả về**. Khi có 50 người dùng cùng tạo voice tag, toàn bộ Connection Pool của backend sẽ cạn kiệt, kéo sập toàn bộ các API khác trong hệ thống (Cascading Failure).
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Tách rời ranh giới giao dịch (Transaction Boundary)**:
      - Luồng Use Case xử lý việc gọi Google TTS Adapter hoàn toàn bên ngoài transaction của Database. HikariCP Connection Pool hoàn toàn không bị chạm vào trong suốt quá trình trao đổi qua mạng với Google.
    - **Mô hình Micro-Transaction**:
      - Sau khi Google trả về kết quả nhị phân và dữ liệu đã được đẩy an toàn lên Cloud Storage (S3), hệ thống mới mở một Database Transaction cực ngắn (Micro-Transaction chỉ kéo dài vài mili-giây) để lưu metadata của Voice Tag vào PostgreSQL. Nhờ vậy, một Connection Pool khiêm tốn (ví dụ 10-20 connections) vẫn có thể gánh hàng ngàn request tạo voice tag đồng thời mà không bao giờ bị timeout.

  ---

  #### 4. Cơ Chế Chống Lạm Dụng Bằng `ffprobe` (Voice Tag Duration Guard)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Tính năng Voice Tag sinh ra là để gắn một câu khẩu hiệu nhận diện thương hiệu cực ngắn vào đầu bản nhạc (như *"Producer XYZ on the beat"*, dài từ 2 đến 5 giây).
    - Kẻ xấu hoặc người dùng vô ý có thể lợi dụng endpoint này, paste nguyên một đoạn văn bản tiểu thuyết dài hàng ngàn từ để ép hệ thống gen ra một file âm thanh dài 15-20 phút, sau đó Voice Tag này được đưa vào buồng trộn (Audio Mixing Engine) sẽ phá hủy toàn bộ bản phối của Beat và ngốn hàng gigabyte RAM khi decode ra PCM.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Chốt chặn kiểm soát thời lượng cứng ($\le 10$ giây)**:
      - Hệ thống cài đặt quy chuẩn: Voice Tag tối đa không quá 10 giây.
    - **Thẩm định nhị phân độc lập bằng `ffprobe`**:
      - Hệ thống không chỉ kiểm tra độ dài chuỗi ký tự ở tầng Validation (vì tốc độ đọc, khoảng dừng âm thanh do TTS sinh ra có thể biến thiên thất thường).
      - Thay vào đó, sau khi nhận luồng byte âm thanh (dù là do người dùng tự upload file MP3 lên hay do Google TTS tổng hợp ra), hệ thống ghi tạm vào đĩa đệm và lập tức triệu hồi `ffprobe` (`audioProbe.probeExactDuration`).
      - `ffprobe` sẽ phân tích sâu cấu trúc container và packet âm thanh để đo chính xác thời lượng thực tế (đến từng phần nghìn giây). Nếu thời lượng vượt quá 10.0 giây, Use Case lập tức ném ngoại lệ nghiệp vụ và từ chối xử lý, bảo vệ an toàn tuyệt đối cho Mixing Engine ở giai đoạn sau.

  ---

  #### 5. Xử Lý Xung Đột Dữ Liệu & Giao Dịch Bù Xóa File Mồ Côi (Compensating Clean-up)

  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong Database, bảng `voice_tags` có ràng buộc duy nhất trên cặp khóa: `UNIQUE(user_id, name)` (Mỗi Producer không được đặt 2 voice tag trùng tên nhau).
    - Hãy xét kịch bản: Producer đã có một voice tag tên là `"DropTheBeat"`. Họ thực hiện tạo một voice tag mới và tiếp tục đặt tên là `"DropTheBeat"`. Khi đó: File âm thanh đã được gen từ Google và đã được đẩy thành công lên S3 với một UUID ngẫu nhiên. Sau đó, bước lưu Database mới chạy và bị ném lỗi `DataIntegrityViolationException` (Duplicate Key).
    - Nếu không xử lý khéo léo, file âm thanh vừa upload lên S3 sẽ bị bỏ rơi vĩnh viễn trên Cloud (S3 Orphaned File), gây lãng phí dung lượng và tiền bạc lưu trữ.
  * **Cơ chế kỹ thuật chúng ta xử lý**:
    - **Áp dụng mẫu kiến trúc Giao Dịch Bù (Compensating Transaction)**:
      - Ở mức ứng dụng, khi xảy ra xung đột khóa Unique tại tầng Database, khối ngoại lệ lập tức kích hoạt cơ chế dọn dẹp khẩn cấp: gọi trực tiếp `storageCleaner.deleteNow(uploadedKey)`.
      - Thao tác này ngay lập tức bắn lệnh sang Amazon S3 xóa bỏ vĩnh viễn file vừa tải lên trước khi lan truyền lỗi `VoiceTagAlreadyExistsException` về cho client. Toàn bộ hệ sinh thái lưu trữ luôn được duy trì ở trạng thái nhất quán hoàn hảo (Zero Garbage Storage).

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế tính năng tạo Voice Tag bằng AI/TTS vừa tiết kiệm chi phí Cloud, vừa đảm bảo hiệu năng và tính toàn vẹn dữ liệu?"*, sếp có thể trả lời đầy mạch lạc và thuyết phục như sau:

  > *"Tại Module Audio của PWB_MiNi, tính năng Voice Tag Google TTS được thiết kế với tư duy tối ưu chi phí Cloud và bảo vệ tài nguyên hệ thống ở mức cao nhất:
  > 1. **Tiết kiệm chi phí**: Triển khai `RedisTtsPreviewCache` lưu trữ mảng byte nhị phân của audio dựa trên hash của text và voice. Tránh hoàn toàn việc gọi lặp lại Google API khi Producer liên tục nghe thử cùng một câu chữ.
  > 2. **Phân tách Preview vs Persist**: Nghe thử chỉ đệm trên RAM và Redis với TTL ngắn, tuyệt đối không tạo file trên S3 hay ghi DB. Chỉ khi người dùng bấm Lưu, file mới được ghi vào S3 và DB.
  > 3. **Bảo tồn Connection Pool**: Toàn bộ quá trình gọi Google TTS và upload S3 diễn ra ngoài DB Transaction, chỉ mở micro-transaction vài mili-giây ở bước ghi cuối cùng, chống cạn kiệt HikariCP Pool.
  > 4. **Anti-Abuse**: Dùng `ffprobe` đo đạc chính xác thời lượng thực tế của file âm thanh, chặn đứng mọi hành vi tạo voice tag vượt quá 10 giây.
  > 5. **Compensating Clean-up**: Nếu gặp xung đột trùng tên voice tag ở Database, hệ thống lập tức bù trừ bằng cách gọi lệnh xóa ngay file vừa tải lên S3, đảm bảo không bao giờ sinh ra file rác mồ côi."*

- **Chi Tiết Bổ Trợ Trọng Yếu: Kiến Trúc Xử Lý Bất Đồng Bộ (Asynchronous Processing Architecture)**:

  Khi người phỏng vấn hỏi: *"Ở module Audio này, các bạn nói là xử lý bất đồng bộ (Asynchronous Processing), vậy cụ thể hệ thống xử lý bất đồng bộ như thế nào và giải quyết các bài toán gì?"*, sếp có thể phân tích mạch lạc qua **4 tầng luận điểm kiến trúc**:

  ---

  #### 1. Tại Sao Bắt Buộc Phải Bất Đồng Bộ? (The "Why")

  * **Bản chất tác vụ âm thanh**:
    - Việc xử lý bài hát (tải từ S3 về, giải mã PCM, đo LUFS, lồng ghép voice tag, nén MP3 và upload ngược lại S3) là tác vụ **CPU-intensive và I/O-intensive** kéo dài từ **30 giây đến vài phút**.
  * **Rủi ro chí mạng nếu xử lý đồng bộ (Synchronous HTTP Request)**:
    - Trình duyệt sẽ bị treo (loading xoay vòng), dẫn đến lỗi **HTTP 504 Gateway Timeout** (do Nginx/Cloudflare thường ngắt kết nối sau 60 giây).
    - Một HTTP Request giữ một Thread của Tomcat và một Connection của HikariCP trong suốt 2-3 phút. Chỉ cần **30 người cùng upload một lúc là toàn bộ server backend tê liệt**, không ai đăng nhập hay xem trang chủ được nữa.
  * $\rightarrow$ **Giải pháp kiến trúc**: Phải biến toàn bộ quy trình này thành **bất đồng bộ hoàn toàn (Fire-and-Forget kết hợp Event-Driven Architecture)**.

  ---

  #### 2. Luồng Xử Lý Bất Đồng Bộ Diễn Ra Như Thế Nào? (Under The Hood)

  Hệ thống xử lý qua chuỗi 3 bước khép kín:

  * **Bước 1: Tiếp Nhận Nhanh & Trả Quyền Điều Khiển Ngay Lập Tức (Fast Acknowledge)**:
    - Khi người dùng bấm hoàn tất upload, Client gọi API `confirmUpload`.
    - Server **chỉ làm đúng 2 việc trong một Micro-Transaction (< 10 mili-giây)**:
      1. Đổi trạng thái bài hát trong Database thành `PROCESSING`.
      2. Ghi một bản ghi sự kiện `SongProcessRequestedEvent` vào bảng `outbox_events` (Transactional Outbox).
    - **Server trả về HTTP 200/202 ngay lập tức**: Trình duyệt của người dùng được giải phóng trong chưa đầy 50ms, người dùng có thể thoải mái chuyển trang, nghe bài khác hoặc tắt máy đi ngủ.
  * **Bước 2: Đẩy Nhiệm Vụ Sang Hàng Đợi Sự Kiện (Decoupling qua Kafka)**:
    - Một background process quét bảng Outbox và publish message `SongProcessRequested` vào topic của **Apache Kafka**.
    - Việc dùng Kafka làm bộ đệm trung gian đóng vai trò như một **bình tích áp (Pressure Tank / Backpressure)**: Dù giờ cao điểm có 10.000 bài hát được gửi lên cùng lúc, Kafka sẽ xếp hàng an toàn, không để lượng tải này tràn thẳng vào làm sập hệ thống xử lý.
  * **Bước 3: Worker Xử Lý Ngầm (Asynchronous Worker Execution)**:
    - Các Audio Worker (Kafka Consumer) chạy hoàn toàn độc lập, tách biệt khỏi luồng HTTP của Web.
    - Worker nhặt từng bài hát (`max.poll.records = 1`) từ Kafka về:
      - Khởi tạo thư mục tạm cô lập trên ổ đĩa.
      - Tải file từ S3, gọi FFmpeg chạy chuỗi DSP Pipeline (LUFS, Ducking, Limiter).
      - Tải file thành phẩm lên S3.
    - Toàn bộ quá trình nặng nhọc này diễn ra **trong background, không ai phải chờ ai**.

  ---

  #### 3. Hai Kỹ Thuật Đỉnh Cao Khi Vận Hành Bất Đồng Bộ Trong Hệ Thống Này

  * **Non-blocking Database Connection trong tiến trình Async**:
    - Khi chạy bất đồng bộ dài 2-3 phút, nếu vô tình gắn `@Transactional` ở hàm Worker, Database Connection Pool sẽ chết đứng.
    - Hệ thống của chúng ta **giải phóng hoàn toàn kết nối Database trong suốt thời gian FFmpeg render**. Chỉ mượn kết nối vài mili-giây lúc đầu để đọc và vài mili-giây lúc cuối để đổi trạng thái sang `READY` hoặc `FAILED`.
  * **Cơ chế phản hồi kết quả về Client (Eventual Consistency & Realtime Notification)**:
    - Khi Worker xử lý xong, hệ thống cập nhật Database thành `READY` và kích hoạt thông báo qua **STOMP WebSocket**.
    - Phía Frontend nhận tín hiệu và tự động cập nhật thanh trạng thái từ "Đang xử lý" sang "Đã sẵn sàng" kèm sóng âm thanh (Waveform) mà **người dùng không cần phải bấm F5 reload trang**.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Ở module Audio này, các bạn nói là xử lý bất đồng bộ, vậy cụ thể luồng đi ra sao và giải quyết bài toán gì?"*, sếp có thể trả lời đanh thép trong 1 phút như sau:

  > *"Tại Module Audio, bọn em xử lý bất đồng bộ theo mô hình **Event-Driven Architecture** qua 3 tầng cốt lõi:*
  > 
  > *1. **Fast Ack**: Khi client xác nhận upload, API chỉ tốn 10ms ghi nhận trạng thái `PROCESSING` và lưu event vào Outbox Table rồi trả về ngay HTTP 200, giải phóng ngay lập tức luồng HTTP và trải nghiệm của người dùng.*
  > *2. **Kafka Buffering**: Sự kiện được đẩy lên Kafka để đóng vai trò bộ đệm chống shock tải (Backpressure), đảm bảo hàng nghìn request gửi lên cùng lúc cũng không làm tràn CPU.*
  > *3. **Isolated Worker**: Worker nhặt message từ Kafka để tải file và chạy FFmpeg trong tiến trình nền độc lập. Điểm mấu chốt là Worker nhả hoàn toàn Database Connection trong suốt thời gian render nhạc để chống cạn kiệt Pool, và sau khi hoàn thành sẽ bắn tín hiệu qua WebSocket để giao diện người dùng tự động chuyển trạng thái `READY` theo thời gian thực."*

---

---

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 2: Module IAM](02-giai-doan-2-module-iam.md) | [Giai đoạn 4: Module Live Room ➡️](04-giai-doan-4-module-liveroom.md)
