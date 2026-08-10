# Audio — Voice tag

> `POST /voice-tags/tts` · `POST /voice-tags` (tải lên) · `POST /voice-tags/tts/preview` · `GET /voice-tags/tts/voices` · `GET|PATCH|DELETE /voice-tags/{id}` · `GET /voice-tags/{id}/audio-url`
> Bối cảnh: [Audio — Tour](audio-00-tour.md)

---

## 1. Bài toán

Voice tag là đoạn âm thanh ngắn sẽ được đóng lên bài hát — thường là ai đó đọc tên studio. Người dùng có hai cách tạo, và chúng khác nhau hoàn toàn về nguồn:

| | TTS | Tải lên |
|---|---|---|
| Nguồn | Google Cloud Text-to-Speech | File người dùng có sẵn |
| Cái không kiểm soát được | Dịch vụ ngoài, tốn tiền mỗi lần gọi | Nội dung file, có thể không phải audio |
| Thời lượng | Do Google trả về | **Phải tự đo** |

Nhưng hai đường hội tụ ở cùng một chỗ: một đối tượng trên S3, một hàng trong `audio_voice_tags`, và cùng một ràng buộc — **tên không trùng trong phạm vi một người dùng**, **dài không quá 10 giây**.

---

## 2. Cả hai đường đều không có transaction

```java
/**
 * Deliberately not transactional: synthesis calls out to Google and then uploads to object storage,
 * so wrapping it would pin a database connection for the whole round trip. The single write at the end
 * gets its own transaction, and the uploaded object is reclaimed if that write fails.
 */
```

Hình dạng chung của cả hai:

```
1 · kiểm đầu vào (giọng có tồn tại / định dạng, kích thước, thời lượng)
2 · kiểm tên trùng — không nguyên tử, xem 3.1
3 · việc chậm: gọi Google hoặc đo file  →  tải lên S3
4 · một lần ghi database
    ├─ hỏng vì trùng tên  → xoá đối tượng vừa tải lên, ném DUPLICATE_VOICE_TAG_NAME
    └─ hỏng vì lý do khác → xoá đối tượng vừa tải lên, ném lại nguyên lỗi
```

Bước 4 là chỗ đáng học: **không có transaction thì phải tự viết phần bù**.

```java
try {
    saved = voiceTagRepository.save(VoiceTag.createTtsTag(...));
} catch (DataIntegrityViolationException ex) {
    // The name check above is not atomic; the unique constraint is what actually decides.
    storageCleaner.deleteNow(outcome.s3Key());
    throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
} catch (RuntimeException ex) {
    storageCleaner.deleteNow(outcome.s3Key());
    throw ex;
}
```

### 2.1. Vì sao bắt `DataIntegrityViolationException` riêng

Bước 2 đã kiểm `existsByUserIdAndName`. Nhưng giữa lúc kiểm và lúc ghi có một khoảng trống — hai request cùng lúc đều thấy "tên chưa tồn tại", cả hai đều tổng hợp giọng nói, cả hai đều tải lên S3, rồi **một trong hai thua ở ràng buộc unique**.

Comment nói thẳng: *"the unique constraint is what actually decides"*. Bước kiểm trước chỉ để **trả về lỗi đẹp trong trường hợp thường**, còn thứ thật sự bảo đảm là ràng buộc trong database.

Người thua phải dọn đối tượng vừa tải lên — nếu không, mỗi lần đua sẽ để lại một file mồ côi.

Đây là khuôn mẫu chuẩn cho mọi thao tác "gọi dịch vụ ngoài rồi mới ghi": **kiểm trước cho đẹp, ràng buộc để cho đúng, và bù trừ khi ràng buộc lên tiếng.**

---

## 3. Đường TTS

### 3.1. Giọng phải khớp ngôn ngữ

```java
/**
 * A voice belongs to exactly one language, so accepting one that does not match would either fail at
 * the provider or quietly produce audio in the wrong language. Null means "use the provider default".
 */
private void assertVoiceAvailable(String languageCode, String voiceName) {
    if (voiceName == null || voiceName.isBlank()) return;
    boolean offered = textToSpeechPort.availableVoices(languageCode).stream()
            .anyMatch(voice -> voice.name().equals(voiceName));
    if (!offered) throw new AudioBusinessException(AudioErrorCode.TTS_VOICE_NOT_SUPPORTED);
}
```

Vế *"quietly produce audio in the wrong language"* là phần đáng sợ: gửi `vi-VN-Wavenet-A` kèm `languageCode: en-US` có thể ra một file đọc tiếng Việt với ngữ điệu Anh, không lỗi gì cả. Kiểm trước là cách duy nhất bắt được.

Kiểm này chạy **trước** khi gọi tổng hợp — mà mỗi lần tổng hợp là một lần bị Google tính tiền.

Danh mục giọng có thứ tự cố định:

```java
private static final List<String> CATALOG_LANGUAGE_ORDER = List.of("vi-VN", "en-US", "en-GB");
```

Tiếng Việt đứng đầu. Đây là quyết định sản phẩm nằm trong một hằng số.

