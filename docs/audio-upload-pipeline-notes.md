# Upload & voice-tag merge pipeline — findings and changes

Ngày: 2026-08-06. Nhánh `develop`. Bối cảnh: chuẩn bị deploy, storage là **AWS S3 thật**
(`ap-southeast-1`, bucket `pwb-mini-prod-bucket`, `STORAGE_S3_ENDPOINT` để trống) — **không dùng MinIO**.

Xuất phát từ 3 quan sát trên trang chi tiết bài hát: toast báo "Upload bài hát thành công" nhưng badge
vẫn "Đang xử lý"; toàn bộ quá trình lâu với file 12 MB; và "Ngày tạo: Invalid Date".

---

## 1. Chẩn đoán

### 1.1 "Upload thành công" nhưng vẫn "Đang xử lý" — hai giai đoạn khác nhau

Không mâu thuẫn, nhưng chữ toast sai. Toast bắn ngay khi `POST /songs` trả về, trong khi chính transaction
đó đã set status `PROCESSING` (`SongUseCaseImpl.createSong` → `song.startProcessing()`) vì bài có voice tag.
Việc ghép FFmpeg chạy async qua outbox → Kafka → `SongProcessorWorker`.

### 1.2 Vì sao chậm — file đi qua Internet 3 lượt

Backend chạy local/deploy nhưng bucket ở Singapore. Cùng một file 12 MB đi qua đường truyền **ba lần**:

| Chặng | Truyền | Nơi |
|---|---|---|
| Browser `PUT` presigned URL | 12 MB lên | `song-upload-form.tsx` — một PUT đơn, không multipart |
| Worker tải file gốc về đĩa | 12 MB xuống | `JaffreeAudioProcessorAdapter.download()` |
| Worker đẩy bản đã ghép lên | ~7-12 MB lên | `JaffreeAudioProcessorAdapter` → `uploadFromPath` |

≈ **31-36 MB qua Internet** cho một file 12 MB. **Đây mới là nút thắt chính**, không phải kích thước file.

Cộng thêm độ trễ cố định: outbox relay poll (5s dev / 2s prod) + frontend poll 3s.

### 1.3 FFmpeg **không** phải nút thắt — đã đo thực tế

Ước tính ban đầu của tôi ("filter graph tốn 12×") **là sai**. Số đo thật trên máy này
(script trong scratchpad, sine tổng hợp, libmp3lame 192k):

| Bài | Interval | Số lần chèn | Thời gian FFmpeg |
|---|---|---|---|
| 316s (đúng ca của bạn) | 25s | 12 | **~1.6s** |
| 900s | 90s | 11 | ~4.2s |
| 900s | 10s | 91 | ~4.7s (loop) / 6.9s (split) |
| 900s | 5s | 181 | ~4.7s (loop) / **15.7s** (split) |

→ Với bài 5 phút, FFmpeg chỉ tốn ~1.6s. Tối ưu filter graph **không giúp gì cho ca của bạn**;
nó chỉ xoá vách scaling ở số lần chèn lớn (`MAX_INSERTIONS = 500` trước đây là bom hẹn giờ).

### 1.4 "Invalid Date" — bug thật

`SongMapper.toDomain` / `VoiceTagMapper.toDomain` / `SongTagConfigMapper.toDomain` **không bao giờ gọi**
`restoreAuditTimestamps(...)` → `createdAt` luôn null → `default-property-inclusion: non_null`
(`application.yml`) cắt hẳn field khỏi JSON → frontend chạy `new Date(undefined)` → `Invalid Date`.
Cả 7 mapper của module liveroom đều gọi hàm này; cả 3 mapper của audio đều thiếu.

---

## 2. Đã làm

### Backend

