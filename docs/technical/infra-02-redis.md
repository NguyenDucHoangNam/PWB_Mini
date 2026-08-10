# Hạ tầng — Redis

> `shared-infrastructure/infra/redis` + các adapter trong IAM và Live Room

---

## 1. Redis là gì

Redis (Remote Dictionary Server) là một **in-memory data store** — hệ thống lưu trữ dữ liệu trên RAM, cho phép đọc/ghi với độ trễ dưới mili-giây. Khác với Postgres lưu trên đĩa và tối ưu cho truy vấn phức tạp, Redis tối ưu cho các thao tác đơn giản nhưng cần tốc độ cực nhanh.

Hai đặc tính quan trọng nhất:

**Key-value store** — mỗi mục dữ liệu là một cặp khoá-giá trị. Khoá là chuỗi ký tự, giá trị có thể là string, set, hash, list, sorted set. Không có bảng, không có quan hệ, không có SQL.

**TTL (Time-To-Live)** — mỗi khoá có thể gán thời hạn sống. Hết thời hạn, Redis tự xoá. Không cần scheduler dọn dẹp, không cần logic hết hạn trong ứng dụng.

---

## 2. Vì sao phải dùng

Có những loại dữ liệu mà Postgres không phù hợp:

- **Bộ đếm tần suất** (rate limit) — cần tăng bộ đếm và kiểm tra giới hạn mỗi request, đòi hỏi tốc độ đọc/ghi rất cao. Đặt trong Postgres nghĩa là mỗi request tạo thêm một lệnh ghi đĩa.
- **Trạng thái ngắn hạn** (phiên đăng nhập, cooldown, khoá tài khoản) — dữ liệu sống vài phút đến vài tuần rồi tự mất. Đặt trong Postgres phải tự viết scheduler xoá, và mỗi lần kiểm tra là một truy vấn.
- **Thao tác nguyên tử** — Redis hỗ trợ script Lua chạy nguyên tử, đảm bảo một chuỗi lệnh (tăng bộ đếm → kiểm tra giới hạn → đặt TTL) không bị xen ngang bởi request khác.

---

## 3. Khi nào sử dụng

Trong PWB, Redis được dùng cho **ba mục đích**, tất cả đều là trạng thái ngắn hạn:

**Quản lý phiên đăng nhập** — danh sách refresh token đang sống, bia mộ token đã xoay (chống replay), blacklist access token đã đăng xuất. Phiên đăng nhập **chỉ tồn tại trong Redis**, Postgres không biết ai đang đăng nhập.

**Giới hạn tần suất và cooldown** — đếm số lần gọi API trong một cửa sổ thời gian, chặn đăng nhập sai nhiều lần, ngăn gửi email spam.

**Chống dò mã phòng** — giới hạn số lần tra mã phòng Live Room từ cùng một IP.

Redis **không** được dùng để cache dữ liệu đọc (không có `@Cacheable` nào), không giữ session HTTP (xác thực dùng JWT stateless), không làm message broker (bất đồng bộ dùng Kafka, realtime dùng STOMP in-memory).

---

## 4. Triển khai trong dự án

### 4.1. Mười nhóm khoá

Tất cả đều có TTL — không có khoá nào sống vĩnh viễn.

| Nhóm khoá | Giữ gì | TTL |
|---|---|---|
| `iam:refresh:token:<sha256>` | Phiên đăng nhập còn sống | 14 ngày |
| `iam:refresh:user:<userId>` | Tập phiên của một người (SET) | 14 ngày |
| `iam:refresh:rotated:<sha256>` | Bia mộ chống trộm token | 14 ngày |
| `iam:jwt:blacklist:<jti>` | Access token đã đăng xuất | = đời còn lại của JWT |
| `iam:ratelimit:<scope>:<key>` | Bộ đếm tần suất IAM | 1 phút |
| `iam:login:fail:email\|ip:<key>` | Đếm sai mật khẩu | 15 phút (email) / 30 phút (IP) |
| `iam:login:lock:email\|ip:<key>` | Khoá tài khoản / IP | 15 phút (email) / 30 phút (IP) |
| `iam:cooldown:register\|resend:<email>` | Cooldown đăng ký / gửi lại OTP | 60 giây |
| `iam:password-reset:cooldown:<email>` | Cooldown đặt lại mật khẩu | 60 giây |
| `pwb:ratelimit:<scope>:<subject>` | Bộ đếm HTTP toàn cục | 1 phút |
| `liveroom:code-lookup:<ip>` | Chống dò mã phòng | 15 phút |

Dòng đầu là dòng nặng nhất: danh sách phiên đăng nhập **chỉ tồn tại trong Redis**. Postgres không biết ai đang đăng nhập. `FLUSHALL` là đăng xuất toàn hệ thống.

### 4.2. Ba kỹ thuật Redis khác nhau cho ba bài toán

**Bộ đếm — script Lua nguyên tử**

Dùng cho rate limit (cả IAM lẫn HTTP toàn cục). Script Lua thực hiện nguyên tử: `INCR` tăng bộ đếm → nếu là lần đầu thì `EXPIRE` đặt TTL → so sánh với giới hạn → trả kết quả kèm TTL còn lại.

