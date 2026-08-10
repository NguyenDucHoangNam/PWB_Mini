# Hạ tầng — Lưu trữ S3

> `shared-infrastructure/infra/storage` + `StoragePort` (Audio) · `StorageService` (IAM)
> Bối cảnh: [bản đồ hệ thống §5](00-ban-do-he-thong.md)

---

## 1. Nguyên tắc chung: bucket không công khai, URL ký sẵn cho từng lượt

Ba loại tệp nằm trên S3, và **không loại nào truy cập được công khai**:

| Loại | Tiền tố khoá | Ai cấp URL |
|---|---|---|
| Bài hát gốc | `audio/originals/{userId}/{uuid}.{ext}` | Audio |
| Bài hát đã đóng dấu | `audio/processed/…` | Audio |
| Voice tag | `audio/voice-tags/{userId}/{tts\|upload}-…` | Audio |
| Ảnh đại diện | `avatars/{userId}/…` | IAM |

Database chỉ lưu **khoá**, không lưu URL. Mỗi lần cần truy cập thì ký một URL tạm.

Ba cái lợi: bucket đóng kín; đổi bucket hay vùng không phải sửa dữ liệu; và **quyền được kiểm ở chỗ cấp URL**, nơi biết ai đang hỏi.

Một cái mất nằm ở chính điểm cuối: **URL đã cấp thì ai cầm cũng dùng được, và không thu hồi được.** Toàn bộ chính sách thời hạn ở mục 3 tồn tại để giảm hậu quả của điều đó.

---

## 2. Ba kiểu đưa tệp lên, chọn theo kích thước

Hệ thống dùng cả ba, và lựa chọn mỗi lần đều có lý do:

| Kiểu | Dùng cho | Vì sao |
|---|---|---|
| **URL ký sẵn để client tự `PUT`** | Bài hát, tới 200 MB | Không tốn luồng Tomcat và băng thông server |
| **Truyền luồng qua backend** | Ảnh đại diện, ≤5 MB | Cần kiểm định dạng; luồng thì không tốn 5 MB heap mỗi lần |
| **`uploadBytes` / `uploadFromPath`** | Voice tag ≤10 MB, bài đã render | Cần cầm bytes để **đo bằng ffprobe** trước khi chấp nhận |

Nguyên tắc: **tệp lớn thì để client tự đưa lên; tệp cần kiểm nội dung thì phải đi qua backend.** Voice tag nhỏ nhưng vẫn qua backend vì [audio-02 §4](audio-02-voice-tag.md) phải đo thời lượng thật — mà đo thì phải có bytes.

Cấu hình `StorageProperties` có phần điều khiển multipart, kèm comment giải thích: một luồng TCP duy nhất qua đường tới vùng xa chủ yếu là ngồi chờ, nên chia phần và tải song song mới nhanh.

---

## 3. Thời hạn URL: năm giá trị khác nhau, và một chỗ bất đối xứng

| Đường | Thời hạn | Vì sao |
|---|---|---|
| Tải bài hát lên | **1 giờ** | 200 MB trên mạng chậm cần thời gian |
| Ảnh đại diện (thường) | **15 phút** | Chỉ cần sống lâu hơn một lượt xem trang |
| Ảnh đại diện trong Live Room | **12 giờ** | Phòng nhận URL một lần, màn hình sống lâu hơn 15 phút |
| Nhạc trong Live Room | **1 giờ** | Một buổi nghe dài hơn một lượt xem trang |
| `GET /songs/{id}/audio-url` | **client chọn**, 1 phút – 1 ngày | Nhu cầu khác nhau thật |
| `GET /voice-tags/{id}/audio-url` | **1 giờ** cố định | Controller quyết định, client không chọn được |

Hai cách khác nhau để giải cùng một vấn đề:

- **IAM** để **bên gọi** truyền TTL vào (`resolve(stored, Duration)`) — Live Room tự xin 12 giờ.
- **Audio** để **client** chọn qua tham số, có kiểm khoảng.

Cách của IAM an toàn hơn: client không tự nâng thời hạn được. Cách của Audio linh hoạt hơn nhưng cần chặn khoảng.

> **Chỗ bất đối xứng:** `GET /songs/{id}/audio-url` cho client chọn TTL qua tham số `expiresIn`, kiểm khoảng 1 phút – 1 ngày (cả `@Min`/`@Max` ở Controller lẫn `assertExpirationInRange` ở UseCase). Còn `GET /voice-tags/{id}/audio-url` cố định 1 giờ tại Controller, không nhận tham số từ client. Hai endpoint phục vụ cùng mục đích nhưng chọn cách tiếp cận khác nhau mà không có lý do rõ ràng.