| File | Thay đổi |
|---|---|
| `audio/.../persistence/mapper/SongMapper.java` | gọi `restoreAuditTimestamps` |
| `audio/.../persistence/mapper/VoiceTagMapper.java` | như trên |
| `audio/.../persistence/mapper/SongTagConfigMapper.java` | như trên |
| `shared-infrastructure/.../outbox/scheduler/OutboxRelayTrigger.java` | **file mới** — chạy relay ngay sau commit thay vì đợi tick. Dedup 1 nudge/transaction, chạy trên `outboxPublishExecutor`, mọi lỗi đều nuốt và log (scheduled poll vẫn là bảo đảm) |
| `shared-infrastructure/.../outbox/persistence/writer/OutboxJpaWriter.java` | gọi `relayTrigger.requestPublish()` sau `repository.save` |
| `audio/.../processor/SongProcessingConsumer.java` | `max.poll.records=1` + `max.poll.interval.ms` (mặc định 30 phút, prop `pwb.audio.processor.kafka.max-poll-interval-ms`) — chặn rebalance khi FFmpeg chạy lâu, tránh ghép trùng |
| `audio/.../audio/WatermarkFilterBuilder.java` | tag track dựng bằng `apad=whole_dur=<interval>,aloop=loop=N-1:size=<interval*44100>` thay cho `asplit=N` + N×`adelay` + `amix=inputs=N`. Fallback về asplit khi buffer loop > 64 MB (interval > ~190s, lúc đó N nhỏ nên asplit rẻ) |
| `audio/.../audio/JaffreeAudioProcessorAdapter.java` | bỏ `ffprobe` lần 3 trên file output (mix cắt theo `duration=first` = độ dài bài, đã biết); pin `libmp3lame` + `-b:a` |
| `audio/.../audio/properties/AudioProcessorProperties.java` | thêm `outputBitrate` (mặc định `192k`) |
| `audio/.../WatermarkFilterBuilderTest.java` | cập nhật cho cả 2 nhánh loop / asplit |
| `audio/.../audio/JaffreeAudioProcessorAdapter.java` | **timeout thật cho FFmpeg** — `executeAsync()` + `get(timeoutMinutes, MINUTES)`, hết giờ thì dừng tiến trình và ném `PROCESSING_TIMED_OUT` |
| `audio/.../application/exception/AudioErrorCode.java` + `messages*.properties` | thêm `AUDIO_028 PROCESSING_TIMED_OUT` |
| `shared-infrastructure/.../storage/config/StorageConfig.java` | **bật multipart thật** trên `S3AsyncClient` — xem 2.2 |
| `shared-infrastructure/.../storage/StorageService.java` + `impl/S3StorageServiceImpl.java` | thêm `uploadFile(key, Path, contentType)` và `downloadToFile(key, Path)` chạy qua transfer manager |
| `shared-infrastructure/.../storage/properties/StorageProperties.java` | thêm `multipartThresholdBytes`, `multipartPartSizeBytes` (mặc định 5 MiB — mức tối thiểu S3 cho phép) |
| `audio/.../domain/service/StoragePort.java` + `infrastructure/service/StoragePortAdapter.java` | bỏ `download(): InputStream`, thay bằng `downloadToPath(key, Path)`; `uploadFromPath` chuyển sang `uploadFile` |
| `shared-infrastructure/.../S3StorageServiceFileTransferTest.java` | **file mới** — 8 test cho 2 method mới |

### Frontend

| File | Thay đổi |
|---|---|
| `features/voice/components/song-upload-form.tsx` | **gộp upload + ghép thành một quy trình** — xem 2.1 |
| `components/ui/upload-progress.tsx` | thêm pha `merging` |
| `app/(dashboard)/dashboard/songs/[songId]/page.tsx` | toast success khi `PROCESSING → PROCESSED`, toast error khi `→ FAILED` — giờ chỉ còn phục vụ ca vào trang bằng đường khác (từ danh sách), vì form đã tự chờ xong mới điều hướng |
| `features/voice/components/song-upload-form.test.tsx` | **file mới** — 6 test cho vòng chờ ghép |
| `messages/vi.json`, `messages/en.json` | thêm `uploadQueued`, `processingCompleted`, `processingFailedToast`, `upload.merging`, `form.processingFailed`, `form.mergeRunInBackground`, `form.mergeWaitHint`; sửa `processingBanner` |
| `features/landing/components/section-{demo,features,how-it-works}.tsx` | khai báo kiểu `Variants` cho các const variant — sửa lỗi `tsc` ở mục 3.3 cũ |
| ~~`features/voice/lib/pending-merge.ts`~~ | **đã xoá** — cờ sessionStorage thành thừa sau 2.1 |

