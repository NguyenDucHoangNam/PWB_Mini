# IAM — Hồ sơ & ảnh đại diện

> `GET /profile` · `PUT /profile` · `POST /profile` (đổi ảnh)
> Bối cảnh: [IAM — Tour](iam-00-tour.md)

---

## 1. Bài toán

Hồ sơ gần như là CRUD tầm thường — trừ ảnh đại diện. Ảnh là **file nhị phân vài MB nằm trên S3**, và điều đó kéo theo ba câu hỏi mà phần còn lại của hồ sơ không có:

1. Database lưu cái gì — URL, hay khoá lưu trữ?
2. Ảnh cũ đi đâu khi người dùng đổi ảnh mới?
3. Làm sao giữ một transaction database không bị treo trong lúc chờ S3?

Câu hỏi 3 đã sinh ra chỗ **cố tình không đánh `@Transactional`** duy nhất trong cả IAM.

---

## 2. Database lưu khoá, không lưu URL

Cột `avatar_url` lưu **khoá lưu trữ S3** (ví dụ `avatars/<userId>/<uuid>.webp`), không phải một URL tải được. Mỗi lần trả hồ sơ ra ngoài, `AvatarUrlResolver` mới ký một URL tạm thời:

```java
public String resolve(String storedReference, Duration ttl) {
    if (storedReference == null || storedReference.isBlank() || isAbsoluteUrl(storedReference)) {
        return storedReference;
    }
    try {
        return storageService.generatePresignedUrl(storedReference, ttl).getUrl().toString();
    } catch (StorageException ex) {
        log.warn("Could not presign avatar, returning no avatar: key={} reason={}", ...);
        return null;
    }
}
```

Ba điều đọc ra được từ hàm ngắn này:

**Giá trị bắt đầu bằng `http://` hoặc `https://` được trả nguyên xi.** Đó là ảnh lấy từ Google — Google trả về một URL công khai đầy đủ, không phải đối tượng trong bucket của mình. Cùng một cột chứa hai loại giá trị, và hàm này là chỗ phân biệt chúng.

**Ký thất bại thì trả `null`, không ném lỗi.** Kết hợp với `default-property-inclusion: non_null`, trường `avatarUrl` **biến mất khỏi JSON** và client rơi về hiển thị chữ cái đầu tên. S3 trục trặc làm mất ảnh, không làm hỏng cả trang hồ sơ.

**TTL truyền vào được.** Mặc định 15 phút (`avatar.url-ttl: 15m`), có comment giải thích: *URL được ký ở mỗi lần đọc hồ sơ, nên chỉ cần sống lâu hơn một lượt xem trang.* Nhưng Live Room gọi cùng hàm này với **12 giờ**, vì một phòng nhận URL đúng một lần lúc người dùng vào và màn hình đó sống lâu hơn 15 phút nhiều. URL hết hạn ở đó sẽ hỏng lặng lẽ thành ảnh vỡ.

---

## 3. Upload ảnh — chỗ cố tình không có transaction

```java
/**
 * Deliberately not annotated {@code @Transactional}: the S3 round trip in the middle can take
 * seconds, and holding a pooled database connection across it is what exhausts the pool under
 * concurrent uploads. Each repository call below opens its own short transaction instead.
 */
@Override
public String execute(UpdateAvatarCommand command) {
    User user = userRepository.findById(command.userId())...;   // transaction ngắn 1
    validate(command.file());
    String previousKey = user.getAvatarUrl();
    String key = buildKey(command.userId(), command.file().contentType());
    upload(key, command.file(), command.userId());               // vài giây, không giữ connection
    user.changeAvatarUrl(key);
    userRepository.save(user);                                   // transaction ngắn 2
    deleteQuietly(previousKey);
    return avatarUrlResolver.resolve(key);
}
```

Đây là **chỗ duy nhất trong IAM cố ý bỏ `@Transactional`**, và lý do rất cụ thể: pool connection là tài nguyên hữu hạn. Mười người upload cùng lúc, mỗi người giữ một connection trong 3 giây chờ S3, là mười connection nằm không. Chia thành hai transaction ngắn thì connection được trả về pool trong lúc chờ mạng.

