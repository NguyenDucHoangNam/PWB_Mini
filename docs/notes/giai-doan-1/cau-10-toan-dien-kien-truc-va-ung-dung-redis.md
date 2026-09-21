# Câu 10: Toàn Diện Kiến Trúc & 4 Trụ Cột Ứng Dụng Thực Chiến Của Redis Trong Dự Án

### ❓ Câu hỏi:
> *"Trong dự án `PWB_MiNi`, bạn sử dụng Redis cho những mục đích gì? Redis đóng vai trò là Cache hay State Store? Hãy phân tích chi tiết các kỹ thuật nâng cao đã triển khai với Redis (Lua Script nguyên tử cho Rate Limit, Refresh Token Rotation & Reuse Detection bằng `MULTI/EXEC`, Blacklist token tức thì, chống Brute-Force 2 chiều, Cooldowns chống spam OTP, Cache kết quả Google Cloud TTS để tối ưu chi phí, và các chính sách Fail-Open vs Fail-Closed khi Redis gặp sự cố)?"*

---

### 💡 Câu trả lời:

Trong dự án `PWB_MiNi`, Redis (phiên bản **7.2-alpine**) không đơn thuần chỉ được sử dụng làm bộ nhớ đệm (Cache) đọc dữ liệu thông thường, mà đóng vai trò là **Hạ tầng Dữ liệu Phân tán trên RAM (Distributed In-Memory Data Store & State Coordinator)**.

Hệ thống tận dụng tối đa 2 đặc tính vượt trội của Redis:
1. **Tốc độ đọc/ghi siêu thấp (Sub-millisecond latency)** nhờ lưu trữ 100% trên RAM.
2. **Khả năng tự động hủy dữ liệu qua TTL (Time-To-Live)** mà không cần viết các tác vụ dọn rác (Garbage Collection / Scheduled Cron) tốn tài nguyên trên cơ sở dữ liệu quan hệ PostgreSQL.

---

### 1. Định Vị Kiến Trúc: Redis trong PWB_MiNi là State Store, Không Chỉ Là "Cache"

Trong thiết kế hệ thống, có sự khác biệt rõ rệt giữa hai khái niệm:
- **Cache**: Dữ liệu sao chép tạm thời từ Database, nếu mất cache thì ứng dụng vẫn truy vấn được từ Database chính (chỉ bị chậm hơn).
- **State Store (Lưu trữ trạng thái phân tán)**: Nơi nắm giữ trạng thái hoạt động thực tế của hệ thống (phiên đăng nhập, danh sách đen token, cờ khóa brute-force, bộ đếm rate limit).

Toàn bộ phiên đăng nhập (Refresh Token) và trạng thái thu hồi token của `PWB_MiNi` **chỉ tồn tại duy nhất trên Redis**. Cơ sở dữ liệu PostgreSQL hoàn toàn không lưu thông tin phiên đăng nhập của người dùng. Nếu Redis bị `FLUSHALL`, toàn bộ người dùng trên hệ thống sẽ được đăng xuất an toàn ngay lập tức.

---

### 2. Bốn (04) Trụ Cột Ứng Dụng Thực Chiến Của Redis Trong Mã Nguồn

```
                        ┌─────────────────────────────────────────────────────────┐
                        │              REDIS 7.2 (IN-MEMORY STORE)                │
                        └───────────────────────────┬─────────────────────────────┘
                                                    │
         ┌──────────────────────────┬───────────────┴───────────────┬─────────────────────────┐
         ▼                          ▼                               ▼                         ▼
┌──────────────────┐      ┌──────────────────┐            ┌──────────────────┐      ┌──────────────────┐
│   TRỤ CỘT 1      │      │   TRỤ CỘT 2      │            │   TRỤ CỘT 3      │      │   TRỤ CỘT 4      │
│ Quản Lý Phiên &  │      │ Phòng Thủ Tấn    │            │ Giới Hạn Lưu     │      │ Cache Tiết Kiệm  │
│ Bảo Mật Token    │      │ Công & Lạm Dụng  │            │ Lượng Phân Tán   │      │ Chi Phí Cloud    │
│ (IAM Module)     │      │ (Anti-Abuse)     │            │ (Rate Limiting)  │      │ (AI Audio TTS)   │
├──────────────────┤      ├──────────────────┤            ├──────────────────┤      ├──────────────────┤
│ • JWT Blacklist  │      │ • Brute-force    │            │ • Lua Script     │      │ • Google Cloud   │
│ • Refresh Token  │      │   Login Lock     │            │   Atomic Counter │      │   TTS Synthesis  │
│ • Token Rotation │      │ • OTP Cooldown   │            │ • Dual Identity  │      │ • Base64 Audio   │
│   (RTR & Theft)  │      │ • Dò mã LiveRoom │            │   (User vs IP)   │      │ • Giảm 90% cước  │
└──────────────────┘      └──────────────────┘            └──────────────────┘      └──────────────────┘
```

