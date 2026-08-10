# Hạ tầng — Redis

> `shared-infrastructure/infra/redis` + các adapter trong IAM và Live Room
> Bối cảnh: [bản đồ hệ thống §5.2](00-ban-do-he-thong.md)

---

## 1. Redis giữ cái gì trong hệ thống này

Không phải cache. **Redis ở đây giữ trạng thái không có bản sao ở đâu khác** — và đó là điều làm nó thành một điểm chết đơn.

Bảy nhóm khoá, tất cả đều có TTL:

| Nhóm khoá | Giữ gì | TTL | Mất thì sao |
|---|---|---|---|
| `iam:refresh:token:<sha256>` | Phiên đăng nhập còn sống | 14 ngày | **Mọi người bị đăng xuất** |
| `iam:refresh:user:<userId>` | Tập phiên của một người | 14 ngày | Mất khả năng "đăng xuất mọi thiết bị" |
| `iam:refresh:rotated:<sha256>` | Bia mộ chống trộm token | 14 ngày | Mất khả năng phát hiện token bị chép |
| `iam:jwt:blacklist:<jti>` | Access token đã đăng xuất | = đời còn lại | Token đã đăng xuất dùng lại được tới 15 phút |
| `iam:ratelimit:<scope>:<key>` | Bộ đếm tần suất | 1 phút | Mất giới hạn tần suất |
| `iam:login:fail\|lock:…` | Đếm sai & khoá tài khoản | 15–30 phút | Mất chống dò mật khẩu |
| `iam:cooldown:…` | Cooldown đăng ký / gửi lại OTP / đặt lại mật khẩu | 60 giây | Mất chống spam email |
| `pwb:ratelimit:global:{ip}` | Bộ đếm HTTP toàn cục | 1 phút | Mất trần 500 req/phút |
| *(Live Room)* throttle tra mã phòng | Chống dò mã phòng | 15 phút | Mất chống dò |

Dòng đầu là dòng nặng nhất: **danh sách phiên đăng nhập chỉ tồn tại trong Redis.** Postgres không biết ai đang đăng nhập. `FLUSHALL` là đăng xuất toàn hệ thống.

Chi tiết từng nhóm ở [02 — Lát cắt dọc đăng nhập](02-lat-cat-doc-dang-nhap.md).

---

## 2. Ba kiểu dùng, ba kỹ thuật khác nhau

### 2.1. Bộ đếm — script Lua nguyên tử

```lua
local count = redis.call('INCR', key)
if count == 1 then redis.call('EXPIRE', key, window) end
local ttl = redis.call('TTL', key)
if count > limit then return {0, ttl} else return {1, limit - count} end
```

`INCR` rồi `EXPIRE` bằng hai lệnh có một khe hở: tiến trình chết ở giữa để lại một khoá **không có TTL** — bộ đếm sống vĩnh viễn và tài khoản đó bị chặn mãi mãi. Script Lua chạy nguyên tử nên khe hở không tồn tại.

Script còn trả về TTL luôn, để đưa vào `retryAfterSeconds` mà không phải hỏi thêm lần nữa. Chi tiết ở [02 §4.2](02-lat-cat-doc-dang-nhap.md).

### 2.2. Cooldown — `SETNX` với TTL

```java
Boolean set = redis.opsForValue().setIfAbsent(key, "1", cooldown);
if (Boolean.FALSE.equals(set)) {
    Long ttl = redis.getExpire(key);
    return ttl != null && ttl > 0 ? ttl : cooldown.toSeconds();
}
return 0L;
```

Câu hỏi khác hẳn bộ đếm: không phải "bao nhiêu lần trong một phút" mà là "đã đủ lâu kể từ lần trước chưa". `SETNX` trả lời đúng câu đó bằng một lệnh, và chính TTL của khoá là số giây còn phải chờ.

### 2.3. Tập hợp — cho thao tác "mọi phiên của một người"

`iam:refresh:user:<userId>` là một SET chứa các hash. Nó tồn tại chỉ để phục vụ **một** thao tác: thu hồi mọi phiên. Không có nó thì phải quét toàn bộ keyspace.

Và nó được dùng để gom một lần xoá thay vì N lần:

```java
// One round trip instead of one per token: this runs on the password-change path, where
// an account with many active sessions would otherwise pay N sequential Redis calls.
redis.delete(keysToDrop);
```

---

## 3. Fail-open hay fail-closed — bốn chính sách khác nhau

Đây là phần đáng đọc nhất. Redis chết thì mỗi chỗ phản ứng một kiểu, và mỗi kiểu đều có lý do.

| Chỗ | Redis chết thì | Cấu hình |
|---|---|---|
| Rate limit IAM (đăng nhập, refresh…) | **Từ chối** (`SERVICE_UNAVAILABLE`) | `fail-closed-for-critical-ops: true` |
| Cooldown đặt lại mật khẩu | **Từ chối, luôn luôn** | Cứng trong code |
| Cooldown đăng ký / gửi lại OTP | Theo cấu hình chung | |
| Throttle tra mã phòng (Live Room) | **Cho qua**, chỉ log warn | Cứng trong code |

Lý do từng dòng:

**Rate limit IAM fail-closed** — comment trong `application.yml` nói gọn: *một bộ giới hạn mở toang thì không bảo vệ gì đúng vào lúc hệ thống đã yếu.* Cái giá rất thật: **Redis chết là không ai đăng nhập được.**

**Cooldown đặt lại mật khẩu ghi đè cả cấu hình:**

```java
purpose == CooldownPurpose.PASSWORD_RESET || rateLimitProperties.isFailClosedForCriticalOps()
```

Kể cả khi hệ thống được đặt fail-open, đường này vẫn từ chối. Comment: endpoint này gửi mail tới **một địa chỉ tuỳ ý**, để hở là biến nó thành trạm phát tán thư không có bộ đếm. Đây là chỗ **duy nhất** trong hệ thống ghi đè cấu hình như vậy.

**Throttle mã phòng fail-open** — ngược hẳn. Chặn tra mã phòng khi Redis chết là làm hỏng sản phẩm cho một rủi ro nhỏ hơn nhiều: kẻ dò mã vẫn phải vượt qua bước chủ phòng duyệt.

Nguyên tắc rút ra: **fail-closed khi thứ bị mất là một lớp bảo vệ không có gì thay thế; fail-open khi phía sau vẫn còn lớp khác.**

---

## 4. Bí mật thì băm trước khi vào Redis

Không thứ nhạy cảm nào được lưu dạng đọc được:

```
refresh token  →  SHA-256  →  iam:refresh:token:<hash>
```

Ai đọc được Redis cũng không mạo danh được ai. Cùng nguyên tắc với mật khẩu trong Postgres ([iam-00 §4.3](iam-00-tour.md)).

Hệ quả thực tế: **không tra ngược được từ Redis ra token**. Debug một phiên cụ thể thì phải băm token trong tay rồi mới tìm.

---

## 5. Điều Redis **không** làm ở đây

Đáng nói vì nó ngược với trực giác:

- **Không cache dữ liệu đọc.** Không có `@Cacheable` nào. Danh sách bài hát, thông tin phòng, hồ sơ — tất cả đọc thẳng Postgres.
- **Không giữ session HTTP.** Xác thực là JWT không trạng thái ([02 §5.1](02-lat-cat-doc-dang-nhap.md)).
- **Không làm message broker.** Realtime dùng simple broker trong bộ nhớ ([13 §2](13-realtime-stomp.md)); bất đồng bộ dùng Kafka.
- **Không giữ trạng thái Live Room.** Bộ đếm frame STOMP và sổ session nằm **trong bộ nhớ tiến trình**, không phải Redis — và đó chính là lý do hệ thống chỉ chạy được một instance ([13 §10](13-realtime-stomp.md)).

Dòng cuối là một sự không nhất quán đáng chú ý: Live Room dùng Redis cho throttle mã phòng nhưng không dùng cho hai thứ kia. Chuyển chúng sang Redis là một trong những việc phải làm để chạy nhiều instance.

---