#### 2.1 Upload + ghép là **một** quy trình

Cách làm ban đầu — báo `uploadQueued` rồi đẩy user sang trang chi tiết đang `PROCESSING` — vẫn sai ý.
Nhìn từ phía user, tải file và ghép voice tag là **một việc**; tách thành hai màn khiến nửa sau trông
như bị lỗi ("sao vẫn còn Đang xử lý"). Giờ:

- Form ở nguyên tại chỗ sau khi `createSong` trả `PROCESSING`, chuyển sang pha `merging`, poll
  `getSong` mỗi 2s.
- Ghép xong → `toast.success` → **lúc đó mới** điều hướng sang trang bài hát (đã ở trạng thái xong).
  Thất bại → toast lỗi rồi vẫn điều hướng, để user bấm "Xử lý lại".
- Bài **không** có voice tag đi thẳng như cũ, không chờ gì.
- **Nút "Chạy nền"**: file đã nằm an toàn trên server rồi, nên rời đi chỉ là bỏ cuộc chờ chứ không
  huỷ upload. Không có nút này thì hàng đợi kẹt sẽ giam user ở màn upload.
- **Trần chờ 5 phút** rồi bàn giao cho trang bài hát — không phải lỗi, chỉ là không giam user.
- Poll lỗi mạng **không** bị coi là ghép thất bại; lần poll sau chính là lần retry.

#### 2.2 Multipart song song cho worker

**Phát hiện quan trọng:** `S3TransferManager` đặt trên một `S3AsyncClient` thường **không chia part gì
cả** — nó chỉ là API đẹp hơn cho cùng một luồng đơn. Thiếu `multipartEnabled(true)` nên nhánh
`uploadMultipart` (>25 MB) mà tên gọi ngụ ý là multipart **cũng chưa bao giờ là multipart**.

- `StorageConfig` bật `multipartEnabled(true)` + threshold/part size 5 MiB → file 8–12 MB của pipeline
  giờ mới thật sự đi 2–3 part song song.
- Worker chuyển sang `downloadToPath` / `uploadFile` (file-based). Bản `InputStream` không thể chia
  part được — stream chỉ đọc được một lần, theo thứ tự — nên kiểu dữ liệu chính là thứ quyết định,
  không phải một tuỳ chọn truyền vào.
- `uploadFromPath` không còn chuyển tiếp `contentLength` của caller: transfer manager tự đọc kích thước
  từ file, tin lời caller là cách một sai lệch trở thành object hỏng.

### Đã kiểm chứng

- **Filter graph mới bit-exact với cũ**: md5 của audio giải mã `b_loop.mp3` == `b_split.mp3` ==
  `a942b55a3d45b9d7f60b2a4532205486`; vị trí chèn đo bằng bandpass 1000 Hz trùng khớp (t ≈ 2, 12, 22 với
  interval 10 / offset 2).
- **Bỏ ffprobe output an toàn**: duration output đo được đúng bằng duration bài (30.000000 / 316.000000).
- `mvn -o test -pl modules/audio` → **26/26 pass**.
- `mvn -o test -pl shared/shared-infrastructure -Dtest='!*IT'` → **51/51 pass** (43 cũ + 8 mới).
- Frontend: `tsc --noEmit` **sạch toàn repo** (không còn lỗi landing), `eslint` sạch trên các file đã đụng.
- `npx vitest run` → **60/61 pass**. Ca fail duy nhất là `login-form.test.tsx` (nút Google Sign-In),
  **có sẵn** — đã kiểm chứng bằng cách stash thay đổi messages của tôi rồi chạy lại, vẫn fail y hệt.
