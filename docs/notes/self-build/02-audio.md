# F13–F19 · Bài hát, voice tag, pipeline ghép tag

Bảng (rút gọn từ `V200__create_audio_schema.sql`):

`songs(id, user_id, title, artist, album, original_s3_key UNIQUE, processed_s3_key, file_size_bytes, duration_seconds, format, status, thumbnail_url, last_error)`
`voice_tags(id, user_id, name, type, text_content, language_code, voice_name, s3_key, duration_seconds, file_size_bytes)` — `UNIQUE(user_id, name)`
`song_tag_configs(id, song_id, voice_tag_id, interval_seconds, volume_percentage, ducking_percentage, start_offset_seconds, enabled)`

`SongStatus`: `UPLOADED, PROCESSING, PROCESSED, FAILED`.

---

## F13 · Xin URL upload — `POST /api/v1/songs/upload-url`

### Luồng thật
1. Parse `format` → enum (`mp3`/`wav`/`flac`). Sai → `INVALID_AUDIO_FORMAT`.
2. Kiểm `sizeBytes`: `<= 0` → `FILE_EMPTY`; vượt trần → `FILE_TOO_LARGE`.
3. Suy ra `contentType` từ format (server quyết, không lấy của client).
4. Sinh key **trong vùng tạm**: `audio/staging/{userId}/{uuid}.{ext}`.
5. Ký presigned PUT với **contentType và size nằm trong chữ ký**, TTL 1 giờ.
6. Trả `{ storageKey, url, contentType, expiresAt }`.

### Tự code
Một method + `StorageService.presignUpload(key, contentType, size, ttl)`.

### Bẫy
- **Giới hạn dung lượng phải nằm trong chữ ký**, vì đây là thời điểm duy nhất áp được. Kiểm sau khi upload thì file đã truyền xong, đã tốn băng thông, đã nằm trong bucket mà không có row nào trỏ tới.
- **`contentType` do server chọn và ký.** Để client tự khai, nó khai `text/html` và S3 sẽ phục vụ file đó như một trang web — XSS trên domain bucket.
- **Hai prefix `staging/` và `originals/` là một quyết định vận hành.** Chưa đăng ký thì nằm ở `staging/`, đăng ký xong mới chuyển sang `originals/`. Nhờ vậy đặt được lifecycle rule "xoá mọi thứ trong `staging/` sau 1 ngày" mà không sợ chạm vào audio thật. Nếu upload thẳng vào `originals/`, rác và audio sống lẫn nhau và không luật nào dọn được cái này mà không đe doạ cái kia.
- TTL 1 giờ là cố định, **không cho client chọn**. Presigned URL là một credential không thu hồi được — thời hạn của nó là cài đặt bảo mật, không phải tuỳ chọn.

---

## F14 · Đăng ký bài hát — `POST /api/v1/songs`

Đây là chức năng nhiều bẫy nhất của module. Client gọi sau khi đã PUT file lên URL của F13.

### Luồng thật
1. Parse format.
2. **Kiểm key thuộc về người gọi**: phải bắt đầu bằng `audio/staging/{userId}/` và không chứa `..`.
3. Suy ra key đích: đổi prefix `staging/` → `originals/`, **giữ nguyên phần còn lại**.
4. Kiểm key đích chưa được đăng ký (`existsByOriginalS3Key`).
5. **Hỏi storage** metadata của file: không có → `UPLOAD_NOT_FOUND`; quá lớn → `FILE_TOO_LARGE`; 0 byte → `FILE_EMPTY`.
6. **Đọc vài byte đầu** (ranged read) và kiểm magic bytes có phải audio thật không.
7. Nếu request có `voiceTagConfig`, nạp voice tag và kiểm nó thuộc về người gọi.
8. **Copy** object từ `staging/` sang `originals/`.
9. Trong một transaction: tạo `Song` (status `UPLOADED`); nếu có voice tag thì `startProcessing()` **trước khi insert** rồi lưu `song_tag_config` và đẩy một event vào outbox.
10. **Sau khi commit** mới xoá bản ở `staging/`.

### Tự code
- **A.** Bước 1–5, 9 (không voice tag). Chạy được: upload → đăng ký → thấy row.
- **B.** Bước 2, 3, 6 — ba phép kiểm bảo mật.
- **C.** Bước 7–10 + xử lý `DataIntegrityViolationException`.