## 6. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Phiên đăng nhập **chỉ** ở Redis | Lưu kèm Postgres | Đọc/ghi nhanh, TTL tự dọn | Redis mất là mọi người đăng xuất |
| Bộ đếm bằng script Lua | `INCR` + `EXPIRE` | Không có khoá vĩnh viễn không TTL | Phải nạp script, khó debug hơn |
| Cooldown bằng `SETNX` | Bộ đếm với trần 1 | Đúng ngữ nghĩa "đã đủ lâu chưa" | Hai kỹ thuật cho hai bài toán giống nhau bề ngoài |
| SET cho phiên của mỗi người | Quét keyspace | Thu hồi toàn bộ trong một lần | Thêm một khoá phải giữ đồng bộ |
| Fail-closed cho rate limit IAM | Fail-open | Không mất lớp bảo vệ lúc yếu nhất | Redis chết là không đăng nhập được |
| Cooldown reset **luôn** fail-closed | Theo cấu hình | Không thành trạm phát tán thư | Không tắt được kể cả khi muốn |
| Throttle mã phòng fail-open | Fail-closed | Phía sau còn bước chủ phòng duyệt | Redis chết là mất lớp chống dò |
| Băm token trước khi lưu | Lưu thô | Rò rỉ Redis không thành mạo danh | Không tra ngược được |
| Không cache dữ liệu đọc | Cache nhiều | Không có bài toán nhất quán cache | Mọi lần đọc đều chạm Postgres |
| Trạng thái STOMP trong bộ nhớ, không Redis | Đưa vào Redis | Đơn giản hơn khi một instance | **Chặn đường chạy nhiều instance** |

---

## 7. Tự kiểm chứng

Đặt sẵn một lối tắt:

```bash
R="docker exec pwb-redis redis-cli -a $(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-) --no-auth-warning"
```

**Xem các nhóm khoá và số lượng:**

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning --scan | sed -E 's/:[a-f0-9]{8,}.*//; s/:[0-9.]+$//' | sort | uniq -c | sort -rn
```

**Xem TTL của một khoá phiên** — phải gần 14 ngày (1209600 giây):

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning --scan --pattern 'iam:refresh:token:*' | head -1
```

**Thấy fail-closed hoạt động** — thí nghiệm rõ nhất của file:

```bash
docker stop pwb-redis
```

Thử đăng nhập: nhận lỗi dịch vụ, **không đăng nhập được** — đúng như thiết kế fail-closed.

Trong khi đó thử `GET /liveroom/rooms/by-code/ABC123`: endpoint vẫn trả lời bình thường, và log backend có dòng `Redis unavailable for room code lookup throttle`. **Cùng một sự cố, hai phản ứng ngược nhau**, đúng bảng ở mục 3.

Bật lại:

```bash
docker start pwb-redis
```

Chú ý sau khi bật lại: mọi phiên đã mất, phải đăng nhập lại — chính là hệ quả ở mục 1.

**Xem chỉ có bản băm được lưu:**

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning --scan --pattern 'iam:refresh:token:*' | head -3
```

Phần sau tiền tố là hex 64 ký tự — SHA-256, không phải token gốc dạng Base64-url.

**Đếm bia mộ so với phiên sống:**

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning eval "return {#redis.call('keys','iam:refresh:token:*'), #redis.call('keys','iam:refresh:rotated:*')}" 0
```

> Đo thật: **152** phiên sống so với **342** bia mộ — cái giá của việc phát hiện token bị chép ([02 §13](02-lat-cat-doc-dang-nhap.md)).

---

## 8. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Redis là điểm chết đơn của việc đăng nhập** | Chết là không ai đăng nhập được; khởi động lại là mọi người bị đăng xuất |
| Không có persistence được cấu hình rõ | `docker-compose` gắn volume `redis_data` nhưng không đặt chính sách AOF/RDB; hành vi phụ thuộc mặc định của image |
| Bia mộ nhiều gấp đôi phiên sống | Mỗi cái sống 14 ngày; cần theo dõi bộ nhớ khi số người dùng tăng |
| Không có chỉ số theo dõi | Chỉ có metric `pwb.ratelimit.iam.decision`; không đo bộ nhớ, tỉ lệ trúng, số khoá |
| Không có Redis cho trạng thái STOMP | Chặn đường chạy nhiều instance — mục 5 |
| Không có xác thực theo user | Một mật khẩu chung cho toàn bộ Redis; mọi thành phần có toàn quyền |
| Tiền tố khoá không nhất quán | `iam:*` cho IAM, `pwb:ratelimit:*` cho filter chung, và Live Room dùng tiền tố riêng |
