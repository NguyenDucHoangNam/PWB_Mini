# Audio — Phát nhạc & tìm kiếm

> `GET /songs/{id}/audio-url` · `GET /voice-tags/{id}/audio-url` · `GET /songs/search|suggest` · `GET /voice-tags/search|suggest`
> Bối cảnh: [Audio — Tour](audio-00-tour.md) · Cơ chế tìm kiếm chung: [iam-06](iam-06-tim-kiem-nguoi-dung.md)

---

## 1. Phát nhạc: một hàm quyết định nghe bản nào

```java
public String playbackKey() {
    return isProcessed() ? processedS3Key : originalS3Key;
}
```

Hai dòng này là toàn bộ chính sách phát nhạc của hệ thống. Và `isProcessed()` có comment đáng đọc:

> *Whether the song is finished as far as a listener is concerned. Both a plain upload and a completed merge qualify — the distinction between them is a detail of the merge job. Only a run still in flight, or one that failed and so has no trustworthy audio, is held back.*

Bảng đầy đủ:

| Trạng thái | Phát cái gì |
|---|---|
| `UPLOADED` (không có voice tag) | Bản gốc |
| `PROCESSING` | Bản gốc — bản đã ghép chưa tồn tại |
| `PROCESSED` | **Bản đã đóng dấu** |
| `FAILED` | Bản gốc |

Nghĩa là **một bài hát luôn nghe được**, kể cả khi việc đóng dấu hỏng. Chọn như vậy vì bản gốc là thứ người dùng đã tải lên và chắc chắn dùng được; giữ lại không cho nghe chỉ vì một job phụ hỏng là phạt người dùng vì lỗi của hệ thống.

Mặt trái: một bài `FAILED` **vẫn phát và vẫn tải về được, không có dấu**. Nếu ai đó chia sẻ link mà không để ý trạng thái, bản gốc đi ra ngoài — đúng thứ voice tag sinh ra để ngăn. Không có cảnh báo nào ở tầng API; client phải tự đọc `status`.

Chưa có khoá nào để phát thì `AUDIO_008 SONG_NOT_UPLOADED`.

---

## 2. URL ký sẵn, và giới hạn thời hạn

```java
private static final Duration MIN_AUDIO_URL_EXPIRATION = Duration.ofMinutes(1);
private static final Duration MAX_AUDIO_URL_EXPIRATION = Duration.ofDays(1);
```

Client **tự chọn thời hạn**, trong khoảng 1 phút đến 1 ngày. Ngoài khoảng thì `IllegalArgumentException` → `INVALID_REQUEST`.

Vì sao cho client chọn: nhu cầu khác nhau thật. Một trang nghe thử cần vài phút; một phòng Live Room nhận URL một lần rồi giữ suốt buổi cần lâu hơn nhiều ([iam-04 §2](iam-04-profile-va-avatar.md) mô tả cùng vấn đề với ảnh đại diện, và giải bằng cách khác — TTL do bên gọi truyền vào chứ không do client).

Vì sao vẫn phải có trần: không có trần thì client xin URL sống 10 năm, và URL ký sẵn **không thu hồi được**. Một link rò ra ngoài là mất bài hát trong suốt thời hạn đó.

Cùng cơ chế cho voice tag (`GET /voice-tags/{id}/audio-url`), nhưng ở đó **không có kiểm khoảng** — `expiration` truyền thẳng xuống `presignDownload`. Một sự bất đối xứng giữa hai endpoint gần như giống hệt nhau.

Mọi đường đều qua `requireOwnedSong` / `requireOwnedVoiceTag`, tức là **chỉ chủ sở hữu xin được URL**. Nhưng URL đã cấp thì ai cầm cũng dùng được — đó là bản chất của URL ký sẵn.

---

## 3. Tìm kiếm: giống IAM, khác hai chỗ