Việc này chạy được vì `UserRepositoryImpl` đánh `@Transactional` **ở mức class** — mỗi lời gọi repository tự mở transaction riêng.

### 3.1. Cái mất: không còn tính nguyên tử

Bỏ transaction là bỏ luôn đảm bảo "hoặc tất cả hoặc không gì". Ba trạng thái dở dang có thể xảy ra:

| Hỏng ở đâu | Hậu quả |
|---|---|
| Upload xong, `save` hỏng | Đối tượng mồ côi trên S3; hồ sơ vẫn ảnh cũ |
| `save` xong, xoá ảnh cũ hỏng | Đối tượng mồ côi trên S3; hồ sơ đúng |
| Upload hỏng | `BusinessException(SERVICE_UNAVAILABLE)`, không đổi gì |

Cả ba đều **chỉ rò rỉ dung lượng lưu trữ**, không làm sai dữ liệu. Đó là lý do đánh đổi này chấp nhận được — và cũng là lý do không nên bắt chước nó ở luồng mà trạng thái dở dang gây hại thật.

Hiện **không có công việc dọn đối tượng mồ côi**.

### 3.2. Xoá ảnh cũ thì im lặng

```java
/**
 * The new avatar is already live at this point, so failing to remove the previous object is
 * a storage-cleanup concern, not a reason to fail the user's request.
 */
private void deleteQuietly(String previousKey) {
    if (previousKey == null || previousKey.isBlank()
            || previousKey.startsWith("http://") || previousKey.startsWith("https://")) {
        return;
    }
    ...
}
```

Điều kiện `startsWith("http")` là thứ chặn một lỗi thật: nếu ảnh cũ là **URL Google**, đó không phải đối tượng trong bucket của mình. Gọi `storageService.delete("https://lh3.googleusercontent.com/…")` là vô nghĩa và sẽ ném lỗi. Đây là lần thứ hai trong cùng luồng phải phân biệt "khoá của mình" với "URL của người ta" — logic ấy nằm rải ở hai chỗ (`AvatarUrlResolver.isAbsoluteUrl` và hàm này) thay vì gom một mối.

### 3.3. Truyền luồng, không đọc vào mảng byte

```java
try (InputStream content = file.openStream()) {
    // Streamed rather than read into a byte[]: a 5 MB heap allocation per concurrent
    // upload is avoidable, and the storage API accepts a stream directly.
    storageService.upload(key, content, file.size(), file.contentType());
}
```

Giới hạn 5 MB (`avatar.max-size-bytes: 5242880`). Hai mươi người upload cùng lúc mà đọc hết vào bộ nhớ là 100 MB heap chỉ để chờ mạng.

### 3.4. Kiểm định dạng

`avatar.allowed-content-types: image/jpeg, image/png, image/webp` — kiểm **trước** khi upload, và đối chiếu `contentType` do client khai (chuyển về chữ thường). Không đọc magic bytes, nên một file được đặt content-type giả vẫn lọt lên bucket. Rủi ro thấp vì ảnh chỉ được phục vụ qua URL ký sẵn và không bao giờ được thực thi, nhưng đây vẫn là kiểm tra dựa trên lời khai của client.

---

## 4. Xem và sửa hồ sơ

`GET /profile` và `PUT /profile` không có gì đặc biệt — trừ việc **cả hai đều đi qua `avatarUrlResolver.resolve(ProfileView)`**:

```java
user.changeFullName(command.fullName());
User saved = userRepository.save(user);
return avatarUrlResolver.resolve(ProfileView.from(saved));
```

Cùng khuôn với `AuthController.toAuthView` ([02 §3](02-lat-cat-doc-dang-nhap.md)): mọi đường trả hồ sơ ra ngoài đều phải đi qua đúng một chỗ giải khoá thành URL. Bỏ sót một chỗ là chỗ đó trả về khoá lưu trữ thô cho client.