### Bẫy
- **Bước 2 chặn IDOR.** Client tự gửi lại key, nên nó gửi được key của người khác. Không kiểm thì bất kỳ ai cũng đăng ký được file của người khác thành bài hát của mình. (Đây là lỗi thật đã từng vá trong dự án — xem commit `707227c`.)
- **Bước 3 phải *suy ra*, không được sinh UUID mới.** Suy ra giữ ánh xạ 1-1 giữa key staging và key song, nhờ đó `UNIQUE(original_s3_key)` mới có nghĩa. Sinh mới thì hai upload khác nhau vẫn đụng nhau được.
- **Bước 4 không nguyên tử.** Giữa `exists` và `insert` có kẽ hở. Thứ thật sự quyết định là **unique index**; phải bắt `DataIntegrityViolationException` và đổi thành 409. Và trong nhánh đó **không được xoá object** — thua cuộc đua nghĩa là đăng ký kia đang sở hữu key đó, object ở đấy là audio sống của họ.
- **Thứ tự bước 8 → 9 → 10 quan trọng ở cả hai chiều.** Copy trước khi ghi row, nên không bao giờ có row trỏ tới object chưa tồn tại. Xoá staging sau khi commit, nên rollback để lại file y nguyên chỗ client đã upload thay vì phá huỷ nó.
- Nhánh lỗi bất kỳ khác ở bước 9 thì **phải xoá ngay** object vừa copy: nó nằm ngoài `staging/` nên lifecycle rule không quét tới, và không row nào trỏ tới nó — đây là cơ hội duy nhất để dọn.

### Kiểm chứng
Lấy `storageKey` của user A, đăng nhập user B gọi `POST /songs` với key đó → phải 403. Gọi `POST /songs` hai lần cùng key → lần 2 phải 409.

---

## F15 · Đọc, sửa, xoá bài hát

`GET /{songId}` · `GET /search` · `GET /suggest` · `PATCH /{songId}` · `DELETE /{songId}`

### Luồng thật
Mọi endpoint bắt đầu bằng `findByIdAndUserId` — **quyền sở hữu nằm trong câu query**, không phải một câu `if` sau đó. Không thấy → `SONG_NOT_FOUND` (404, không phải 403: không tiết lộ bài hát đó có tồn tại hay không).

`DELETE`: xoá `song_tag_config` → xoá `song` → **sau commit** xoá cả `originalS3Key` và `processedS3Key`.

`GET /search`: `Specification` ghép từ khoá + status + khoảng ngày, có phân trang. `GET /suggest`: bản rút gọn, chỉ trả `id` + `title`, dùng cho ô autocomplete.

### Bẫy
- `findByIdAndUserId` thay vì `findById` + `if (!song.getUserId().equals(userId))`: cùng kết quả, nhưng cách thứ hai chỉ cần quên một lần ở một endpoint là thủng. Đưa vào query thì không quên được.
- Xoá object **sau commit**: xoá trước mà transaction rollback thì row còn, file mất.

---

## F16 · Lấy URL phát — `GET /api/v1/songs/{songId}/audio-url`

### Luồng thật
1. `findByIdAndUserId`.
2. `playbackKey()`: **đã xử lý xong thì trả `processedS3Key`, ngược lại trả `originalS3Key`.**
3. Rỗng → `SONG_NOT_UPLOADED`.
4. Ký presigned GET, TTL 15 phút.

### Bẫy
`playbackKey()` nằm **trong entity `Song`**, không trong service. Lý do: có 3 chỗ cần phát audio (API này, live room, admin) — để logic ở service thì phải nhớ lặp lại ở cả 3, và quên một chỗ là ở đó người dùng nghe bản chưa ghép tag.

Quy tắc: bài hát upload kèm voice tag thì nghe bản đã ghép; bài upload trần thì bản gốc *chính là* thứ chủ nhân yêu cầu, không có gì để ghép.

---

## F17 · Voice tag bằng TTS — `POST /api/v1/voice-tags/tts`

Kèm `POST /tts/preview` và `GET /tts/voices`.

### Luồng thật — tạo
1. Kiểm `voiceName` có thuộc `languageCode` không. Không khớp → `TTS_VOICE_NOT_SUPPORTED`.
2. Kiểm trùng tên trong phạm vi user.
3. Gọi Google TTS → nhận bytes → upload lên S3.
4. Lưu row; `DataIntegrityViolationException` → **xoá object vừa upload** rồi trả 409.

### Luồng thật — preview
1. Kiểm voice.
2. Tra **cache Redis** theo `(text, language, voice)`. Trúng → trả luôn, không gọi Google.
3. Trượt → gọi Google, ghi cache với TTL, trả bytes. **Không** ghi S3, **không** ghi DB.

### Bẫy
- Preview cố ý không để lại dấu vết vĩnh viễn — nghe thử rồi không ưng thì không có gì phải dọn. Cache Redis là một nới lỏng có chủ đích của quy tắc đó, đổi lấy tiền: mỗi lần tổng hợp là một lần Google tính phí, mà chọn giọng nghĩa là phát đi phát lại cùng một câu qua nhiều giọng. Tắt được bằng `preview-cache.enabled: false`.
- **Không `@Transactional`**: gọi Google rồi upload S3, giữ connection DB suốt vòng đó là cạn pool. Một lệnh ghi cuối cùng tự có transaction của nó.
- Kiểm trùng tên **không nguyên tử** — unique constraint mới quyết định. Và nhánh catch phải **xoá object đã upload**, nếu không mỗi lần đụng tên là một file rác vĩnh viễn.

---

## F18 · Voice tag bằng upload — `POST /api/v1/voice-tags/upload`