- **Timeout FFmpeg đo bằng ffmpeg thật** (script `TimeoutProbe` trong scratchpad, encode 30 phút với timeout 1s).
  Hai điều chỉ lộ ra khi chạy thật, cả hai đã sửa trong code:
  - `forceStop()` **có thể ném `CancellationException`** khi nó chạy đua với tiến trình vừa kết thúc
    (lần chạy 1 ném, lần chạy 2 không) → nếu không bắt thì nó nuốt mất `PROCESSING_TIMED_OUT`
    và bài hát bị ghi nhầm thành `PROCESSING_FAILED`.
  - `get()` sau khi cancel ném `CancellationException`, **không phải** `ExecutionException` → mệnh đề
    `catch` ban đầu bắt hụt.
  - Kết quả sau khi sửa: `TimeoutException` sau ~1.1s → dừng → `ffmpeg.exe` còn sống: **0** → xoá được
    file output (quan trọng trên Windows: tiến trình còn sống thì không xoá được job dir).
  - Ghi chú: `forceStop()` của Jaffree gửi `q` chứ không kill, nên FFmpeg thoát status 0 và để lại một
    file mp3 **hợp lệ nhưng bị cắt ngắn**. Không sao — adapter ném exception trước bước upload, và
    `workspace.release(jobDir)` xoá nó.

---

## 3. Còn dang dở / cần quyết định

### 3.1 Chưa chạy được integration test outbox (môi trường, không phải code)

`OutboxRelaySchedulerIT` và `KafkaOutboxPublisherIT` fail khi load context:

```
Caused by: org.apache.kafka.common.KafkaException: Failed to create new NetworkClient
Caused by: java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
```

**Đã xác nhận là lỗi có sẵn**: gỡ toàn bộ thay đổi outbox của tôi ra rồi chạy lại vẫn fail y hệt (5/5 error).

**Đã khoanh được nguyên nhân chính xác** (trước chỉ đoán "firewall / antivirus / VPN"). Đo bằng một
chương trình Java 2 dòng trên máy này:

| Thao tác | Kết quả |
|---|---|
| `new ServerSocket(0)` + connect `127.0.0.1` | **OK** |
| `Selector.open()` | **FAIL** — `Unable to establish loopback connection` |

TCP loopback bình thường; thứ bị chặn là **socket AF_UNIX** mà JDK 21 dùng cho wakeup pipe của NIO
selector (`sun.nio.ch.UnixDomainSockets.connect0`). Vì vậy **mọi** thứ chạy trên NIO selector đều chết
tại chỗ trên máy này, không riêng Kafka:

- Kafka client (2 IT ở trên)
- Netty → `S3AsyncClient` → không dựng được client, nên không test được multipart tại đây (mục 3.7)
- `com.sun.net.httpserver.HttpServer` → không dựng được S3 giả để quan sát request

Đã thử vài system property để ép JDK bỏ AF_UNIX, không cái nào ăn. Nhiều khả năng do phần mềm bảo mật
trên Windows chặn AF_UNIX. **Cần chạy lại 2 IT này ở CI hoặc máy khác trước khi deploy.**

### 3.2 `modules/iam` test source không compile — **rộng hơn 3 file, mới sửa được 3**

Ước lượng cũ ("chỉ 3 file") **sai**. javac dừng ở đợt lỗi đầu tiên; sửa xong 3 file đó thì lộ ra
**24 file nữa** đều lệch với refactor iam (`User.rehydrate` thêm 3 tham số, `LoginPolicy` đổi
constructor, `ThrottlingService` → `RateLimitGuard`, `LoginAttemptChecker`/`OtpGenerator` thêm method,
`OtpCode` bỏ `MAX_ATTEMPTS`/`isPending`, …).