Cạm bẫy đi kèm cách IAM: **quên truyền TTL dài là hỏng lặng lẽ.** URL ảnh đại diện trong phòng hết hạn sau 15 phút sẽ không báo lỗi — nó chỉ trở thành ảnh vỡ, và client rơi về hiển thị chữ cái đầu tên.

---

## 4. Xoá tệp: ba chính sách, tất cả đều "thà rác còn hơn hỏng"

| Chỗ | Cách xoá | Nếu xoá hỏng |
|---|---|---|
| Xoá bài hát / voice tag | `StorageCleaner.deleteAfterCommit` | Log `error` kèm *"manual cleanup required"* |
| Ghi database hỏng sau khi đã tải lên | `StorageCleaner.deleteNow` | Như trên |
| Thay ảnh đại diện | `deleteQuietly` trong IAM | Log `warn` *"Orphaned previous avatar object"* |

`deleteAfterCommit` xử lý cả trường hợp không có transaction:

```java
if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    keys.forEach(this::deleteQuietly);
    return;
}
```

Thứ tự bắt buộc: **commit trước, xoá tệp sau**. Xoá trước rồi rollback là bản ghi còn mà tệp mất — hỏng hẳn. Xoá sau commit thì xấu nhất là bản ghi mất mà tệp còn — rác, vô hại.

**Không có công việc dọn rác nào tồn tại.** Ba nguồn rác đã biết:

1. Tệp tải lên rồi bị `POST /songs` từ chối vì quá to ([audio-01 §9](audio-01-tai-len-bai-hat.md)) — URL ký sẵn không mang giới hạn kích thước
2. Ảnh đại diện cũ không xoá được
3. Đối tượng mồ côi khi việc ghi database hỏng giữa chừng

Cả ba đều chỉ tốn dung lượng, không làm sai dữ liệu — đó là lý do đánh đổi này chấp nhận được. Nhưng dung lượng thì chỉ tăng.

---

## 5. Hai cổng cho cùng một dịch vụ

| Module | Cổng | Ghi chú |
|---|---|---|
| Audio | `StoragePort` (`domain/service`) | `presignUpload`, `presignDownload`, `findMetadata`, `uploadBytes`, `uploadFromPath`, `downloadToPath`, `delete` |
| IAM | `StorageService` | `upload`, `delete`, `generatePresignedUrl` |
| Live Room | *không có cổng riêng* | Mượn `StoragePort` của Audio qua `AudioSongCatalogAdapter` |

Hai cổng khác tên cho cùng một hạ tầng, mỗi cổng khai báo đúng những gì module mình cần — đúng tinh thần "cổng thuộc về bên gọi" ([01 §6](01-architecture-overview.md)). Cái giá là hai adapter cùng bọc một client S3.

Live Room không tự khai báo cổng lưu trữ mà đi qua Audio — hợp lý, vì nó không sở hữu tệp nào, chỉ xin URL cho bài hát của người khác.

---

## 6. Phân biệt "khoá của mình" với "URL của người ta"

Cột `avatar_url` chứa **hai loại giá trị**: khoá S3 (`avatars/…`) hoặc URL Google đầy đủ (`https://lh3.googleusercontent.com/…`) với tài khoản đăng nhập bằng Google.

Nên có hai chỗ phải phân biệt:

```java
// AvatarUrlResolver — không ký URL cho một URL đã đầy đủ
if (storedReference == null || storedReference.isBlank() || isAbsoluteUrl(storedReference)) {
    return storedReference;
}
```

```java
// UpdateAvatarUseCaseImpl.deleteQuietly — không gọi S3 delete với một URL của Google
if (previousKey.startsWith("http://") || previousKey.startsWith("https://")) return;
```

Cùng một quy tắc, viết ở hai nơi, không dùng chung hàm nào. Thêm chỗ thứ ba đụng tới `avatar_url` là thêm một chỗ phải nhớ.

---

