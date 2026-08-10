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

## 3. Tìm kiếm: giống IAM, khác một chỗ quan trọng

Cơ chế nền — outbox → Kafka → Elasticsearch, `Optional.empty()` nghĩa là "không trả lời được", ES xếp hạng và Postgres cấp dữ liệu — **giống hệt** và đã mô tả đầy đủ ở [iam-06](iam-06-tim-kiem-nguoi-dung.md). Không lặp lại ở đây.

Ba điểm khác:

### 3.1. Thứ hạng được áp lại bằng tay

```java
/**
 * The engine ranked the ids; the rows come from Postgres so nothing renders from a stale copy. The
 * database returns them in its own order, so the ranking is re-applied here — losing it would leave
 * a "most relevant first" list sorted by nothing in particular.
 */
List<Song> ordered = hits.ids().stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .toList();
```

Vế thứ hai là chỗ dễ sai nhất khi cài đặt kiểu "ES xếp hạng, DB cấp dữ liệu": `findAllByIdIn` trả về theo thứ tự của Postgres, **không** theo thứ tự đã hỏi. Lấy thẳng kết quả đó ra hiển thị là mất toàn bộ công xếp hạng — danh sách trông vẫn hợp lệ nên bug này rất khó nhận ra.

Cách chữa: duyệt theo `hits.ids()` rồi tra map, không duyệt theo kết quả database.

`filter(Objects::nonNull)` xử lý id đã bị xoá, kèm comment: *"the index is eventually consistent, so it can briefly point at something that is already gone."*

Cùng vấn đề, cùng cách chữa như [iam-06 §4](iam-06-tim-kiem-nguoi-dung.md) — nhưng ở đây comment nói rõ **vì sao** phải áp lại thứ tự, còn bên IAM thì không.

### 3.2. Tìm kiếm bị giới hạn theo chủ sở hữu

`SongSearchCriteria` mang `userId`, và mọi truy vấn đều lọc theo nó. Người dùng chỉ tìm được **bài hát của chính mình**.

Khác hẳn IAM, nơi tìm kiếm là công cụ của admin để duyệt toàn bộ người dùng. Ở Audio, tìm kiếm là công cụ cá nhân — không có tìm kiếm toàn cục, không có khám phá nhạc của người khác.

### 3.3. Đường ngã về dùng Specification riêng

`songRepository.search(criteria, pageable)` — Audio đóng gói đường ngã về vào repository, trong khi IAM gọi thẳng `UserSpecifications` từ tầng application. Kết quả giống nhau; Audio giữ được ranh giới tầng sạch hơn.

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
| Áp lại thứ hạng bằng tay | Dùng thứ tự Postgres trả về | Giữ được kết quả xếp hạng | Một vòng lặp phải nhớ viết ở mọi chỗ dùng khuôn này |
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

**Thấy đường ngã về:**

```bash
docker stop pwb-elasticsearch
```

`search` vẫn trả 200, log có `SEARCH.songs fallback to database`. Bật lại bằng `docker start pwb-elasticsearch`.

**Đếm document trong index nhạc:**

```bash
curl -s "http://localhost:9200/pwb_songs/_count?pretty"
```

---

## 7. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Bài `FAILED` phát ra bản không có dấu | Không cảnh báo ở tầng API — mục 1 |
| URL đã cấp không thu hồi được | Bản chất của URL ký sẵn; chỉ hạn chế được bằng thời hạn |
| `voice-tags/audio-url` không kiểm khoảng thời hạn | Bất đối xứng với `songs/audio-url` — mục 2 |
| Không có tìm kiếm công khai | Chỉ tìm được nhạc của chính mình — mục 3.2 |
| Waveform tính lại ở mỗi client | Kế hoạch tính trước chưa cài đặt — mục 4 |
| Không có phát trực tuyến theo đoạn | Client tải cả file; không hỗ trợ HLS hay range request do backend điều khiển |
| Không đếm lượt nghe | Không có gì ghi lại việc một bài đã được phát |
