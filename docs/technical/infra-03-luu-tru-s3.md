# Hạ tầng — Lưu trữ S3

> `shared-infrastructure/infra/storage` + `StoragePort` (Audio) · `StorageService` (IAM)
> Bối cảnh: [bản đồ hệ thống §5](00-ban-do-he-thong.md)

---

## 1. Nguyên tắc chung: bucket không công khai, URL ký sẵn cho từng lượt

Ba loại tệp nằm trên S3, và **không loại nào truy cập được công khai**:

| Loại | Tiền tố khoá | Ai cấp URL |
|---|---|---|
| Tệp vừa tải lên, chưa đăng ký | `audio/staging/{userId}/{uuid}.{ext}` | Audio |
| Bài hát gốc (đã đăng ký) | `audio/originals/{userId}/{uuid}.{ext}` | Audio |
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
| Nhạc trong Live Room | **15 phút** | Client tự gia hạn trước khi hết, nên không cần dài hơn |
| `GET /songs/{id}/audio-url` | **15 phút** cố định | UseCase quyết định, client không chọn được |
| `GET /voice-tags/{id}/audio-url` | **1 giờ** cố định | Controller quyết định, client không chọn được |

Không đường nào để **client** chọn thời hạn nữa. Chỗ duy nhất còn nhận TTL từ bên gọi là **IAM** (`resolve(stored, Duration)`), và bên gọi ở đó là server — Live Room tự xin 12 giờ cho ảnh đại diện.

Lý do bỏ: URL ký sẵn **là** credential, và không thu hồi được (mục 1). Thời hạn của nó vì thế là một thiết lập bảo mật, không phải sở thích của client. `GET /songs/{id}/audio-url` từng nhận `expiresIn` tới **86 400 giây**, nghĩa là bất kỳ ai gọi được endpoint đều tự cấp cho mình một đường dẫn công khai sống trọn một ngày — xoá bài, huỷ gói hay khoá tài khoản đều không làm nó ngừng hoạt động. Client thực tế chưa bao giờ truyền tham số đó, nó chỉ dùng mặc định.

15 phút đủ vì `usePresignedUrl` (Frontend) gia hạn trước hạn 5 phút, nên người nghe để trang mở cả buổi vẫn không đứt — cái ngắn lại chỉ là khoảng sống của một URL lỡ lọt ra ngoài.

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

**Không có công việc dọn rác nào chạy trong ứng dụng** — việc đó đẩy sang lifecycle rule của bucket, và code được sắp lại để rule đó an toàn.

Chìa khoá là **tách tiền tố**. Tệp mới `PUT` lên nằm ở `audio/staging/`; `POST /songs` copy nó sang `audio/originals/` (copy phía S3, bytes không qua backend) rồi mới ghi hàng, và chỉ xoá bản staging **sau khi commit**. Nhờ vậy không hàng nào trong database trỏ vào `audio/staging/`, nên **mọi thứ còn lại ở đó theo định nghĩa là rác** và hết hạn sau một ngày — xem [cấu hình bucket](../storage/bucket-configuration.md).

Thứ tự hai chiều đều có lý do: copy **trước** khi ghi, để không có hàng nào trỏ vào đối tượng chưa tồn tại; xoá bản staging **sau** commit, để rollback trả tệp về đúng chỗ client đặt nó thay vì phá mất.

Ba nguồn rác đã bịt:

- **Tệp tải lên rồi không đăng ký:** nay nằm trong `audio/staging/` và hết hạn sau một ngày.
- **Tệp quá to nằm lại bucket:** trần kích thước nay ký vào URL, nên tệp vượt hạn mức không lên được bucket ngay từ đầu.
- **Bản render mồ côi khi bài bị xoá giữa lúc ghép:** `markProcessed` nay báo lại cho `SongProcessorWorker` rằng hàng đã biến mất, và worker xoá tệp vừa tải lên. Trường hợp *kết quả trùng lặp* thì cố ý **không** xoá — khoá đầu ra suy ra từ `songId`, nên nó chính là tệp bài hát đang phát.

Còn lại:

1. Ảnh đại diện cũ không xoá được (S3 lỗi lúc thay ảnh)
2. Đối tượng mồ côi khi việc ghi database hỏng giữa chừng — bao gồm cả trường hợp hiếm là copy xong mà commit ngã, để lại bản copy trong `audio/originals/`, ngoài tầm lifecycle rule

Cả hai chỉ tốn dung lượng, không làm sai dữ liệu — đó là lý do đánh đổi này chấp nhận được.

---

## 5. Hai cổng cho cùng một dịch vụ