## 7. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Bucket kín, URL ký sẵn từng lượt | Bucket công khai + URL cố định | Kiểm quyền ở chỗ cấp URL | Mỗi lượt đọc tốn một lần ký |
| Lưu khoá, không lưu URL | Lưu URL | Đổi bucket không phải sửa dữ liệu | Phải giải khoá ở mọi đường trả về |
| Bài hát: client tự tải lên | Qua backend | Không tốn băng thông server | Backend không thấy tệp, phải kiểm gián tiếp |
| Voice tag: qua backend | URL ký sẵn | Cần bytes để đo ffprobe | Chiếm luồng Tomcat, nhưng chỉ ≤10 MB |
| Thời hạn khác nhau theo ngữ cảnh | Một giá trị chung | Nhu cầu thật sự khác nhau | Bốn con số phải nhớ; quên là ảnh vỡ lặng lẽ |
| IAM: TTL do bên gọi truyền | Client chọn | Client không tự nâng thời hạn | Bên gọi phải nhớ truyền |
| Audio Song: TTL do client chọn, có trần | Server quyết định | Linh hoạt cho nhiều loại màn hình | Phải kiểm khoảng; voice tag chọn cách khác (cố định) không rõ lý do |
| Xoá tệp sau commit | Trong transaction | Rollback không làm mất tệp | Sinh rác khi commit rồi xoá hỏng |
| Xoá hỏng chỉ log | Ném lỗi | Ảnh/bài mới đã sống, không có gì để undo | Rác tích tụ, không ai dọn |
| Hai cổng cho một dịch vụ | Một cổng chung | Mỗi module khai báo đúng cái mình cần | Hai adapter bọc cùng một client |

---

## 8. Tự kiểm chứng

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Xem một URL ký sẵn và thời hạn của nó:**

```bash
curl -s -X POST http://localhost:8080/api/v1/songs/upload-url -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"format":"mp3"}'
```

`expiresAt` cách hiện tại đúng 1 giờ. Phần query của URL chứa `X-Amz-Signature`, `X-Amz-Expires` — đó là chữ ký.

**Thấy bucket thật sự kín** — lấy phần URL **trước dấu `?`** rồi mở:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://<bucket>.s3.<region>.amazonaws.com/audio/originals/<userId>/<uuid>.mp3"
```

`403` — không có chữ ký thì không đọc được.

**Xem database lưu khoá chứ không lưu URL:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT left(original_s3_key,50) AS goc, left(coalesce(processed_s3_key,'-'),50) AS da_xu_ly FROM audio_songs LIMIT 5;"
```

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT email, left(coalesce(avatar_url,'(chua co)'),60) FROM iam_users;"
```

Ở bảng thứ hai: giá trị bắt đầu bằng `avatars/` là khoá S3, bắt đầu bằng `https://` là ảnh Google — đúng hai loại ở mục 6.

**Thấy chỗ bất đối xứng về thời hạn** — xin URL nhạc với thời hạn 2 ngày:

```bash
curl -s "http://localhost:8080/api/v1/songs/<songId>/audio-url?expiresIn=172800" -H "Authorization: Bearer $T"
```

Bị từ chối (vượt trần 86 400 giây). Gọi `GET /voice-tags/{id}/audio-url` thì không có tham số để chọn — endpoint luôn trả URL 1 giờ cố định, bất kể client muốn gì.

**Thấy URL hết hạn** — lấy một URL ảnh đại diện (15 phút), mở được ngay; chờ quá 15 phút rồi mở lại, S3 trả `AccessDenied` kèm `Request has expired`.

> **Lưu ý khi làm thí nghiệm ở local:** URL ký sẵn trỏ tới bucket cấu hình trong `.env`. Nếu đó là bucket production thì mọi thí nghiệm tải lên ở đây đều ghi vào bucket thật — kiểm tra trước khi chạy.

---

## 9. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Không có công việc dọn rác** | Ba nguồn rác đã biết, không cái nào được dọn — mục 4 |
| URL ký sẵn không thu hồi được | Chỉ giới hạn được bằng thời hạn |
| URL tải lên không mang giới hạn kích thước | Tệp quá to lên được bucket rồi mới bị từ chối, và nằm lại |
| `voice-tags/audio-url` cố định 1 giờ, không cho client tuỳ chỉnh | Song cho chọn, voice tag không — mục 3 |
| Quên truyền TTL dài là ảnh vỡ lặng lẽ | Mục 3 |
| Logic "khoá vs URL ngoài" nằm hai chỗ | Mục 6 |
| Không kiểm nội dung tệp bài hát | Chỉ kiểm phần mở rộng và kích thước; tệp giả chỉ lộ khi FFmpeg chạy |
| Không có hạn ngạch dung lượng | Không giới hạn tổng dung lượng mỗi người dùng |
| Không có phiên bản đối tượng | Ghi đè là mất bản cũ; khoá có UUID nên hiếm, nhưng không có lưới an toàn |