---

#### 🛡️ Trụ Cột 1: Quản Lý Phiên & Bảo Mật Token Toàn Diện (Module `iam`)
Triển khai tập trung tại lớp [`TokenManagerServiceAdapter`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/service/impl/TokenManagerServiceAdapter.java):

##### 1.1. Vô hiệu hóa tức thì Access Token (JWT Blacklist)
- **Vấn đề**: JWT là chuẩn phi trạng thái (Stateless). Khi người dùng bấm "Đăng xuất" hoặc Admin khóa tài khoản, Access Token vẫn còn hiệu lực cho đến khi hết hạn (ví dụ 15 phút), tạo ra rủi ro chiếm đoạt phiên.
- **Giải pháp**:
  - Khi logout, hệ thống trích xuất mã định danh duy nhất `jti` (JWT ID) từ token.
  - Lưu vào Redis: `iam:jwt:blacklist:<jti>` với giá trị `"1"`.
  - **Tối ưu bộ nhớ**: Gán `TTL = Thời gian sống còn lại của Token` (`accessExpiresInSeconds`). Khi token tự hết hạn theo thời gian thì Redis cũng tự xóa key này khỏi RAM, không để lại rác bộ nhớ.
  - Khi request đi qua [`JwtAuthenticationFilter`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/security/JwtAuthenticationFilter.java#L50), hệ thống gọi `redis.hasKey(...)` với độ phức tạp $O(1)$. Nếu phát hiện token nằm trong Blacklist, lập tức từ chối với mã `401 Unauthorized`.

##### 1.2. Quản lý Refresh Token & Đăng xuất đa thiết bị
- **Lưu phiên đơn lẻ**: `iam:refresh:token:<sha256(rawToken)>` $\rightarrow$ lưu `userId` với TTL mặc định là 14 ngày.
  - *Nguyên tắc an toàn*: Chuỗi Refresh Token ngẫu nhiên (SecureRandom 64 ký tự) bắt buộc phải được **băm bằng SHA-256** trước khi lưu làm key trên Redis. Nếu hacker đọc trộm được Redis cũng không thể lấy được token thô để mạo danh người dùng.
- **Quản lý đa thiết bị qua Redis Set**:
  - Key `iam:refresh:user:<userId>` sử dụng cấu trúc dữ liệu **Redis SET** (`sAdd`, `sRem`) để gom tất cả các mã băm Refresh Token của cùng một tài khoản đang đăng nhập trên nhiều thiết bị (Điện thoại, Laptop, Máy tính bảng).
  - Khi người dùng đổi mật khẩu hoặc bấm **"Đăng xuất khỏi tất cả thiết bị"**, hệ thống chỉ cần đọc SET này và xóa sạch các key tương ứng trong một round-trip mạng, thay vì phải quét toàn bộ cơ sở dữ liệu (`KEYS *`).

##### 1.3. Phát hiện Token bị đánh cắp bằng Refresh Token Rotation (RTR & Theft Detection)
- **Kỹ thuật xoay vòng**: Mỗi lần client dùng Refresh Token để lấy cặp token mới, Refresh Token cũ sẽ bị hủy ngay lập tức và cấp một Refresh Token mới tinh.
- **Phát hiện tái sử dụng trái phép (Reuse Detection)**:
  - Khi xoay vòng token cũ sang token mới, hệ thống lưu lại mã băm của token cũ vào key "bia mộ": `iam:refresh:rotated:<sha256(oldToken)>` với TTL ngắn.
  - Toàn bộ thao tác (lưu token mới, xóa token cũ, cập nhật Set, ghi nhận token rotated) được thực thi nguyên tử thông qua khối **Transaction của Redis (`connection.multi()` / `connection.exec()`)**.
  - Nếu kẻ tấn công đánh cắp được token cũ và gửi lên để refresh sau khi chủ sở hữu hợp pháp đã xoay vòng:
    - Redis phát hiện key này nằm trong `iam:refresh:rotated:...`.
    - Hệ thống cảnh báo ngay lập tức **`REFRESH_TOKEN_REUSED`** và kích hoạt cơ chế tự vệ khẩn cấp: **Thu hồi toàn bộ phiên đăng nhập của tài khoản đó (`revokeAllRefreshTokensForUser`)**, buộc hacker và nạn nhân phải đăng nhập lại từ đầu bằng mật khẩu.

---

#### 🛡️ Trụ Cột 2: Phòng Thủ Tấn Công & Chống Lạm Dụng (Anti-Abuse & Brute-Force)

##### 2.1. Chống dò quét mật khẩu 2 chiều (Account & IP) — [`RedisLoginAttemptChecker`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/service/impl/RedisLoginAttemptChecker.java)
- Khi đăng nhập sai mật khẩu, hệ thống tăng bộ đếm thất bại theo cả 2 chiều:
  - Theo Email: `iam:login:fail:email:<email>` (Cửa sổ theo dõi: 15 phút).
  - Theo IP: `iam:login:fail:ip:<clientIp>` (Cửa sổ theo dõi: 30 phút).
- Khi số lần thất bại chạm ngưỡng (5 lần đối với Email, 20 lần đối với IP):
  - Hệ thống tạo cờ khóa: `iam:login:lock:email:...` hoặc `iam:login:lock:ip:...` với giá trị `"1"` và TTL tương ứng.
  - Mọi request đăng nhập tiếp theo từ Email hoặc IP này sẽ bị chặn đứng ngay lập tức từ tầng Service mà không cần chạy thuật toán băm BCrypt tốn kém CPU.
- Khi người dùng đăng nhập thành công: Hệ thống gọi `redis.delete(...)` xóa sạch cả key fail lẫn key lock.

##### 2.2. Chống Spam OTP & Cooldown Bảo Mật — [`ThrottlingServiceAdapter`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/service/impl/ThrottlingServiceAdapter.java)
- Áp dụng nguyên lý `SETNX` (Set if Not Exists) kèm TTL:
  - `iam:cooldown:resend:<email>`: Buộc người dùng phải đợi tối thiểu 60 giây mới được bấm gửi lại mã OTP email (chống nghẽn Mail Server).
  - `iam:cooldown:register:<email>`: Cooldown giữa các lần đăng ký tài khoản.
  - `iam:password-reset:cooldown:<email>`: Cooldown giữa các lần yêu cầu đặt lại mật khẩu (ngăn chặn biến server thành công cụ gửi spam mail).
  - `otp:daily-count:<email>`: Đếm tổng số OTP được gửi trong ngày, vượt quá trần cho phép sẽ từ chối để bảo vệ chi phí dịch vụ gửi mail/SMS.

##### 2.3. Chống Dò Quét Mã Phòng Live Room — [`RoomCodeLookupThrottleAdapter`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/service/RoomCodeLookupThrottleAdapter.java)
- Key: `liveroom:code-lookup:<clientIp>`.
- Giới hạn số lần một IP được phép nhập mã PIN dò tìm phòng Live Room trong cửa sổ thời gian 15 phút.
- Ngăn chặn triệt để kỹ thuật tấn công Brute-force / Enumeration quét mã phòng ngẫu nhiên để đột nhập vào các phòng nghe nhạc riêng tư.

---

#### 🛡️ Trụ Cột 3: Giới Hạn Lưu Lượng Phân Tán Bằng Lua Script (Distributed Rate Limiting)
Triển khai tại [`HttpRateLimitService`](file:///d:/Learning/Project/PWB_MiNi/Backend/shared/shared-web/src/main/java/com/pwb/web/filter/HttpRateLimitService.java) kết hợp với [`HttpRateLimitFilter`](file:///d:/Learning/Project/PWB_MiNi/Backend/shared/shared-web/src/main/java/com/pwb/web/filter/HttpRateLimitFilter.java):

##### 3.1. Kịch bản Redis Lua Script nguyên tử (Atomic Counter)
```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local count = redis.call('INCR', key)
if count == 1 then
    redis.call('EXPIRE', key, window)
end
local ttl = redis.call('TTL', key)
if count > limit then
    return {0, ttl, 0}         -- Bị từ chối: trả về retryAfterSeconds
else
    return {1, limit - count, ttl} -- Được chấp thuận: trả về remaining quota
end
```

##### 3.2. Tại sao BẮT BUỘC dùng Lua Script thay vì lệnh Java riêng lẻ?
1. **Triệt tiêu Race Condition**: Toàn bộ chuỗi lệnh `INCR` $\rightarrow$ `EXPIRE` $\rightarrow$ `TTL` $\rightarrow$ So sánh giới hạn được Redis thực thi như một giao dịch đơn nguyên tử (Single-threaded Execution). Không bao giờ có request nào xen ngang được vào giữa.
2. **Chống rò rỉ bộ nhớ (No Key Leak)**: Nếu dùng mã Java gọi `INCR` rồi mới gọi `EXPIRE`, lỡ server bị crash hoặc mất mạng đúng lúc ở giữa, key trên Redis sẽ **sống vĩnh viễn không có TTL**. Người dùng đó sẽ bị chặn `429` mãi mãi. Lua Script đảm bảo `EXPIRE` luôn luôn chạy cùng `INCR`.
3. **Tiết kiệm vòng mạng (Zero Network Round-Trip)**: Chỉ tốn đúng **01 round-trip mạng** từ Spring Boot sang Redis để vừa đếm, vừa đặt hạn, vừa lấy thời gian `TTL` đưa thẳng vào header `Retry-After`.

##### 3.3. Nhận diện Danh tính Kép (Dual Identity)
- Khóa Redis có định dạng: `pwb:ratelimit:<scope>:<subject>`.
- `subject` được phân định thông minh:
  - **Đã đăng nhập**: Dùng `u:<userId>` $\rightarrow$ Khóa hạn ngạch theo tài khoản cá nhân, giúp các nhân viên trong cùng văn phòng/trường học sau mạng NAT không bị "vạ lây" khóa oan.
  - **Khách vãng lai**: Fallback về `ip:<clientIp>` $\rightarrow$ Bảo vệ các trang công khai (Login, Register).

---

#### 🛡️ Trụ Cột 4: Tiết Kiệm Chi Phí Cloud & Tăng Tốc Độ Đọc (Cost-Saving Cache cho AI TTS)
Triển khai tại [`RedisTtsPreviewCache`](file:///d:/Learning/Project/PWB_MiNi/Backend/modules/audio/src/main/java/com/pwb/audio/infrastructure/tts/RedisTtsPreviewCache.java):

- **Bài toán nghiệp vụ**: Tính năng tạo Voice Tag cho phép người dùng nghe thử âm thanh trước khi chốt đơn. Mỗi lần nghe thử phải gọi sang **Google Cloud Text-To-Speech API**. Nếu gọi trực tiếp:
  - Chi phí hóa đơn Google Cloud tăng phi mã theo từng ký tự.
  - Độ trễ mạng cao (mất từ 1 đến 3 giây để Google xử lý và trả file âm thanh).
- **Giải pháp Cache trên Redis**:
  - Key được băm SHA-256 từ nội dung câu nói + ngôn ngữ + tên giọng đọc:
    `audio:tts:preview:<sha256(phrase + \0 + lang + \0 + voice)>`.
  - Kết quả âm thanh tổng hợp được chuyển thành chuỗi Base64 và lưu thẳng vào Redis với TTL là **24 giờ**.
- **Hiệu quả thực chiến**:
  - Khi người dùng (hoặc nhiều người dùng khác nhau) cùng nghe thử các câu khẩu hiệu phổ biến (ví dụ: *"Producer Workbench"*, *"DJ Drop"*...), kết quả được trả về ngay tức khắc từ RAM trong **vài mili-giây**.
  - **Giảm tải từ 80% đến 90% số lượng request gọi sang Google Cloud**, trực tiếp bảo vệ ngân sách tài chính của dự án.

---

### 3. Bảng Tổng Hợp 11 Nhóm Khóa Redis Trong Toàn Bộ Hệ Thống

| Nhóm Khóa Redis | Cấu Trúc | TTL Mặc Định | Mục Đích Nghiệp Vụ |
| :--- | :---: | :---: | :--- |
| `iam:refresh:token:<sha256>` | `String` | 14 ngày | Lưu `userId` của phiên đăng nhập đang hoạt động |
| `iam:refresh:user:<userId>` | `Set` | 14 ngày | Gom toàn bộ Refresh Token của 1 user (đăng xuất mọi thiết bị) |
| `iam:refresh:rotated:<sha256>` | `String` | 14 ngày | "Bia mộ" token cũ để phát hiện hành vi trộm cắp token |
| `iam:jwt:blacklist:<jti>` | `String` | = Đời còn lại của JWT | Vô hiệu hóa tức thì Access Token khi người dùng Đăng xuất |
| `iam:login:fail:email:<email>` | `String (INCR)` | 15 phút | Đếm số lần đăng nhập sai theo Email |
| `iam:login:fail:ip:<clientIp>` | `String (INCR)` | 30 phút | Đếm số lần đăng nhập sai theo IP |
| `iam:login:lock:email:<email>` | `String` | 15 phút | Khóa tài khoản tạm thời khi đăng nhập sai quá 5 lần |
| `iam:login:lock:ip:<clientIp>` | `String` | 30 phút | Khóa IP tạm thời khi đăng nhập sai quá 20 lần |
| `iam:cooldown:<purpose>:<email>` | `String (SETNX)` | 60 giây | Thời gian chờ giữa các lần bấm gửi OTP / đặt lại mật khẩu |
| `otp:daily-count:<email>` | `String (INCR)` | Hết ngày (24h) | Giới hạn số lượng mã OTP tối đa được gửi trong 1 ngày |
| `liveroom:code-lookup:<clientIp>` | `String (INCR)` | 15 phút | Chặn dò quét Brute-force mã phòng Live Room |
| `pwb:ratelimit:<scope>:<subject>` | `Lua Script` | 1 phút | Hạn ngạch Rate Limit HTTP API toàn hệ thống (500 req/min) |
| `audio:tts:preview:<sha256>` | `String (Base64)`| 24 giờ | Bộ nhớ đệm âm thanh giọng đọc AI, giảm chi phí Google Cloud |

---

### 4. Thiết Kế Khả Dụng Cao: 5 Chính Sách Fail-Open vs Fail-Closed Khi Redis Gặp Sự Cố

Trong môi trường phân tán, hạ tầng Redis hoàn toàn có thể gặp sự cố (đứt cáp mạng, Redis server khởi động lại hoặc tràn bộ nhớ). Hệ thống `PWB_MiNi` áp dụng quy tắc thiết kế kiến trúc chuẩn mực: **"Tùy biến phản ứng theo mức độ rủi ro nghiệp vụ"**:

```
                                  [ REDIS GẶP SỰ CỐ / MẤT KẾT NỐI ]
                                                  │
                ┌─────────────────────────────────┴─────────────────────────────────┐
                ▼                                                                   ▼
       [ FAIL-CLOSED: TỪ CHỐI ]                                            [ FAIL-OPEN: CHO QUA ]
       (Bảo vệ hệ thống & tài chính)                                       (Bảo vệ trải nghiệm người dùng)
                │                                                                   │
    • Rate Limit nghiệp vụ IAM (Login/Token)                           • Rate Limit HTTP toàn cục (Filter)
    • Cooldown Đặt lại mật khẩu (Tránh spam Mail)                      • Throttle dò mã phòng Live Room
                                                                       • Cache TTS Preview (Gọi thẳng Google)
```

1. **Rate Limit IAM (Đăng nhập, Cấp token) $\rightarrow$ `FAIL-CLOSED`**:
   - Nếu Redis chết, các endpoint nhạy cảm của IAM sẽ **chủ động từ chối request (`503 Service Unavailable`)**.
   - *Lý do*: Mở toang cửa xác thực đúng vào lúc hệ thống phòng ngự đang sập sẽ tạo điều kiện cho hacker tấn công Brute-force đánh sập toàn bộ database.
2. **Cooldown Đặt lại Mật khẩu $\rightarrow$ `FAIL-CLOSED (Bắt buộc)`**:
   - Luôn luôn từ chối nếu không kiểm tra được cooldown trên Redis.
   - *Lý do*: Endpoint này gửi email tới bất kỳ hòm thư nào. Nếu mở toang, hacker có thể lợi dụng hệ thống để làm trạm phát tán thư rác (Email Relay Bombing).
3. **Rate Limit HTTP Toàn Cục (Filter) $\rightarrow$ `FAIL-OPEN`**:
   - Ghi log `WARN` và **cho phép request đi qua bình thường**.
   - *Lý do*: Không thể vì Redis chập chờn mà chặn đứng toàn bộ người dùng đang nghe nhạc hợp lệ trên toàn hệ thống.
4. **Throttle Tra Mã Phòng Live Room $\rightarrow$ `FAIL-OPEN`**:
   - Cho phép người dùng nhập mã vào phòng bình thường.
   - *Lý do*: Phía sau mã phòng vẫn còn lớp bảo vệ Host Approval (Chủ phòng phải duyệt mới được vào nghe nhạc), rủi ro lọt phòng là cực kỳ thấp so với việc làm đứt gãy trải nghiệm người dùng.
5. **Cache Giọng Đọc TTS Preview $\rightarrow$ `FAIL-OPEN (Graceful Degradation)`**:
   - Khi không đọc được cache từ Redis, hệ thống âm thầm gọi trực tiếp sang Google Cloud TTS để lấy âm thanh phát cho người dùng. Người dùng vẫn nghe được nhạc mượt mà, chỉ phát sinh thêm chi phí tạm thời.

---

### 5. Thực Tế Triển Khai (Single Node) vs Khả Năng Scale-out Phân Tán

- **Thực tế Production hiện tại**: Để tối ưu chi phí hạ tầng, toàn bộ hệ thống đang được đóng gói trong một máy chủ VPS (4 vCPU / 7.6GB RAM) bằng Docker Compose, trong đó Redis 7.2 chạy ở cổng nội bộ (không mở port ra ngoài Internet công cộng).
- **Khả năng mở rộng (Scale-out Ready)**: Nhờ sử dụng Redis làm tầng điều phối phân tán độc lập thay vì Local In-Memory Cache của Java, ứng dụng Backend Spring Boot hoàn toàn ở trạng thái **Stateless**. Khi cần mở rộng lên cụm 3 đến 10 máy chủ trên AWS ECS/EKS đằng sau Load Balancer, toàn bộ cơ chế bảo mật (JWT Blacklist, Rate Limiting, RTR) sẽ hoạt động ngay lập tức mà không cần chỉnh sửa bất kỳ dòng code nào.

---

### 💡 Tóm tắt 30 giây để trả lời phỏng vấn:
> *"Trong dự án PWB_MiNi, Redis là một **Distributed In-Memory State Store** giải quyết 4 bài toán trọng yếu:
> 1. **Bảo mật phiên**: Quản lý JWT Blacklist tức thì khi logout, và thực hiện Refresh Token Rotation (RTR) kèm Reuse Detection qua `MULTI/EXEC`.
> 2. **Phòng chống tấn công**: Khóa đăng nhập Brute-force theo cả Email và IP, cùng các bộ đếm Cooldown chống spam OTP.
> 3. **Rate Limiting phân tán**: Dùng **Lua Script nguyên tử** triệt tiêu Race Condition và phân định Dual Identity (`u:<userId>` vs `ip:<clientIp>`) chống lỗi vạ lây sau mạng NAT.
> 4. **Tối ưu chi phí Cloud**: Cache giọng đọc Google Cloud TTS, giảm 90% chi phí API bên thứ ba.
> Mọi tương tác Redis đều được gắn TTL chặt chẽ và cài đặt chính sách **Fail-Open / Fail-Closed** linh hoạt theo mức độ rủi ro nghiệp vụ."*