### Luồng thật
1. Kiểm định dạng hỗ trợ.
2. Kiểm dung lượng.
3. **Chạy ffprobe trên bytes đang giữ trong bộ nhớ** để lấy thời lượng thật, và chặn nếu dài quá hạn (`VOICE_TAG_TOO_LONG`).
4. Kiểm trùng tên → upload → lưu row, cùng cách dọn dẹp như F17.

### Bẫy
Bước 3 vừa lấy metadata vừa **là phép kiểm "đây có thật là audio không"** — ffprobe không đọc nổi thì không phải file audio. Bên F14 đạt cùng kết luận bằng cách khác (đọc magic bytes qua ranged read), vì ở đó file nằm trên S3 chứ không trong bộ nhớ. Hai đường khác nhau tới cùng một yêu cầu — đáng để ý khi tự thiết kế.

---

## F19 · Pipeline ghép voice tag

Không có endpoint riêng. Kích hoạt từ F14, và `POST /songs/{songId}/retry-processing`.

### Luồng thật
**Worker** (`SongProcessorWorker`, cố ý **không** `@Transactional`):
1. `loadPending(songId)` — một transaction readOnly riêng: đọc song, **bỏ qua nếu status ≠ `PROCESSING`**, đọc config, bỏ qua nếu `enabled = false`, đọc voice tag, dựng `AudioProcessingRequest` với `outputKey = audio/processed/{userId}/{songId}.mp3`.
2. Chạy ffmpeg (có thể mất vài phút).
3. `markProcessed(...)` — transaction riêng. Trả `true` nếu row đã biến mất → caller xoá file vừa render.
4. Lỗi → `markFailed(...)` trong `REQUIRES_NEW`, rồi ném lại exception gốc.

**Ffmpeg**:
1. Kiểm dung lượng đĩa trống (ước lượng `2.5 × kích thước nguồn`).
2. Tải song + voice tag về thư mục tạm.
3. ffprobe lấy thời lượng cả hai. Chặn nếu `interval` ngắn hơn chính voice tag (`INVALID_TAG_INTERVAL`).
4. Đo chênh lệch âm lượng giữa hai file → ra `matchGainDb`.
5. Dựng filtergraph, chạy ffmpeg **có timeout**, upload kết quả.

**Filtergraph** — hai nhánh trộn lại:
- *tagtrack*: resample 44100 → chỉnh `matchGainDb` → `volume` theo % người dùng → lặp lại mỗi `interval` → `adelay` theo `startOffset`.
- *bed* (bài hát): nếu `duckingPercentage < 100` thì `volume` với biểu thức phụ thuộc `t`, hạ âm lượng đúng những khoảng có tag.
- `amix=duration=first` rồi `alimiter=limit=0.950:level=disabled`.

### Tự code
- **A.** Worker chạy đồng bộ ngay trong `createSong` (chưa hàng đợi). Filtergraph tối giản: một tag duy nhất ở giây 0.
- **B.** Lặp tag theo `interval` + `adelay`, và ducking.
- **C.** Timeout, dọn thư mục tạm, `retry-processing`, tách worker ra chạy bất đồng bộ.

### Bẫy
- **Worker không được `@Transactional`.** Ffmpeg chạy vài phút; giữ connection DB suốt thời gian đó là cạn pool ngay khi có 2 bài cùng lúc. Chia thành 3 transaction ngắn ở đầu, cuối và nhánh lỗi.
- **`markFailed` phải `REQUIRES_NEW`** — cùng lý do với OTP ở F2: transaction chính đã hỏng, ghi vào đó thì mất.
- **`alimiter` là bắt buộc.** Song và tag cộng thẳng vào nhau; một bài đã master sát 0 dBFS sẽ vượt ngưỡng ngay khi có tag, và libmp3lame cắt ngọn. Cắt ngọn không làm tag to hơn — nó nghiền cả hai thành cùng một mảng méo.
- **`level=disabled` trên `alimiter`**: thiếu nó, limiter tự chuẩn hoá đầu ra trở lại 0 dBFS, xoá sạch ducking vừa làm.
- **Timeout là bắt buộc.** `execute()` của Jaffree chặn vô hạn. Một input hỏng khiến ffmpeg treo sẽ giữ thread mãi mãi, và vì worker xử lý một bài một lúc nên **mọi bài phía sau đứng luôn**.
- **Nhánh "orphaned" ở bước 3 rất dễ làm sai.** Chỉ xoá file vừa render khi **row song đã biến mất** (bị xoá lúc đang render, và lúc đó `deleteSong` dọn key khi key merged chưa tồn tại). Kết quả **trùng lặp** thì tuyệt đối **không** xoá — `outputKey` suy ra từ `songId` nên lần render thứ hai ghi đúng vào key mà song đang trỏ tới; xoá là xoá mất audio đang phục vụ.
- `retryProcessing` chỉ cho phép từ `FAILED`. Bài đã `PROCESSED` là bất khả xâm phạm — render lại sẽ đè lên audio đang được nghe.

### Kiểm chứng
Một bài 3 phút + tag 2 giây + `interval=30` → file ra phải có đúng 7 lần tag. Đếm bằng tai hoặc mở waveform trong Audacity.

---

## Xong F13–F19

Sang [03-liveroom.md](03-liveroom.md).