| Module | Cổng | Ghi chú |
|---|---|---|
| Audio | `StoragePort` (`domain/service`) | `presignUpload`, `presignDownload`, `findMetadata`, `readHead`, `copy`, `uploadBytes`, `uploadFromPath`, `downloadToPath`, `delete` |
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
| IAM: TTL do bên gọi (server) truyền | Client chọn | Client không tự nâng thời hạn | Bên gọi phải nhớ truyền |
| Audio: TTL cố định phía server | Client chọn qua tham số | URL là credential không thu hồi được, nên thời hạn là thiết lập bảo mật | Màn hình cần lâu hơn phải gia hạn, không xin dài |
| Ký cả `Content-Type` và `Content-Length` vào URL tải lên | Chỉ ký khoá | S3 tự từ chối tệp sai loại hoặc quá cỡ **trước khi** nhận bytes | Client phải khai đúng kích thước trước, và gửi lại đúng content type server trả về |
| `Content-Disposition: attachment` trên mọi URL tải xuống | Để trình duyệt tự xử theo content type đã lưu | Nội dung là do người tải lên quyết định; ép tải về thì HTML lưu trong bucket không chạy được | Không mở trực tiếp trên tab được nữa (thẻ `<audio>`/`<img>` không bị ảnh hưởng) |
| Một đối tượng ứng đúng một bài hát (unique index) | Không ràng buộc | Đăng ký trùng khoá thì xoá bài này làm mất tiếng bài kia | Thêm một index, và một nhánh lỗi `AUDIO_029` |
| Tải lên vào `audio/staging/`, copy sang `audio/originals/` khi đăng ký | Để nguyên một tiền tố | Tách xong thì mọi thứ còn trong staging chắc chắn là rác, nên lifecycle rule xoá được mà không sợ chạm nhạc thật | Một lệnh copy S3 mỗi bài, và một cửa sổ hẹp lúc commit ngã để lại bản copy mồ côi |
| Xoá tệp sau commit | Trong transaction | Rollback không làm mất tệp | Sinh rác khi commit rồi xoá hỏng |
| Xoá hỏng chỉ log | Ném lỗi | Ảnh/bài mới đã sống, không có gì để undo | Rác tích tụ, không ai dọn |
| Hai cổng cho một dịch vụ | Một cổng chung | Mỗi module khai báo đúng cái mình cần | Hai adapter bọc cùng một client |

---

## 8. Tự kiểm chứng

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"user1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Xem một URL ký sẵn và thời hạn của nó:**

```bash
curl -s -X POST http://localhost:8080/api/v1/songs/upload-url -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"format":"mp3","sizeBytes":4096}'
```

`expiresAt` cách hiện tại đúng 1 giờ. Phần query của URL chứa `X-Amz-Signature`, `X-Amz-Expires` — đó là chữ ký — và `X-Amz-SignedHeaders=content-length;content-type;host`: hai header đó nằm **trong** chữ ký, nên S3 sẽ từ chối một `PUT` khai khác đi. Response cũng trả `contentType`, là giá trị client bắt buộc gửi lại y nguyên.

**Thấy chữ ký thật sự ràng buộc** — xin URL cho 4096 byte rồi `PUT` một body khác cỡ:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X PUT "<url>" -H "Content-Type: audio/mpeg" --data-binary @<tệp-khác-cỡ>
```

`403` kèm `SignatureDoesNotMatch`. Đổi `Content-Type` sang `text/html` cũng vậy — đó là chỗ bịt lại việc lưu HTML vào bucket.

**Thấy trần kích thước chặn từ trước** — xin URL cho tệp vượt hạn mức:

```bash
curl -s -X POST http://localhost:8080/api/v1/songs/upload-url -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"format":"mp3","sizeBytes":999999999}'
```

`AUDIO_005` (`FILE_TOO_LARGE`) — bị từ chối khi **chưa** có byte nào rời khỏi máy client, khác với trước đây là tệp lên tới bucket rồi mới bị từ chối lúc đăng ký.

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

**Thấy client không chọn được thời hạn** — thử xin URL nhạc sống 2 ngày:

```bash
curl -s "http://localhost:8080/api/v1/songs/<songId>/audio-url?expiresIn=172800" -H "Authorization: Bearer $T"
```

Tham số bị bỏ qua hoàn toàn: `expiresAt` trả về vẫn là 15 phút. Endpoint không còn đọc `expiresIn` nữa.

**Thấy một đối tượng chỉ ứng một bài** — đăng ký cùng một `originalS3Key` hai lần:

```bash
curl -s -X POST http://localhost:8080/api/v1/songs -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"title":"lan 2","originalS3Key":"<khoá đã đăng ký>","durationSeconds":10,"format":"mp3"}'
```

`AUDIO_029` (`UPLOAD_ALREADY_REGISTERED`).

**Thấy URL hết hạn** — lấy một URL ảnh đại diện (15 phút), mở được ngay; chờ quá 15 phút rồi mở lại, S3 trả `AccessDenied` kèm `Request has expired`.

> **Lưu ý khi làm thí nghiệm ở local:** URL ký sẵn trỏ tới bucket cấu hình trong `.env`. Nếu đó là bucket production thì mọi thí nghiệm tải lên ở đây đều ghi vào bucket thật — kiểm tra trước khi chạy.

---

## 9. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Dọn rác nằm ở phía bucket, không ở code** | Code tách `audio/staging/` ra để rule an toàn, nhưng bản thân rule là cấu hình bucket — không test nào bắt được nếu ai đó tắt nó, xem [cấu hình bucket](../storage/bucket-configuration.md) |
| Mỗi lần đăng ký bài tốn thêm một lần copy S3 | Giá phải trả cho việc tách tiền tố; copy chạy phía S3 nên không tốn băng thông backend, nhưng vẫn là một lệnh gọi có thể hỏng |
| URL ký sẵn không thu hồi được | Chỉ giới hạn được bằng thời hạn — nay là 15 phút cho nhạc |
| Cấu hình bucket không nằm trong repo | Mã hoá mặc định, chặn truy cập công khai, lifecycle, CORS đều là thao tác tay; không có IaC nào kiểm chứng — xem [cấu hình bucket](../storage/bucket-configuration.md) |
| Quên truyền TTL dài là ảnh vỡ lặng lẽ | Mục 3 |
| Logic "khoá vs URL ngoài" nằm hai chỗ | Mục 6 |
| Kiểm nội dung bài hát chỉ đọc magic bytes | Đủ để loại tệp không phải audio; không chứng minh tệp giải mã được — cái đó chỉ lộ khi FFmpeg chạy |
| Không có hạn ngạch dung lượng | Trần theo **từng tệp** đã có (ký vào URL), nhưng tổng dung lượng mỗi người dùng thì không giới hạn |
| Không có phiên bản đối tượng | Ghi đè là mất bản cũ; khoá có UUID nên hiếm, nhưng không có lưới an toàn |