Cơ chế nền là một truy vấn JPA Specification chạy thẳng trên Postgres — **giống hệt** và đã mô tả đầy đủ ở [iam-06](iam-06-tim-kiem-nguoi-dung.md), gồm cả những gì `LIKE '%…%'` không làm được: không bỏ dấu tiếng Việt, không chịu được gõ sai, không xếp hạng. Không lặp lại ở đây.

> Trước ngày 2026-08-10 chặng này chạy trên Elasticsearch, và mục này từng mô tả một cái bẫy đáng nhớ: engine trả về id đã xếp hạng, nhưng `findAllByIdIn` lấy hàng về **theo thứ tự của Postgres**, nên phải duyệt theo danh sách id chứ không theo kết quả database — quên là mất sạch thứ hạng mà danh sách trông vẫn hợp lệ. Cái bẫy đó không còn chỗ tồn tại: không còn engine, không còn bước lấy id rồi hydrate lại.

Hai điểm khác so với IAM:

### 3.1. Tìm kiếm bị giới hạn theo chủ sở hữu

`SongSearchCriteria` mang `userId`, và `SongSpecifications.fromCriteria` bắt đầu bằng `ownedBy(criteria.userId())` — điều kiện này **không có nhánh nào bỏ qua được**, khác với bốn điều kiện còn lại đều tuỳ chọn:

```java
public static Specification<SongJpaEntity> fromCriteria(SongSearchCriteria criteria) {
    Specification<SongJpaEntity> spec = ownedBy(criteria.userId());

    if (criteria.hasKeyword())          spec = spec.and(titleContains(criteria.keyword()));
    if (!criteria.statuses().isEmpty()) spec = spec.and(statusIn(criteria.statuses()));
    if (criteria.format() != null && !criteria.format().isBlank()) {
        spec = spec.and(hasFormat(criteria.format()));
    }
    if (criteria.minDurationSeconds() != null || criteria.maxDurationSeconds() != null) {
        spec = spec.and(durationBetween(criteria.minDurationSeconds(), criteria.maxDurationSeconds()));
    }
    return spec;
}
```

Người dùng chỉ tìm được **bài hát của chính mình**. Khác hẳn IAM, nơi tìm kiếm là công cụ của admin để duyệt toàn bộ người dùng. Ở Audio, tìm kiếm là công cụ cá nhân — không có tìm kiếm toàn cục, không có khám phá nhạc của người khác.

Đây cũng là chỗ Audio lọc nhiều hơn IAM hẳn: ngoài từ khoá còn có trạng thái (nhiều giá trị), định dạng file, và khoảng thời lượng — `durationBetween` xử lý cả ba trường hợp chỉ có cận dưới, chỉ có cận trên, hoặc có cả hai.

### 3.2. Truy vấn nằm trong repository, không ở tầng application

`songRepository.search(criteria, pageable)` — Audio đóng gói truy vấn vào repository, trong khi IAM gọi thẳng `UserSpecifications` và `UserJpaRepository` từ tầng application. Kết quả giống nhau; Audio giữ được ranh giới tầng sạch hơn, use case không biết gì về JPA.

Cả `search` lẫn `suggest` đều có `MAX_SUGGESTIONS = 20` và trả rỗng ngay khi không có từ khoá — giống IAM.

---

## 4. Waveform: có ở giao diện, không có ở backend

`docs/waveform-precompute-plan.md` mô tả kế hoạch tính trước dạng sóng để hiển thị. Trạng thái thật:

- **Backend: chưa có gì.** Không file `.java` nào trong `modules/` hay `shared/` nhắc tới `waveform`.
- **Frontend: có.** `Frontend/src/features/liveroom/components/music/track-waveform.tsx` cùng vài chỗ khác.

Nghĩa là dạng sóng hiện được **vẽ ở phía client**, từ file audio mà trình duyệt tải về. Kế hoạch tính trước ở server vẫn là kế hoạch.

Hệ quả thực tế: mỗi người trong một phòng Live Room tự phân tích lại cùng một file để vẽ cùng một hình.

---