**Đã sửa xong:**

| File | Việc |
|---|---|
| `testsupport/TestUserBuilder.java` | thêm 3 tham số mới của `User.rehydrate` cho cả 8 factory |
| `testsupport/StubTokenManagerService.java` | bỏ `@Override` trên `isRefreshTokenRevoked` (interface đã bỏ method, adapter IT vẫn dùng) |
| `testsupport/AuthControllerHarness.java` | **file mới** — dựng `AuthController` + 11 mock sau một `MockMvc` dùng chung cho 2 test dưới |
| `api/controller/AuthControllerTest.java` | viết lại theo 10 use case thay cho `IamFacade`; thêm ca refresh-qua-cookie, logout ưu tiên cookie hơn body, forgot-password không trả userId |
| `contract/AuthEndpointContractTest.java` | viết lại theo contract hiện tại: `refreshToken` **không** còn trong body, phải nằm trong `Set-Cookie` HttpOnly |
| `application/facade/IamFacadeImplTest.java` | **đã xoá** — chỉ test uỷ quyền của `IamFacadeImpl`, class này không còn tồn tại; từng use case đã có test riêng |

Hai chi tiết đáng nhớ (đều chỉ lộ ra khi chạy thật, không phải khi đọc code):

- **`@WebMvcTest` không dùng được ở module này.** `@SpringBootConfiguration` duy nhất nằm ở
  `com.pwb.iam.testsupport.TestIamConfiguration`, mà Boot tìm ngược từ package của test lên
  **không đệ quy xuống** từng package → không bao giờ thấy. Hai test cũ vì vậy chưa từng chạy được.
- **`standaloneSetup` cũng không đủ.** Nó đưa cho handler adapter một bean factory không resolve được
  embedded value, trong khi `/refresh` đọc cookie qua
  `@CookieValue("${pwb.iam.refresh-token.cookie.name:…}")`. Dưới standalone tên đó ở nguyên dạng
  placeholder → 3 test cookie fail (và nếu không để ý thì chúng "pass" vì lý do sai).
  `addPlaceholderValue(...)` **không** cứu được: nó chỉ áp cho request mapping.
  → Harness dựng `GenericWebApplicationContext` thật với `@EnableWebMvc`.

Chạy bằng JUnit Platform launcher trực tiếp (vì `mvn test` vẫn fail ở 21 file còn lại):
**43/43 pass**.

**Còn lại:** 21 file test iam vẫn không compile. Đây là việc riêng, không liên quan pipeline upload;
`mvn install` vẫn cần `-Dmaven.test.skip=true` cho tới khi dọn xong.

### 3.3 `tsc` fail ở 4 file landing — **đã sửa**

Nguyên nhân: `ease: "easeOut"` nằm trong object literal gán cho `Variants` thì bị suy ra thành
`string`; các chỗ viết inline `transition={{...}}` không lỗi vì có contextual typing.
Sửa bằng cách khai báo `const xVariants: Variants = {...}` (import `type Variants` từ framer-motion),
không phải ép kiểu. `npx tsc --noEmit` giờ sạch toàn repo.

### 3.4 Quyết định cần bạn chốt: `outputBitrate`

Trước đây không set bitrate → FFmpeg dùng mặc định **128k**, tức mọi bài upload trên 128k đều **bị giảm
chất lượng âm thầm** ở bản đã ghép (bản mà user phát hành). File trong ảnh là 12 MB / 316s ≈ 304 kbps.
Tôi đặt mặc định `192k`. Chỉnh bằng:

```
pwb.audio.processor.output-bitrate=320k
```

Đặt `128k` để giữ đúng hành vi cũ. Bitrate cao hơn = encode lâu hơn một chút + upload nhiều byte hơn.

### 3.5 Đòn bẩy tốc độ lớn nhất còn lại: vị trí deploy

FFmpeg chỉ ~1.6s. Phần lớn thời gian là mạng. Sau khi deploy:

- **Đặt backend cùng region với bucket (`ap-southeast-1`)** → chặng download + upload của worker
  (24 MB) từ vài chục giây xuống ~1-2s. Đây là thay đổi có tác động lớn nhất còn lại.
- Chặng browser → S3 là đường truyền của chính user, không sửa được; thanh progress hiện có là cách xử lý đúng.
- Cân nhắc bật multipart cho chặng upload output nếu file lớn
  (`multipart-upload-threshold-bytes` đang là 25 MB, output ~7.6 MB nên chưa chạm).

### 3.6 Chưa làm (cân nhắc sau)

- ~~Timeout thật cho FFmpeg~~ — **đã làm**, xem mục 2 và phần kiểm chứng. `pwb.audio.processor.timeout-minutes`
  (mặc định 15) giờ có tác dụng thật. Giữ nó **nhỏ hơn** `max-poll-interval-ms` của consumer (30 phút),
  nếu không Kafka coi listener đã chết và giao bài cho instance khác trong lúc instance này còn đang chạy.
  Chưa có unit test tự động cho nhánh timeout (phải mock được chuỗi builder của Jaffree); hiện chỉ có
  kiểm chứng thủ công bằng ffmpeg thật.
- **Concurrency của consumer** vẫn là 1 (`KafkaConsumerConfig.setConcurrency(1)`) → các bài xử lý tuần tự.
  Tăng chỉ có ích nếu topic có nhiều partition và server đủ CPU; FFmpeg là CPU-bound nên tăng bừa sẽ chậm hơn.
- **Frontend poll 3s** giữ nguyên. Có thể đổi sang SSE/WebSocket nếu muốn phản hồi tức thì.
- Import thừa trong `JaffreeAudioProcessorAdapter` (`java.nio.file.Paths`, `java.util.Objects`) — có sẵn, vô hại.

---

### 3.7 Multipart: chưa đo được ở máy này, **cần xác nhận sau khi deploy**

Đây là điểm yếu duy nhất còn lại của mục 2.2. Tôi **chưa** quan sát được request thật để chứng minh
SDK chia part, vì `Selector.open()` bị chặn (mục 3.1) nên không dựng được cả `S3AsyncClient` lẫn HTTP
server giả. Cái đã có:

- Compile sạch, `S3AsyncClientBuilder.multipartEnabled` / `multipartConfiguration` tồn tại thật trong
  AWS SDK **2.28.0** (đã `javap` để chắc, không đoán theo trí nhớ).
- 8 unit test cho phần có nhánh thật (`downloadToFile` / `uploadFile`, gỡ `CompletionException`,
  404 → `STORAGE_OBJECT_NOT_FOUND`).

Cái **chưa** có: bằng chứng SDK thật sự phát nhiều ranged GET / part PUT.

Có sẵn `MultipartProbe.java` trong scratchpad — dựng HTTP server giả làm S3, đếm số ranged GET và số
part PUT, chạy được cả 2 chế độ để so sánh. Chạy nó ở CI hoặc máy không chặn AF_UNIX:

```
java -cp "<classpath>" MultipartProbe <thư-mục> true
```

Kỳ vọng với object 12 MB, part 5 MiB: **ranged GET ≥ 3**, `CreateMultipartUpload = true`, **part PUT ≥ 3**.
Nếu ra `ranged GET = 1` và `plain PUT = 1` thì multipart **vẫn chưa ăn** và cần xem lại config.

Cách đo đơn giản hơn nếu ngại chạy probe: bật log request của SDK và xem một lần ghép thật.

## 4. Ghi chú bảo mật

`Backend/.env` chứa `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY` thật của bucket production, cùng
`APP_SEED_ADMIN_PASSWORD`. File **có** trong `.gitignore` (`*.env`) nên chưa lộ qua git. Trước khi deploy,
chuyển sang IAM role hoặc secret manager thay vì file `.env` trên máy dev.