Tại sao Lua chứ không phải `INCR` rồi `EXPIRE` riêng? Vì hai lệnh riêng có khe hở: tiến trình chết ở giữa để lại một khoá không có TTL — bộ đếm sống vĩnh viễn và tài khoản đó bị chặn mãi mãi. Script Lua chạy nguyên tử nên khe hở không tồn tại.

Script còn trả về TTL luôn để đưa vào header `Retry-After` mà không phải gọi Redis thêm lần nữa.

**Cooldown — `SETNX` với TTL**

Dùng cho cooldown đăng ký, gửi lại OTP, đặt lại mật khẩu. Câu hỏi khác hẳn bộ đếm: không phải "bao nhiêu lần trong một phút" mà là "đã đủ lâu kể từ lần trước chưa". `SETNX` (set if not exists) trả lời đúng câu đó bằng một lệnh — nếu khoá đã tồn tại thì trả về `false` kèm TTL còn lại (số giây phải chờ). Nếu chưa tồn tại thì tạo khoá với TTL và cho qua.

**Tập hợp (SET) — gom phiên của một người**

`iam:refresh:user:<userId>` là một Redis SET chứa các hash của refresh token. Nó tồn tại chỉ để phục vụ một thao tác: thu hồi mọi phiên khi đổi mật khẩu. Không có nó thì phải quét toàn bộ keyspace để tìm token thuộc về một user. Nhờ SET, thu hồi toàn bộ chỉ cần một round-trip thay vì N lần gọi.

### 4.3. Chống dò mật khẩu — login fail + lock

`RedisLoginAttemptChecker` dùng `INCR` + `EXPIRE` (hai lệnh riêng, không dùng Lua) để đếm số lần đăng nhập sai. Khi vượt ngưỡng (5 lần cho email, 20 lần cho IP), tạo khoá lock với TTL tương ứng (15 phút cho email, 30 phút cho IP). Khi đăng nhập thành công, xoá cả khoá fail lẫn lock.

Lý do không dùng Lua: đếm sai mật khẩu không cần nguyên tử hoàn hảo như rate limit — nếu tiến trình chết ở giữa, khoá fail không có TTL cũng chỉ kẹt đến khi Redis restart, không chặn vĩnh viễn vì khoá lock được tạo riêng với TTL cứng.

### 4.4. Bảo mật — băm trước khi lưu

Refresh token được băm qua SHA-256 trước khi dùng làm key trong Redis. Ai truy cập được Redis cũng không mạo danh được ai — cùng nguyên tắc với mật khẩu được hash bằng BCrypt trong Postgres.

Hệ quả: không tra ngược được từ Redis ra token. Debug một phiên cụ thể thì phải băm token trong tay rồi mới tìm.

### 4.5. Fail-open và fail-closed — năm chính sách khác nhau

Redis chết thì mỗi chỗ phản ứng một kiểu:

| Chỗ | Redis chết thì | Cấu hình |
|---|---|---|
| Rate limit IAM (đăng nhập, refresh…) | **Từ chối** (`SERVICE_UNAVAILABLE`) | `fail-closed-for-critical-ops: true` |
| Cooldown đặt lại mật khẩu | **Từ chối, luôn luôn** | Cứng trong code |
| Cooldown đăng ký / gửi lại OTP | Theo cấu hình chung | |
| Rate limit HTTP toàn cục (filter) | **Cho qua**, log warn | Cứng trong code |
| Throttle tra mã phòng (Live Room) | **Cho qua**, chỉ log warn | Cứng trong code |

**Rate limit IAM fail-closed** — một bộ giới hạn mở toang thì không bảo vệ gì đúng vào lúc hệ thống đã yếu. Cái giá: Redis chết là không ai đăng nhập được.

**Cooldown đặt lại mật khẩu luôn fail-closed** — kể cả khi cấu hình chung đặt fail-open, đường này vẫn từ chối. Lý do: endpoint đặt lại mật khẩu gửi mail tới một địa chỉ tuỳ ý, để hở là biến nó thành trạm phát tán thư không có bộ đếm.

**Rate limit HTTP toàn cục và throttle mã phòng fail-open** — chặn mọi request hoặc chặn tra mã phòng khi Redis chết là làm hỏng sản phẩm cho một rủi ro nhỏ hơn nhiều. Đặc biệt với mã phòng, kẻ dò mã vẫn phải vượt qua bước chủ phòng duyệt.

Nguyên tắc: fail-closed khi thứ bị mất là lớp bảo vệ không có gì thay thế; fail-open khi phía sau vẫn còn lớp khác.

### 4.6. Rate limit HTTP toàn cục

`HttpRateLimitFilter` chặn ở tầng servlet filter, trước khi request tới controller. Dùng Lua script giống hệt IAM rate limit. Trần mặc định **500 request/phút** cho toàn bộ API, đồng thời hỗ trợ cấu hình rule riêng cho từng endpoint (pattern + method + limit + window).

Subject được xác định theo thứ tự ưu tiên: nếu user đã xác thực thì đếm theo account ID (`u:<userId>`), nếu chưa thì đếm theo IP (`ip:<clientIp>`). Cách này tránh trường hợp cả toà nhà sau carrier-grade NAT dùng chung một bộ đếm — account xác thực thì mỗi người có bộ đếm riêng.