## 5. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| `FAILED` vẫn phát bản gốc | Chặn không cho nghe | Không phạt người dùng vì lỗi của hệ thống | Bản chưa đóng dấu vẫn ra ngoài được |
| Client chọn thời hạn URL | Server quyết định | Nhu cầu khác nhau thật (trang nghe thử vs phòng nghe chung) | Phải có trần, và client phải nhớ chọn cho đúng |
| Trần 1 ngày | Dài hơn | URL ký sẵn không thu hồi được | Nhu cầu lâu hơn phải xin lại |
| Chỉ chủ sở hữu xin được URL | Ai cũng xin được | Kiểm quyền ở chỗ cấp, không ở chỗ dùng | URL đã cấp thì ai cầm cũng dùng |
| Tìm kiếm giới hạn theo người dùng | Tìm toàn cục | Nhạc chưa phát hành là riêng tư | Không có khám phá, không có tìm kiếm công khai |
| `ownedBy` là điều kiện không bỏ qua được | Để người gọi tự thêm bộ lọc chủ sở hữu | Một chỗ quên là lộ nhạc của người khác | — |
| Vẽ waveform ở client | Tính trước ở server | Không cần thêm bước xử lý | Mỗi người trong phòng phân tích lại cùng một file |

---

## 6. Tự kiểm chứng

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Lấy URL phát nhạc:**

```bash
curl -s "http://localhost:8080/api/v1/songs/<songId>/audio-url" -H "Authorization: Bearer $T"
```

**Xem `playbackKey` chọn bản nào** — so khoá trong URL với hai cột trong database:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT title, status, left(original_s3_key,45) AS goc, left(coalesce(processed_s3_key,'(chua co)'),45) AS da_dong_dau FROM audio_songs ORDER BY created_at DESC;"
```

Bài `PROCESSED` phải cho URL trỏ tới `audio/processed/…`; bài khác trỏ tới `audio/originals/…`.

**Thử thời hạn ngoài khoảng** — truyền `expiration` là 2 ngày, nhận `INVALID_REQUEST`. Rồi thử cùng giá trị đó với `GET /voice-tags/{id}/audio-url`: **được chấp nhận**, đúng như sự bất đối xứng ở mục 2.

**Tìm kiếm:**

```bash
curl -s "http://localhost:8080/api/v1/songs/search?keyword=intro" -H "Authorization: Bearer $T"
```

**Thấy tìm kiếm bị giới hạn theo chủ sở hữu** — đăng nhập bằng `pro2@gmail.com` và tìm cùng từ khoá. Không thấy bài của `pro1`.

**Thấy các bộ lọc cùng hoạt động** — từ khoá, trạng thái và khoảng thời lượng trong một lần gọi:

```bash
curl -s "http://localhost:8080/api/v1/songs/search?keyword=intro&status=PROCESSED&minDuration=60&maxDuration=300" -H "Authorization: Bearer $T"
```

**Thấy giới hạn của việc không bỏ dấu** — đặt tên một bài có dấu rồi tìm bằng chuỗi không dấu. Bài đó **sẽ không xuất hiện**, đúng như thiết kế hiện tại.

---

## 7. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Bài `FAILED` phát ra bản không có dấu | Không cảnh báo ở tầng API — mục 1 |
| URL đã cấp không thu hồi được | Bản chất của URL ký sẵn; chỉ hạn chế được bằng thời hạn |
| `voice-tags/audio-url` không kiểm khoảng thời hạn | Bất đối xứng với `songs/audio-url` — mục 2 |
| Không có tìm kiếm công khai | Chỉ tìm được nhạc của chính mình — mục 3.1 |
| Tìm kiếm không bỏ dấu, không xếp hạng | `LIKE '%…%'` trên tiêu đề; chi tiết ở [iam-06 §2](iam-06-tim-kiem-nguoi-dung.md) |
| Waveform tính lại ở mỗi client | Kế hoạch tính trước chưa cài đặt — mục 4 |
| Không có phát trực tuyến theo đoạn | Client tải cả file; không hỗ trợ HLS hay range request do backend điều khiển |
| Không đếm lượt nghe | Không có gì ghi lại việc một bài đã được phát |