> **Chụp thật** từ `GET /voice-tags/tts/voices`: `vi-VN-Wavenet-A` (FEMALE), `vi-VN-Wavenet-B` (MALE), `vi-VN-Wavenet-C`, `vi-VN-Wavenet-D`… — danh sách bắt đầu bằng tiếng Việt đúng như hằng số quy định.

### 3.2. Xem thử không để lại dấu vết

```java
/**
 * Not transactional and deliberately touching nothing but the provider: a preview exists so the user can
 * reject it, and rejected audio should leave no trace in storage or the database.
 */
@Override
public TtsPreview previewVoiceTagTts(String text, String languageCode, String voiceName) {
    assertVoiceAvailable(languageCode, voiceName);
    TtsResult result = textToSpeechPort.synthesize(new TtsRequest(text, languageCode, voiceName));
    return new TtsPreview(result.audioBytes(), result.contentType(), result.durationSeconds());
}
```

Trả **bytes thẳng về response**, không lưu S3, không ghi database. Người dùng nghe, không ưng, gõ lại — không có gì phải dọn.

Nhưng mỗi lần bấm nghe thử là **một lần Google tính tiền**. Đó là lý do endpoint này có luật rate limit riêng, chặt hơn hẳn mặc định, và luật ấy nằm trong `application.yml` kèm comment:

```yaml
# Every preview is a billed Google TTS synthesis, so it gets a much tighter cap than the
# global default that covers ordinary reads and writes.
tts-preview:
  pattern: /api/v1/voice-tags/tts/preview
  methods: [POST]
  limit: 20
  window-seconds: 60
```

**20/phút, so với 500/phút toàn cục.** Đây là endpoint duy nhất trong cả hệ thống có luật rate limit riêng ở tầng `HttpRateLimitFilter` — và lý do là tiền, không phải bảo mật.

### 3.3. Thiếu cấu hình thì tắt, không sập

`GoogleTtsAdapter` khởi tạo client trong khối `try`; thiếu đường dẫn credentials thì:

```
log.warn("Google TTS credentials-path is not configured; TTS features are disabled.")
```

Ứng dụng vẫn khởi động, và mọi lời gọi tổng hợp ném `TTS_CLIENT_NOT_CONFIGURED` (`AUDIO_016`, phân loại `BUSINESS`).

So sánh với IAM: khoá ký JWT thiếu là **không khởi động được** ([02 §5.1](02-lat-cat-doc-dang-nhap.md)). Khác biệt hợp lý — thiếu khoá ký JWT là lỗ hổng bảo mật, thiếu TTS chỉ là thiếu một tính năng. Cùng logic với `GOOGLE_CLIENT_ID` ở [iam-03 §6](iam-03-google-oauth.md).

---

## 4. Đường tải lên: ba lớp kiểm

| Kiểm gì | Cách | Lỗi |
|---|---|---|
| Định dạng | Phần mở rộng của tên file → `AudioFormat.of` | `AUDIO_019` |
| Kích thước | `≤ 10 MB`, và `> 0` | `AUDIO_005` / `AUDIO_018` |
| **Thời lượng** | **Đo bằng ffprobe**, `≤ 10 giây` | `AUDIO_027` |

Hai comment giải thích vì sao hai lớp cuối không thể bỏ:

> *"The extension decides the format, but ffprobe is what proves the bytes are really audio — the check below would otherwise pass for anything renamed to `.mp3`."*

> *"Measured here rather than taken from the request: the client cannot be trusted about it, and a clip longer than the limit would be stamped over a song at an interval it no longer fits inside."*

Vế thứ hai nối thẳng sang [audio-03](audio-03-cau-hinh-ghep-tag.md): nếu tag dài hơn khoảng cách giữa hai lần chèn, các bản sao của tag sẽ chồng lên nhau. Giới hạn 10 giây ở đây là cái chặn từ gốc để pipeline không bao giờ gặp tình huống đó.

Khác với bài hát ([audio-01](audio-01-tai-len-bai-hat.md)), voice tag **đi qua backend** — `uploadBytes`, không phải URL ký sẵn. Hợp lý vì nó chỉ 10 MB và cần đo nội dung trước khi chấp nhận, mà đo thì phải cầm được bytes.

Tên khoá lưu trữ:

```
audio/voice-tags/{userId}/tts-{tên-đã-làm-sạch}-{uuid}.mp3
audio/voice-tags/{userId}/upload-{tên-đã-làm-sạch}-{uuid}.{ext}
```

`safeName` thay mọi ký tự ngoài `[a-zA-Z0-9._-]` bằng `_`. Tên tiếng Việt có dấu sẽ thành một chuỗi gạch dưới — xấu nhưng an toàn, và UUID mới là phần bảo đảm không trùng.

---

## 5. Xoá: chặn khi đang được dùng

```java
if (songTagConfigRepository.existsByVoiceTagId(command.voiceTagId())) {
    throw new AudioBusinessException(AudioErrorCode.VOICE_TAG_IN_USE);
}
voiceTagRepository.deleteById(voiceTag.getId());
storageCleaner.deleteAfterCommit(voiceTag.getS3Key());
```