`PUT /profile` chỉ đổi được **`fullName`**. Email không đổi được — nó là khoá đăng nhập, và đổi email đòi hỏi cả một luồng xác minh riêng. Vai trò cũng không đổi được, đó là việc của admin ([iam-05](iam-05-quan-tri-nguoi-dung.md)).

---

## 5. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Lưu khoá S3, ký URL lúc đọc | Lưu URL công khai | Bucket không cần mở công khai; đổi bucket không phải sửa dữ liệu | Ký một lần cho mỗi lần đọc hồ sơ |
| TTL 15 phút | Dài hơn cho đỡ ký lại | Link rò ra ngoài chết nhanh | Nơi giữ URL lâu (Live Room) phải tự xin TTL dài hơn |
| Ký hỏng → `null`, không ném | Ném lỗi | S3 trục trặc không làm hỏng trang hồ sơ | Ảnh mất im lặng, không có tín hiệu gì cho client |
| **Không** `@Transactional` khi upload | Bọc cả hàm trong transaction | Không giữ connection suốt vòng gọi S3 | Mất tính nguyên tử; sinh đối tượng mồ côi |
| Xoá ảnh cũ im lặng | Ném lỗi khi xoá hỏng | Ảnh mới đã sống rồi, không có gì để undo | Rác tích tụ trên S3 |
| Truyền luồng thay vì `byte[]` | Đọc hết vào bộ nhớ | Không tốn 5 MB heap mỗi upload đồng thời | Không tính được checksum trước khi gửi |
| Cột `avatar_url` chứa hai loại giá trị | Hai cột riêng | Một cột, một khái niệm "ảnh ở đâu" | Phải kiểm `startsWith("http")` ở hai chỗ |
| Chỉ đổi được `fullName` | Cho đổi cả email | Email là khoá đăng nhập | Muốn đổi email phải nhờ admin hoặc tạo tài khoản mới |

---

## 6. Tự kiểm chứng

**Xem hồ sơ và để ý `avatarUrl`:**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"user1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4); curl -s http://localhost:8080/api/v1/profile -H "Authorization: Bearer $TOKEN"
```

User seed chưa có ảnh nên trường `avatarUrl` **vắng mặt hoàn toàn** khỏi JSON — đúng hành vi `non_null` mô tả ở mục 2.

**Xem database lưu khoá chứ không lưu URL:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT email, avatar_url FROM iam_users WHERE avatar_url IS NOT NULL LIMIT 5;"
```

Giá trị bắt đầu bằng `avatars/` là khoá S3; bắt đầu bằng `https://` là ảnh Google.

**Xem URL ký sẵn hết hạn** — lấy `avatarUrl` từ response hồ sơ, mở được ngay; chờ quá 15 phút rồi mở lại, S3 trả lỗi hết hạn.

**Đổi tên và xem nó không bị Google ghi đè** ([iam-03 §7](iam-03-google-oauth.md)):

```bash
curl -s -X PUT http://localhost:8080/api/v1/profile -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"fullName":"Ten Moi"}'
```

---

## 7. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Không có công việc dọn đối tượng mồ côi | Mọi upload hỏng giữa chừng để lại rác vĩnh viễn trên S3 — mục 3.1 |
| Kiểm định dạng dựa trên lời khai của client | Không đọc magic bytes — mục 3.4 |
| Ảnh mất không có tín hiệu | Ký hỏng trả `null`, client không phân biệt được "chưa có ảnh" với "S3 hỏng" |
| Logic "khoá của mình vs URL ngoài" nằm hai chỗ | `AvatarUrlResolver` và `deleteQuietly` — mục 3.2 |
| Không đổi được email | Mục 4 |
| TTL phải tự nhớ ở nơi gọi | Live Room dùng 12 giờ, và nếu ai đó quên thì ảnh trong phòng hỏng lặng lẽ sau 15 phút |
| Không giới hạn kích thước ảnh (chiều rộng/cao) | Chỉ giới hạn dung lượng 5 MB; một ảnh 8000×8000 vẫn được chấp nhận và trả nguyên kích thước về client |