Không xoá được một tag đang gắn với bài hát nào đó (`AUDIO_007`). Kể cả khi bài hát đã xử lý xong và không cần tag nữa — cấu hình vẫn còn thì tag vẫn bị khoá.

Kết hợp với việc **không có API gỡ cấu hình khỏi bài hát** ([audio-03 §5](audio-03-cau-hinh-ghep-tag.md)), hệ quả là: **một tag đã dùng cho một bài hát thì chỉ xoá được sau khi xoá bài hát đó.**

Xoá là xoá cứng. Migration `V201__soft_delete_uploaded_voice_tags.sql` rồi `V203__hard_delete_voice_tags.sql` cho thấy dự án đã thử xoá mềm rồi quay lại — cùng đường với bài hát (`V202`).

---

## 6. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Không transaction cho cả hai đường tạo | Bọc cả hàm | Không giữ connection qua vòng gọi Google/S3 | Phải tự viết phần bù bằng `deleteNow` |
| Kiểm tên trùng **và** dựa vào ràng buộc unique | Chỉ một trong hai | Lỗi đẹp cho trường hợp thường, đúng cho trường hợp đua | Hai chỗ cùng nói về một quy tắc |
| Kiểm giọng trước khi tổng hợp | Để Google từ chối | Không tốn tiền cho một lời gọi chắc chắn sai; chặn được cả trường hợp sai ngôn ngữ mà không báo lỗi | Thêm một lời gọi liệt kê giọng |
| Xem thử không lưu gì | Lưu rồi cho xoá | Từ chối không để lại rác | Mỗi lần nghe thử là một lần trả tiền |
| Rate limit riêng 20/phút cho preview | Dùng mặc định 500 | Tiền, không phải bảo mật | Luật riêng phải nhớ khi đọc cấu hình |
| Đo thời lượng bằng ffprobe | Tin client | File giả và clip quá dài bị chặn ngay | Mỗi lần tải lên tốn một tiến trình ffprobe |
| Voice tag đi qua backend | URL ký sẵn như bài hát | Phải cầm bytes mới đo được | Chiếm luồng Tomcat, nhưng chỉ ≤10 MB |
| Trần 10 giây | Dài hơn | Bảo đảm tag luôn ngắn hơn khoảng chèn | Không làm được tag dài kiểu jingle |
| Thiếu credentials thì tắt tính năng | Không khởi động | Dev không cần TTS vẫn chạy được | Cấu hình sai chỉ lộ khi có người dùng thử |
| Không xoá được tag đang dùng | Xoá kèm cấu hình | Không làm hỏng bài hát đang trỏ tới | Phải xoá bài hát trước mới xoá được tag |

---

## 7. Tự kiểm chứng

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Xem danh mục giọng, để ý tiếng Việt đứng đầu:**

```bash
curl -s http://localhost:8080/api/v1/voice-tags/tts/voices -H "Authorization: Bearer $T"
```

**Thử giọng không khớp ngôn ngữ** — gửi `voiceName: "vi-VN-Wavenet-A"` với `languageCode: "en-US"`. Nhận `AUDIO_025`, và **Google không bị gọi**.

**Xem rate limit riêng của preview** — gọi `POST /voice-tags/tts/preview` hơn 20 lần trong một phút, để ý header `X-RateLimit-Limit` đổi từ `500` sang `20`.

**Xem hai kiểu voice tag trong database:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT name, type, duration_seconds, voice_name, left(s3_key, 40) FROM audio_voice_tags ORDER BY created_at DESC;"
```

Khoá bắt đầu bằng `audio/voice-tags/<id>/tts-` là TTS, `upload-` là tải lên.

**Thử xoá một tag đang được dùng** — lấy `voiceTagId` xuất hiện trong `audio_song_tag_configs` rồi gọi `DELETE`. Nhận `AUDIO_007`.

**Thử tải lên một file giả** — đổi tên `anh.png` thành `tag.mp3` rồi tải lên. Phần mở rộng qua được, nhưng ffprobe không đo được thời lượng và request bị từ chối.

---

## 8. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Tag đã dùng thì gần như không xoá được | Phải xoá bài hát trước — mục 5 |
| Đua tên trùng vẫn tốn một lần gọi Google | Người thua đã trả tiền tổng hợp trước khi biết mình thua — mục 2.1 |
| Không kiểm được nội dung TTS | Người dùng gõ gì Google đọc nấy; không lọc nội dung |
| Tên có dấu thành gạch dưới trong khoá S3 | Chỉ ảnh hưởng tên đối tượng, không ảnh hưởng tên hiển thị |
| Không có hạn mức TTS theo người dùng | Chỉ có rate limit 20/phút theo IP; một người kiên nhẫn vẫn tiêu được nhiều tiền |
| Không sửa được nội dung một TTS tag | `PATCH` chỉ đổi được tên; muốn đổi lời phải tạo tag mới |
| Cấu hình TTS thiếu chỉ lộ lúc chạy | Mục 3.3 |
