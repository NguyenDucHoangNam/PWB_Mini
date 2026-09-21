# Giai đoạn 5: An Toàn, Tối Ưu & Triển Khai (Ops & Security)

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 4: Module Live Room](04-giai-doan-4-module-liveroom.md) | [Giai đoạn 6: Tài Liệu Đối Chiếu ➡️](06-giai-doan-6-tai-lieu-doi-chieu.md)

---

- `HttpRateLimitService.java` (`com.pwb.web.filter`): Thuật toán Token Bucket trên Redis bảo vệ các API nhạy cảm.
- `StompRateLimitInterceptor.java` (`com.pwb.liveroom.infrastructure.realtime`): Giới hạn tần suất frame STOMP chống spam WebSocket.
- `docker-compose.yml` (`Root`): Khởi chạy cụm Postgres, Redis, Kafka, Backend, Frontend.
- `nginx.conf` (`nginx/`): Reverse proxy điều phối traffic HTTP và WebSocket upgrade.

---

#### 1. Kiến Trúc Rate Limiting Đa Tầng & Thuật Toán Token Bucket Trên Redis (`HttpRateLimitService` & `HttpRateLimitFilter`)

- **Các file mã nguồn cốt lõi**:
  - `HttpRateLimitService.java` (`com.pwb.web.filter`): Dịch vụ tính toán hạn ngạch phân tán sử dụng Redis Lua Script nguyên tử và cơ chế xử lý lỗi Fail-Open.
  - `HttpRateLimitFilter.java` (`com.pwb.web.filter`): Bộ lọc chặn bắt HTTP request, xác định đối tượng gọi (User vs IP), phân chia Scope hạn ngạch và tiêm HTTP Headers chuẩn hóa.
  - `ClientIpResolver.java` (`com.pwb.web.security`): Giải mã địa chỉ IP thực của client, bảo vệ hệ thống khỏi kỹ xảo giả mạo IP qua header `X-Forwarded-For`.
  - `SecurityConfig.java` (`com.pwb.iam.infrastructure.config`): Định vị chính xác thứ tự của Rate Limiter bên trong Spring Security Filter Chain.

---

##### Luận điểm 1: Lý thuyết nền tảng: Rate Limiting là gì? Tại sao hệ thống bắt buộc phải có và bản chất các thuật toán kinh điển

- **Bản chất vấn đề: Tại sao không thể để người dùng gửi request tùy thích?**:
  - Máy chủ web (Server), cơ sở dữ liệu (Database) và bộ nhớ (RAM) đều là các tài nguyên hữu hạn. Một CPU chỉ xử lý được một số lượng phép tính nhất định mỗi giây, và Connection Pool của cơ sở dữ liệu thường chỉ chứa 20 đến 50 kết nối đồng thời.
  - Nếu không có cơ chế kiểm soát tốc độ (Rate Limiting), hệ thống sẽ đối mặt với 3 mối đe dọa sống còn:
    1. **Tấn công từ chối dịch vụ (DDoS / Resource Exhaustion)**: Một kẻ tấn công hoặc một script tự động gửi hàng ngàn request mỗi giây sẽ chiếm toàn bộ thread của Tomcat và cạn kiệt Connection Pool của PostgreSQL, khiến toàn bộ người dùng chân chính khác bị treo trang hoặc nhận lỗi 504 Gateway Timeout.
    2. **Tấn công dò quét (Brute-Force Attack)**: Hacker dùng từ điển chạy bot thử hàng triệu mật khẩu hoặc mã OTP trong thời gian ngắn.
    3. **Nguy cơ "cháy túi tiền" vì API bên thứ ba (Financial Drain)**: Trong PWB_MiNi, tính năng thử giọng đọc Voice Tag gọi trực tiếp sang **Google Cloud Text-to-Speech (TTS)**. Mỗi ký tự chuyển đổi đều bị Google tính tiền USD thật. Nếu một user bấm liên tục nút "Nghe thử", hóa đơn đám mây cuối tháng sẽ tăng phi mã.
- **Bản chất các thuật toán Rate Limiting kinh điển trong ngành**:
  1. **Fixed Window Counter (Bộ đếm cửa sổ cố định)**:
     - *Nguyên lý*: Chia thời gian thành các khối cố định (ví dụ: 00:00 - 01:00, 01:00 - 02:00). Mỗi phút cho phép tối đa 100 request. Sang phút mới, bộ đếm tự động reset về 0.
     - *Nhược điểm chết người (Boundary Bursting)*: Nếu người dùng gửi 100 request vào giây `00:59` và gửi tiếp 100 request vào giây `01:01`. Tính theo từng cửa sổ thì cả 2 đều hợp lệ (< 100 req/phút), nhưng trên thực tế, máy chủ đã phải hứng chịu **200 request chỉ trong vòng 2 giây**!
  2. **Sliding Window Log (Nhật ký cửa sổ trượt)**:
     - *Nguyên lý*: Ghi lại chính xác timestamp (dấu thời gian tính bằng mili-giây) của mọi request vào một danh sách (ví dụ Redis ZSET). Khi request mới đến, xóa toàn bộ timestamp cũ hơn `now - 60s`, sau đó đếm số lượng phần tử còn lại.
     - *Ưu / Nhược điểm*: Cực kỳ chính xác, triệt tiêu 100% lỗi bão ở biên giới hạn. Nhưng nhược điểm là **tiêu tốn bộ nhớ RAM khủng khiếp** (lưu hàng triệu timestamp nếu lưu lượng lớn).
  3. **Token Bucket (Thùng thẻ bài - Thuật toán được chọn)**:
     - *Nguyên lý*: Hãy tưởng tượng một chiếc thùng có sức chứa tối đa $B$ chiếc thẻ (Token). Mỗi giây, hệ thống tự động rót thêm $R$ thẻ vào thùng. Khi thùng đầy thì thẻ bị tràn ra ngoài (không tăng thêm). Mỗi khi có một request gửi đến, nó phải "lấy đi 1 chiếc thẻ" từ thùng. Nếu trong thùng còn thẻ -> Request được đi tiếp. Nếu thùng rỗng -> Request bị từ chối ngay lập tức (HTTP 429).
     - *Điểm mạnh vượt trội*: Thuật toán này cho phép hệ thống chấp nhận các đợt tăng lưu lượng đột biến trong ngắn hạn (Bursting Traffic - ví dụ người dùng bấm nhanh 3-4 thao tác liên tiếp) miễn là trong thùng còn thẻ, nhưng vẫn giữ vững tốc độ trung bình dài hạn để bảo vệ server.
- **Tại sao phải dùng Redis phân tán (Distributed Rate Limiting)?**:
  - Nếu lưu bộ đếm trong bộ nhớ RAM của Java (In-Memory như Guava RateLimiter hay ConcurrentHashMap), khi hệ thống mở rộng quy mô lên 3 hoặc 5 server backend chạy song song sau Load Balancer, mỗi server chỉ nhìn thấy một phần request. Kẻ tấn công có thể rải đều request qua các server để qua mặt hạn ngạch.
  - Do đó, **Redis** đóng vai trò là "Bộ não tập trung" duy nhất lưu trữ trạng thái bộ đếm chung cho toàn bộ cụm máy chủ với tốc độ truy xuất In-Memory dưới 1 mili-giây.

---

##### Luận điểm 2: Vị trí "tử huyệt" của Filter trong Spring Security Filter Chain & Mẹo vô hiệu hóa Servlet Registration

- **Vòng đời vật lý của một HTTP Request trong ứng dụng Spring Boot**:
  - Khi một gói tin HTTP từ trình duyệt gửi tới Tomcat:
    $$\text{Tomcat Container} \longrightarrow \text{Servlet Filter Chain} \longrightarrow \text{DispatcherServlet} \longrightarrow \text{Controller}$$
  - Bên trong Servlet Filter Chain, Spring Security cắm vào một chuỗi con chuyên trách gọi là `SecurityFilterChain`. Thứ tự sắp xếp các filter bên trong chuỗi này quyết định tính sống còn của bảo mật.
- **Tại sao `HttpRateLimitFilter` bắt buộc phải nằm ở vị trí "bánh kẹp" này?**:
  - Trong `SecurityConfig.java`, vị trí được chốt cứng:
    ```
    JwtAuthenticationFilter (Xác thực ai đang gọi)
            ↓
    HttpRateLimitFilter (Đếm xem người đó đã gọi bao nhiêu lần)
            ↓
    AuthorizationFilter (Kiểm tra người đó có quyền Admin/User không)
    ```
  - **Lý do 1: Bắt buộc phải nằm SAU `JwtAuthenticationFilter`**:
    - Filter xác thực JWT phải chạy trước để đọc header `Authorization: Bearer <token>`, giải mã chữ ký cryptographic và nạp đối tượng người dùng vào `SecurityContextHolder`.
    - Khi `HttpRateLimitFilter` chạy ngay sau đó, nó có thể đọc được ID của tài khoản đang đăng nhập để trừ hạn ngạch riêng của người đó. Nếu đặt trước JWT, `SecurityContext` lúc này hoàn toàn rỗng, filter sẽ tưởng nhầm tất cả mọi người đều là khách ẩn danh và gom chung vào bộ đếm IP!
  - **Lý do 2: Bắt buộc phải nằm TRƯỚC `AuthorizationFilter`**:
    - Nếu một kẻ tấn công hoặc bot gửi bão request vào các endpoint công khai hoặc gửi request mang token rác, nếu đặt Rate Limiter sau `AuthorizationFilter`, request sẽ bị tầng phân quyền xử lý và trả về 401/403 trước khi kịp đếm. Hậu quả là kẻ tấn công có thể spam hàng triệu request ẩn danh mà không bao giờ bị Rate Limiter chặn lại.
- **Cạm bẫy kỹ thuật kinh điển: Huỷ đăng ký Servlet Filter tự động (`registration.setEnabled(false)`)**:
  - Trong Spring Boot, bất kỳ class nào được đánh dấu là `@Component` hoặc `@Bean` kế thừa từ `Filter` (hoặc `OncePerRequestFilter`), Spring Boot sẽ **tự động phát hiện và đăng ký nó vào gốc Servlet Filter Chain** của Tomcat với đường dẫn `/*`.
  - Nếu bạn vừa cấu hình nó nằm trong `SecurityFilterChain` của Spring Security, vừa để Spring Boot auto-register, filter đó sẽ **bị kích hoạt 2 lần cho mỗi request**:
    1. Lần 1: Chạy ở tầng Servlet ngoài cùng (lúc này chưa hề có thông tin JWT -> filter đếm theo IP).
    2. Lần 2: Chạy trong Security Chain (lúc này có JWT -> filter đếm theo User).
  - Kết quả: Mỗi request bị trừ 2 lần hạn ngạch, và các request có token vẫn bị phạt oan uổng theo IP ở vòng 1!
  - **Giải pháp Under The Hood**: Khai báo cấu hình tường minh:
    ```java
    FilterRegistrationBean<HttpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false); // Ngăn không cho Tomcat tự map vào /*
    ```
    Dòng lệnh này giữ cho Bean vẫn sống trong Spring Context để nạp vào Spring Security Chain, nhưng vô hiệu hóa hoàn toàn việc đăng ký tự động ngoài Tomcat.

---

##### Luận điểm 3: Chiến lược định danh Subject: Tài khoản trước, IP sau (`u:{userId}` vs `ip:{clientIp}`) — Hóa giải thảm họa Carrier-Grade NAT

- **Khái niệm Carrier-Grade NAT (CGNAT) và Mạng tập thể**:
  - Trong thực tế đời sống, địa chỉ IPv4 đang cạn kiệt. Các nhà mạng viễn thông (4G/5G), các tòa nhà văn phòng, trường đại học hay quán cà phê thường gom hàng ngàn thiết bị di động và laptop vào một mạng nội bộ riêng (Private IP), và chỉ dùng chung **một địa chỉ IP công cộng duy nhất (Public IP)** để giao tiếp với Internet thông qua cơ chế NAT (Network Address Translation).
- **Thảm họa "Vạ lây tập thể" (The Innocent Bystander Problem) nếu chỉ đếm theo IP**:
  - Hãy tưởng tượng một văn phòng công ty có 100 nhân viên đang cùng truy cập PWB_MiNi trên cùng một đường truyền mạng.
  - Nếu hệ thống thiết lập: *"Mỗi IP chỉ được phép nghe thử giọng đọc TTS tối đa 20 lần/phút"*.
  - Một nhân viên táy máy bấm liên tục 20 lần nghe thử trong vòng 30 giây -> **Bộ đếm của địa chỉ IP văn phòng đó cạn kiệt**.
  - Ngay lập tức, 99 nhân viên còn lại trong văn phòng khi thao tác sẽ đồng loạt bị máy chủ trả về lỗi `HTTP 429 Too Many Requests` và bị khóa chức năng, dù họ chưa hề bấm một lần nào! Đây là trải nghiệm tồi tệ nhất trong thiết kế hệ thống.
- **Giải pháp phân định danh tính thông minh trong `HttpRateLimitFilter.java`**:
  - Hệ thống áp dụng nguyên tắc: **Ưu tiên tài khoản định danh, IP chỉ là phương án dự phòng cuối cùng**:
    ```
    Kiểm tra SecurityContextHolder:
      ├─ Đã đăng nhập (AuthenticatedUser) ──> Subject = "u:" + userId  (Ví dụ: "u:550e8400-e29b...")
      └─ Khách vãng lai (Anonymous)        ──> Subject = "ip:" + clientIp (Ví dụ: "ip:14.232.208.5")
    ```
  - **Lợi ích kiến trúc**:
    1. Mỗi tài khoản người dùng có một "chiếc thùng hạn mức" riêng biệt. Bạn dùng ở văn phòng, về nhà dùng Wifi hay bật 4G trên đường thì hạn mức vẫn đi theo đúng tài khoản của bạn, không ai bị vạ lây từ người khác.
    2. Chỉ những thao tác công khai chưa thể đăng nhập (như form Đăng ký, Đăng nhập, Quên mật khẩu) mới bắt buộc phải đếm theo IP để chống bot càn quét.
  - **Tách biệt không gian tên (Namespace Isolation)**:
    - Việc gắn thêm tiền tố `u:` (User) và `ip:` (IP) là chi tiết thiết kế cực kỳ tinh tế. Nếu không có tiền tố, một ID tài khoản tình cờ có định dạng chuỗi trông giống địa chỉ IP (hoặc ngược lại) sẽ bị gộp chung bộ đếm trong Redis, gây ra lỗi xung đột ngầm không thể truy vết.

---

##### Luận điểm 4: Thuật toán Fixed Window kết hợp TTL bằng Redis Lua Script & Thiết kế "Fail-Open" sinh tử

- **Tại sao bắt buộc phải dùng Redis Lua Script thay vì code Java gọi tuần tự?**:
  - Nhiều lập trình viên thường viết code Java như sau:
    ```java
    // SAI LẦM KINH ĐIỂN: Gửi 2 lệnh mạng riêng biệt
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) {
        redisTemplate.expire(key, 60, TimeUnit.SECONDS); // Đặt thời gian sống 60 giây
    }
    ```
  - **Cạm bẫy Race Condition & Khóa bất tử (Immortal Key)**:
    - Giả sử đúng vào thời điểm lệnh `increment` vừa thực thi xong trên Redis, kết nối mạng giữa Java và Redis bị đứt, hoặc server Backend bị crash, hoặc ứng dụng bị restart. Lệnh `expire` thứ hai **không bao giờ được gửi tới Redis**.
    - Hậu quả: Khóa Redis đó có giá trị `count = 1` nhưng có `TTL = -1` (tồn tại vĩnh viễn không bao giờ hết hạn). Kể từ giây phút đó, bộ đếm chỉ tăng mà không bao giờ reset về 0, khiến người dùng bị khóa vĩnh viễn khỏi hệ thống!
  - **Giải pháp Under The Hood: Lua Script nguyên tử (Atomic Execution)**:
    - Redis là một tiến trình đơn luồng (Single-Threaded Event Loop). Khi một đoạn mã Lua Script được gửi tới Redis, Redis sẽ thực thi toàn bộ script đó từ đầu đến cuối như **một khối lệnh nguyên tử duy nhất**:
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
          return {0, ttl, 0}          -- Bị từ chối: {allowed=0, retryAfter=ttl, remaining=0}
      else
          return {1, limit - count, ttl} -- Được đi tiếp: {allowed=1, remaining=limit-count, ttl=ttl}
      end
      ```
    - Tuyệt đối không một lệnh nào khác từ client khác có thể xen ngang vào giữa chừng, loại bỏ hoàn toàn 100% rủi ro tạo ra khóa bất tử và giảm độ trễ mạng từ 2-3 chuyến khứ hồi (Round-trips) xuống còn đúng 1 chuyến duy nhất.
- **Thiết kế "Fail-Open" (Mở cửa thoát hiểm khi hỏng) vs "Fail-Close" (Đóng chặt cửa)**:
  - Đây là câu hỏi kinh điển đo lường tư duy Senior/Architect trong phỏng vấn: *"Chuyện gì sẽ xảy ra nếu cụm Redis gặp sự cố, bị đầy RAM hoặc đường truyền mạng tới Redis bị nghẽn?"*.
  - Hai trường phái kiến trúc:
    - **Fail-Close**: Coi an ninh là tuyệt đối. Nếu không kiểm tra được hạn mức từ Redis thì chặn toàn bộ request (trả về lỗi 500 hoặc 429). Kết quả: Redis sập kéo theo toàn bộ trang web chết 100%.
    - **Fail-Open (Lựa chọn của PWB_MiNi)**: Coi tính sẵn sàng (High Availability) của sản phẩm là ưu tiên hàng đầu.
  - **Cơ chế Under The Hood trong `HttpRateLimitService.java`**:
    - Toàn bộ lệnh gọi Redis được bọc trong khối `try-catch Exception`.
    - Khi Redis có sự cố hoặc timeout, hệ thống ghi log cảnh báo (`log.warn`), tăng bộ đếm giám sát Micrometer `pwb.ratelimit.decision { decision: allow, reason: fail-open }`, và lập tức trả về:
      ```java
      return RateLimitResult.allow(limit, window.getSeconds());
      ```
    - Thay vì làm sập trang web của khách hàng, hệ thống chấp nhận tạm thời "mở cửa" cho request đi qua để ứng dụng vẫn hoạt động trơn tru. Rate Limiter là lớp bảo vệ phòng vệ theo chiều sâu (Defense-in-depth), không phải là cổng thẩm quyền, nên không được phép trở thành điểm lỗi đơn lẻ (Single Point of Failure - SPOF) làm tê liệt dịch vụ.

---

##### Luận điểm 5: Phân chia Scope tránh tràn Bucket (Bleeding Buckets) & Chuẩn hóa Header HTTP (IETF + Legacy)

- **Cạm bẫy kiến trúc "Tràn bộ đếm" (Bleeding Buckets)**:
  - Giả sử hệ thống định nghĩa 2 quy tắc:
    1. Trần toàn cục cho duyệt web thông thường: 500 request / phút.
    2. Trần riêng cho API tạo thử giọng đọc Voice Tag TTS (`/api/v1/voice-tags/tts/preview`): 20 request / phút (để bảo vệ ví tiền Google Cloud).
  - Nếu lập trình viên ngây thơ chỉ tạo 1 key Redis cho mỗi user: `pwb:ratelimit:u:{userId}`.
  - Khi người dùng lướt xem danh sách 25 bài hát (mỗi lần chuyển trang tốn 1 request, tổng cộng 25 request), bộ đếm nhảy lên 25. Lúc này người dùng bấm thử nghe giọng đọc TTS, hệ thống so sánh `count (25) > limit (20)` -> **Người dùng bị chặn nghe thử ngay lập tức!** Ngược lại, hạn mức 20 của TTS lại vô tình bị tiêu hao bởi những request duyệt web thông thường. Hiện tượng này gọi là *Bleeding Buckets* (Các thùng hạn mức bị rò rỉ lẫn vào nhau).
- **Giải pháp Scope trong `HttpRateLimitFilter.java`**:
  - Gắn trực tiếp định danh phạm vi (`scope`) vào cấu trúc Key của Redis:
    $$\text{pwb:ratelimit:}\underbrace{\text{scope}}_{\text{global hoặc tts-preview}}\text{:}\underbrace{\text{subject}}_{\text{u:\{id\} hoặc ip:\{ip\}}}$$
  - Khi người dùng gọi các API thông thường, request khớp vào scope `global` (so sánh với trần 500).
  - Khi người dùng gọi API TTS Preview, `AntPathMatcher` khớp vào quy tắc `tts-preview` (so sánh với trần 20).
  - Hai chiếc thùng này nằm ở 2 khóa Redis hoàn toàn độc lập, đảm bảo việc duyệt web tẹt ga không bao giờ làm ảnh hưởng tới hạn mức nghe thử TTS, và ngược lại.
- **Tiêm Header chuẩn hóa quốc tế (IETF RFC) và Chuẩn cũ (Legacy)**:
  - Một API chuyên nghiệp phải thông báo minh bạch tình trạng hạn mức cho client. Trên mọi phản hồi HTTP thành công hoặc thất bại, Filter đều tiêm song song 2 bộ header:
    - *Chuẩn thông dụng (Legacy)*: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
    - *Chuẩn quốc tế IETF*: `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`.
  - Khi request bị từ chối (HTTP 429 Too Many Requests), Filter tiêm thêm header tiêu chuẩn:
    ```http
    HTTP/1.1 429 Too Many Requests
    Retry-After: 42
    Content-Type: application/json
    
    {
      "code": "RATE_LIMITED",
      "message": "Bạn đang thao tác quá nhanh. Vui lòng thử lại sau 42 giây."
    }
    ```
  - Header `Retry-After: 42` giúp ứng dụng Frontend (Next.js/Axios) biết chính xác cần phải đếm ngược bao nhiêu giây trên giao diện để khóa nút bấm (Disable Button), mang lại trải nghiệm người dùng cực kỳ chuyên nghiệp và mượt mà.

---

##### Elevator Pitch (Tóm tắt bỏ túi 1 phút cho phỏng vấn - Phần 1)

> *"Tại hạ tầng của PWB_MiNi, hệ thống Rate Limiting được thiết kế theo mô hình phân tán nhiều tầng với 5 trụ cột kỹ thuật chuẩn mực:
> 
> 1. **Thuật toán Token Bucket nguyên tử bằng Redis Lua Script**: Khắc phục triệt để hiện tượng bão lưu lượng ở biên giới hạn (Boundary Bursting) và ngăn chặn 100% rủi ro tạo ra khóa bất tử (Immortal Key) nếu xảy ra đứt kết nối mạng giữa chừng.
> 2. **Định vị chính xác trong Spring Security Filter Chain**: Nằm sau JWT Filter để lấy principal đếm theo tài khoản, nhưng đứng trước Authorization Filter để chặn đứng các cuộc tấn công quét mã ẩn danh trước khi tiêu tốn tài nguyên phân quyền.
> 3. **Chiến lược Subject hóa giải thảm họa Carrier-Grade NAT**: Ưu tiên đếm theo User ID (`u:{userId}`) và chỉ fallback về IP (`ip:{clientIp}`) cho khách vãng lai, ngăn chặn tình trạng hàng trăm người trong cùng một tòa nhà văn phòng bị phạt vạ lây.
> 4. **Tách biệt Scope chống hiện tượng Bleeding Buckets**: Phân chia ranh giới rõ ràng giữa trần duyệt web toàn cục (500 req/phút) và trần chi phí dịch vụ bên ngoài (TTS Preview 20 req/phút) bằng tiền tố Scope trong Redis Key.
> 5. **Triết lý kiến trúc Fail-Open**: Khi Redis gặp sự cố, hệ thống tự động mở cửa cho request đi qua kèm cảnh báo metric, đảm bảo Rate Limiter không bao giờ trở thành Single Point of Failure làm tê liệt toàn bộ dịch vụ của khách hàng."*

---

#### 2. Giới Hạn Tần Suất Khung STOMP WebSocket Theo Từng Bucket Riêng Biệt (`StompRateLimitInterceptor.java`)

- **Các file mã nguồn cốt lõi**:
  - `StompRateLimitInterceptor.java` (`com.pwb.liveroom.infrastructure.realtime`): Bộ đánh chặn `ChannelInterceptor` kiểm soát tần suất gửi khung tin STOMP ngay trước khi nạp vào Message Broker.
  - `WebSocketConfig.java` (`com.pwb.liveroom.infrastructure.realtime.config`): Đăng ký interceptor vào luồng dữ liệu vào `clientInboundChannel`.
  - `LiveroomConfig.java` (`com.pwb.liveroom.infrastructure.config.properties`): Khai báo cấu hình hạn ngạch cho từng loại bucket nghiệp vụ (RTC, Chat, Default).
  - `LiveroomStompExceptionHandler.java` (`com.pwb.liveroom.api.realtime`): Xử lý và định tuyến thông báo lỗi về hàng đợi cá nhân người dùng.

---

##### Luận điểm 1: Lý thuyết nền tảng: WebSocket Stateful vs HTTP Stateless — Vì sao HTTP Filter hoàn toàn "mù" trước các frame STOMP?

- **Sự khác biệt bản chất giữa HTTP và WebSocket**:
  - **Mô hình HTTP (Stateless Request-Response)**:
    - Mỗi khi client muốn tải dữ liệu, nó mở kết nối TCP (hoặc dùng lại kết nối HTTP Keep-Alive), gửi một HTTP Request (gồm Header và Body), máy chủ xử lý rồi trả về một HTTP Response, sau đó kết thúc chu trình nghiệp vụ.
    - Toàn bộ lưu lượng này đều bắt buộc phải chảy qua cổng kiểm soát của Servlet Container (Tomcat) và chuỗi `SecurityFilterChain`. Vì vậy, bộ lọc `HttpRateLimitFilter` ở Phần 1 có thể dễ dàng "nhìn thấy", đếm từng request và chặn lại bằng mã `HTTP 429`.
  - **Mô hình WebSocket (Stateful Persistent Connection)**:
    - WebSocket chỉ dùng giao thức HTTP đúng **một lần duy nhất** lúc khởi đầu để thực hiện nghi thức bắt tay nâng cấp kết nối: `GET /ws` kèm header `Upgrade: websocket` và `Connection: Upgrade`. Máy chủ Tomcat chấp thuận và phản hồi mã `HTTP 101 Switching Protocols`.
    - **Kể từ thời điểm nhận mã 101 trở đi, giao thức HTTP chính thức chấm dứt**. Đường truyền TCP bên dưới được chuyển sang chế độ song công toàn phần (Full-Duplex Socket) và giữ sống liên tục trong nhiều giờ liền.
    - Dữ liệu trao đổi lúc này không còn là HTTP Request nữa, mà là các **WebSocket Frames** nhị phân siêu nhẹ chạy thẳng qua socket.
- **"Vùng mù" của hệ thống bảo mật nếu chỉ có HTTP Filter**:
  - Vì chuỗi Servlet Filter và Spring Security Filter chỉ làm việc với các HTTP Request, chúng **hoàn toàn không thể nhìn thấy bất kỳ một frame WebSocket nào** chạy qua socket sau khi bắt tay đã thành công.
  - Nếu hệ thống không có lớp bảo vệ thứ hai, một kẻ tấn công chỉ cần đăng nhập tài khoản hợp lệ, mở 1 kết nối WebSocket duy nhất, rồi sau đó dùng script xả hàng triệu frame tin nhắn chat, reaction hoặc bão gói tin rác qua socket.
  - Hậu quả: Bộ nhớ đệm của socket bị tràn, CPU của server bị vắt kiệt để phân phối tin nhắn rác, và toàn bộ phòng Live Room bị "đánh sập" mà bộ lọc `HttpRateLimitFilter` ở tầng HTTP hoàn toàn không hề hay biết!
  - **Kết luận kiến trúc**: Bắt buộc phải xây dựng một cơ chế Rate Limiting chuyên biệt nằm sâu bên trong **tầng ứng dụng tin nhắn (Messaging Layer)** của Spring WebSocket.

---

##### Luận điểm 2: Chốt chặn ở tầng Spring Messaging: ChannelInterceptor (`preSend`) & Phân loại lệnh `StompCommand.SEND`

- **Kiến trúc luồng dữ liệu Spring Messaging**:
  - Trong kiến trúc Spring WebSocket STOMP, khi một frame từ client gửi lên máy chủ qua socket, nó được đóng gói thành một đối tượng `Message<?>` và đẩy vào một đường ống dẫn tin gọi là **`clientInboundChannel`** (Kênh tin nhắn đầu vào từ client).
  - Trước khi tin nhắn này được chuyển giao cho Message Broker (để broadcast ra phòng) hoặc chuyển cho `@MessageMapping` Controller (để xử lý nghiệp vụ), nó bắt buộc phải đi qua các **`ChannelInterceptor`**.
- **Cơ chế đánh chặn Under The Hood của `StompRateLimitInterceptor`**:
  - Interceptor kế thừa giao diện `ChannelInterceptor` và cài đặt phương thức cốt lõi:
    ```java
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) { ... }
    ```
  - Phương thức này hoạt động như một "trạm kiểm soát vé" ngay trước khi gói tin bước vào Broker. Nếu phương thức trả về `message`, gói tin được đi tiếp. Nếu trả về `null`, gói tin sẽ bị **hủy bỏ ngay lập tức (Drop Frame)** và biến mất khỏi hệ thống.
- **Trích xuất thông tin STOMP Frame bằng `MessageHeaderAccessor`**:
  - Không phải frame nào gửi lên cũng là dữ liệu người dùng. Giao thức STOMP có nhiều loại lệnh (Commands): `CONNECT` (kết nối), `SUBSCRIBE` (đăng ký nghe phòng), `UNSUBSCRIBE` (hủy nghe), `HEARTBEAT` (nhịp đập kiểm tra sống còn), và `SEND` (gửi dữ liệu thực tế).
  - Interceptor trích xuất header chuyên biệt:
    ```java
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || !StompCommand.SEND.equals(accessor.getCommand())) {
        return message; // Bỏ qua các lệnh điều khiển, nhịp tim và cho đi qua tự do
    }
    ```
  - **Tối ưu hóa tài nguyên**: Hệ thống chỉ nhắm mục tiêu kiểm soát duy nhất các frame có lệnh `StompCommand.SEND` (nơi người dùng phát sinh dữ liệu tải nặng). Các frame duy trì kết nối (Heartbeat) hoặc đăng ký nhận tin được miễn trừ để đảm bảo kết nối mạng không bị ngắt oan.

---

##### Luận điểm 3: Mô hình Phân Loại Bucket Theo Destination: Cân bằng giữa Bão ICE Candidates và Spam Tin Nhắn Chat

- **Thách thức kiến trúc trong phòng Live Room đa phương tiện**:
  - Trong một phòng Live Room của PWB_MiNi, kết nối WebSocket phục vụ đồng thời 3 nhu cầu hoàn toàn khác nhau:
    1. **Bắt tay WebRTC Audio/Video**: Các thành viên trao đổi tọa độ mạng (ICE Candidates) để kết nối P2P trực tiếp với nhau.
    2. **Trò chuyện văn bản (Chat)**: Gửi tin nhắn chat và bình luận.
    3. **Tương tác phòng khác**: Thả tim (Reactions), đồng bộ danh sách bài hát.
  - **Mâu thuẫn về lưu lượng (Traffic Characteristics Conflict)**:
    - Khi một thành viên mới bước vào phòng 7 người, trình duyệt của người đó phải đồng thời thương lượng kết nối P2P với 6 người còn lại. Trình duyệt sẽ phát ra một **"cơn bão" từ 50 đến 150 gói tin ICE Candidate trong vòng chưa đầy 2-3 giây** để dò tìm đường truyền mạng tối ưu.
    - Nếu ta đặt một hạn mức chung ngây thơ (ví dụ: tối đa 20 frame / 10 giây): Cơn bão ICE Candidate sẽ ngay lập tức vượt ngưỡng, các gói tin tọa độ mạng bị vứt bỏ, dẫn đến việc **cuộc gọi WebRTC bị thất bại hoàn toàn (Signaling Dropped / Call Failure)**!
    - Ngược lại, nếu ta nới lỏng hạn mức lên 200 frame / 10 giây để WebRTC hoạt động: Kẻ phá hoại có thể gửi liên tục 20 tin nhắn chat mỗi giây, làm ngập rác màn hình chat của mọi người và làm treo trình duyệt các máy yếu.
- **Giải pháp Under The Hood: Phân chia 3 Bucket theo Destination URL (`bucketOf`)**:
  - Interceptor kiểm tra chuỗi ký tự trong địa chỉ đích (`destination`) của frame để phân luồng vào 3 chiếc thùng (Buckets) với hạn ngạch hoàn toàn khác nhau:
    ```java
    private Bucket bucketOf(String destination) {
        if (destination.contains("/rtc/")) return Bucket.RTC;
        if (destination.contains("/chat/") || destination.contains("/comments/")) return Bucket.CHAT;
        return Bucket.OTHER;
    }
    ```
  - **Cấu hình hạn ngạch độc lập trong `LiveroomConfig` (chu kỳ 10 giây)**:
    - **`Bucket.RTC`**: Hạn mức cực lớn (mặc định **400 frames / 10s**) -> Dư dả để hấp thụ trọn vẹn cơn bão ICE Candidates khi nhiều người cùng vào phòng một lúc mà không bao giờ bị nghẽn tín hiệu.
    - **`Bucket.CHAT`**: Hạn mức kiểm soát chặt (mặc định **30 frames / 10s**) -> Tối đa 3 tin nhắn/giây, đủ cho người dùng gõ phím nhanh nhất nhưng triệt tiêu hoàn toàn các script bot spam chữ.
    - **`Bucket.OTHER`**: Hạn mức cân bằng (mặc định **60 frames / 10s**) cho các tương tác thả tim và đồng bộ trạng thái.
  - Nhờ tách bucket, việc bạn bật camera/micro trao đổi hàng trăm gói tin WebRTC không bao giờ làm cạn kiệt hạn mức chat chữ của bạn, và ngược lại.

---

##### Luận điểm 4: In-Memory Sliding Window per Session — Tối ưu hiệu năng Micro-second mà không làm nghẽn Redis

- **Tại sao Rate Limit WebSocket KHÔNG dùng Redis tập trung như HTTP?**:
  - Đây là câu hỏi phỏng vấn cực kỳ tinh tế: *"Tại sao ở tầng HTTP sếp dùng Redis, mà sang tầng WebSocket STOMP sếp lại lưu bộ đếm trong bộ nhớ RAM của Java?"*.
  - Câu trả lời nằm ở 2 yếu tố kiến trúc:
    1. **Bản chất Stateful gắn chặt với một Server (Server Pinning)**: Một kết nối WebSocket một khi đã thiết lập thành công thì socket TCP đó sẽ nằm cố định trên đúng một tiến trình máy chủ Backend duy nhất cho đến khi ngắt kết nối. Không bao giờ có chuyện frame số 1 bay vào Server A mà frame số 2 của cùng socket đó lại bay vào Server B. Do đó, việc lưu trạng thái trên chính RAM của server đó là hoàn toàn chính xác và đầy đủ.
    2. **Áp lực về độ trễ (Latency) và Băng thông mạng**: Khung tin STOMP bay về với tốc độ hàng micro-giây. Nếu mỗi frame đều phải gọi qua mạng sang Redis để chạy script Lua:
       - Độ trễ của khung tin nhắn sẽ bị đội lên gấp hàng trăm lần (từ vài micro-giây lên 2-5 mili-giây).
       - Làm Redis Server bị quá tải bởi hàng chục ngàn thao tác đọc ghi mỗi giây vô nghĩa.
- **Cơ chế Under The Hood: Quản lý cửa sổ trượt bằng `ConcurrentHashMap`**:
  - Hệ thống sử dụng một bảng băm an toàn đa luồng trên RAM:
    ```java
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    ```
  - Khóa định danh được kết hợp giữa mã phiên WebSocket và loại thùng: `key = sessionId + "|" + bucket`.
- **Cấu trúc đối tượng `Window` cực nhẹ và cơ chế Thread-Safe**:
  - Mỗi bucket của một session chỉ tốn vỏn vẹn một đối tượng nhỏ gồm 3 trường dữ liệu:
    ```java
    private static final class Window {
        private long startedAt; // Thời điểm bắt đầu cửa sổ (mili-giây)
        private int count;      // Số frame đã gửi trong cửa sổ hiện tại
        private boolean warned; // Đã gửi cảnh báo trong cửa sổ này chưa
        
        synchronized boolean allow(int limit, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - startedAt >= windowMs) { // Nếu đã hết chu kỳ (10s) -> Reset cửa sổ
                startedAt = now;
                count = 0;
                warned = false;
            }
            count += 1;
            return count <= limit; // Còn trong hạn mức -> Cho phép
        }
    }
    ```
  - Từ khóa `synchronized` ở cấp độ phương thức của từng `Window` đảm bảo tính toán chính xác tuyệt đối giữa các luồng xử lý gói tin song song của cùng một phiên người dùng, nhưng hoàn toàn không gây tắc nghẽn (Lock Contention) sang các phiên của người dùng khác. Tốc độ kiểm tra hạn ngạch chỉ mất **vài nano-giây**, giữ trọn vẹn bản chất siêu tốc của thời gian thực.

---

##### Luận điểm 5: Cơ chế Xử Lý Vi Phạm: Drop Frame Không Ngắt Socket, Cơ Chế Claim Warning và Dọn Dẹp Rò Rỉ Bộ Nhớ

- **1. Triết lý "Drop Frame trong im lặng" thay vì ngắt kết nối (Silent Drop vs Disconnect)**:
  - Khi một frame vượt quá hạn mức (`window.allow() == false`), phản xạ thông thường của người mới làm là ngắt luôn kết nối WebSocket (`session.close()`).
  - **Hậu quả của việc ngắt socket**: Phía trình duyệt (Client SDK) được lập trình cơ chế tự động kết nối lại (Auto-Reconnect). Khi bị ngắt, hàng ngàn client bị chặn sẽ đồng loạt gửi lại request bắt tay HTTP Upgrade, tạo ra một cơn **"Bão kết nối lại" (Reconnection Storm)** làm sập luôn máy chủ web.
  - **Giải pháp của PWB_MiNi**: Interceptor chỉ đơn giản trả về `return null;`. Gói tin vi phạm bị vứt bỏ vào thùng rác, máy chủ không chuyển tiếp nó đi đâu cả, nhưng **kết nối socket vật lý vẫn được giữ nguyên vẹn**. Người dùng chỉ bị mất gói tin spam mà không làm đứt gãy phiên làm việc.
- **2. Cơ chế `claimWarning` — Chống bão tin nhắn cảnh báo (Warning Storm Prevention)**:
  - Nếu một bot cố tình xả 1,000 frame spam trong 1 giây, nếu mỗi frame bị chặn máy chủ đều gửi lại một tin nhắn báo lỗi: *"Bạn đang thao tác quá nhanh"*, thì vô hình trung **chính máy chủ lại đang tự spam ngược lại mạng của mình 1,000 tin nhắn lỗi**!
  - **Cơ chế Claim một lần duy nhất**:
    ```java
    if (window.claimWarning()) {
        notifySender(accessor); // Chỉ gửi thông báo lỗi đúng 1 lần duy nhất trong chu kỳ 10s
        log.warn("Throttled STOMP frames: sessionId={} bucket={} destination={}", ...);
    }
    ```
    Biến cờ `warned` bên trong `Window` bảo đảm trong suốt chu kỳ 10 giây của cửa sổ, máy chủ chỉ gửi đúng 1 thông báo lỗi duy nhất về hàng đợi cá nhân của người gửi (`/user/queue/errors` thông qua `convertAndSendToUser`). Toàn bộ các frame spam tiếp theo sau đó sẽ bị drop hoàn toàn trong im lặng mà không tốn một chu kỳ CPU nào để gửi tin phản hồi.
- **3. Cơ chế thu hồi bộ nhớ chống rò rỉ RAM (Memory Leak Prevention)**:
  - Do `ConcurrentHashMap` lưu trữ các đối tượng `Window` trên RAM, nếu người dùng tắt trình duyệt, tắt nguồn máy tính hoặc rớt mạng mà không dọn dẹp bảng băm, hàng triệu đối tượng `Window` cũ sẽ tích tụ theo ngày tháng, dẫn đến tràn bộ nhớ Heap JVM (Out Of Memory).
  - **Giải pháp Under The Hood**: Tận dụng cơ chế lắng nghe sự kiện của Spring Framework:
    ```java
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            windows.keySet().removeIf(key -> key.startsWith(sessionId + "|"));
        }
    }
    ```
    Ngay khi socket TCP đóng lại, Spring kích hoạt `SessionDisconnectEvent`. Interceptor lập tức quét và xóa sạch toàn bộ các key liên quan đến `sessionId` đó khỏi bảng băm. Bộ nhớ RAM được dọn dẹp sạch sẽ 100%, bảo đảm hệ thống vận hành bền bỉ nhiều tháng liên tục mà không hề bị phình to bộ nhớ.

---

##### Elevator Pitch (Tóm tắt bỏ túi 1 phút cho phỏng vấn - Phần 2)

> *"Tại hạ tầng WebSocket Realtime của PWB_MiNi, cơ chế bảo vệ máy chủ khỏi nguy cơ quá tải và spam được thiết kế chuyên biệt qua `StompRateLimitInterceptor` với 5 điểm nhấn kỹ thuật:
> 
> 1. **Bảo vệ vùng mù của HTTP Filter**: WebSocket sau khi bắt tay sẽ chuyển sang socket TCP thuần mà HTTP Filter không thể can thiệp; interceptor cắm trực tiếp vào `clientInboundChannel` của Spring Messaging để kiểm soát từng frame `StompCommand.SEND`.
> 2. **Tách biệt 3 Bucket theo Destination**: Thiết lập hạn mức cực lớn cho `Bucket.RTC` (400 frames/10s) để hấp thụ bão ICE Candidates WebRTC, trong khi siết chặt `Bucket.CHAT` (30 frames/10s) để triệt tiêu bot spam tin nhắn văn bản.
> 3. **Cửa sổ trượt In-Memory siêu tốc**: Lưu trữ trạng thái bộ đếm theo từng Session trên RAM bằng `ConcurrentHashMap`, cho tốc độ phản hồi chỉ vài nano-giây mà không làm nghẽn Redis hay tăng độ trễ mạng.
> 4. **Cơ chế Drop Frame và Claim Warning**: Hủy frame vi phạm trong im lặng mà không ngắt socket (tránh bão Reconnection Storm), đồng thời chỉ gửi duy nhất 1 cảnh báo lỗi về `/user/queue/errors` trong mỗi chu kỳ 10 giây để tránh bão tin nhắn phản hồi.
> 5. **Dọn dẹp rò rỉ bộ nhớ qua Event**: Tự động giải phóng toàn bộ đối tượng bộ đếm khỏi bảng băm ngay khi nhận sự kiện `SessionDisconnectEvent`, loại bỏ 100% rủi ro Memory Leak cho máy chủ."*

---

#### 3. Nginx Reverse Proxy Đỉnh Cao: Điều Phối WebSocket, Fix Bẫy Chuyển Hướng & Bảo Vệ IP Thật (`nginx/nginx.conf`)

- **Các file mã nguồn cốt lõi**:
  - `nginx/nginx.conf`: Tệp cấu hình máy chủ Nginx ở tầng biên (Edge Reverse Proxy), điều phối lưu lượng cho 2 tên miền, xử lý nâng cấp WebSocket và chống giả mạo IP.
  - `nginx/init-letsencrypt.sh`: Kịch bản khởi tạo chứng chỉ SSL/TLS Let's Encrypt tự động lần đầu qua xác thực ACME Challenge.
  - `docker-compose.prod.yml`: Khai báo dịch vụ `nginx` (cổng 80, 443) và `certbot` gắn chung volume lưu trữ chứng chỉ.

---

##### Luận điểm 1: Lý thuyết nền tảng: Reverse Proxy là gì? Kiến trúc 2 Tên Miền & Chấm Dứt SSL/TLS (TLS Termination) Tại Biên

- **Bản chất: Phân biệt Forward Proxy và Reverse Proxy**:
  - **Forward Proxy (Proxy xuôi - Đại diện cho Người dùng)**:
    - Đứng trước một nhóm client (ví dụ: máy tính trong trường học hoặc công ty). Khi nhân viên lướt web, request đi qua Forward Proxy để ra Internet. Máy chủ web bên ngoài chỉ nhìn thấy IP của Forward Proxy mà không biết danh tính thật của nhân viên bên trong. (Mục đích: Ẩn danh client, vượt tường lửa, lọc nội dung).
  - **Reverse Proxy (Proxy ngược - Đại diện cho Máy chủ)**:
    - Đứng ở **cửa ngõ tiếp nhận duy nhất** phía trước một cụm máy chủ nội bộ. Khi hàng triệu người dùng từ khắp nơi trên thế giới truy cập trang web, họ chỉ nói chuyện với duy nhất Reverse Proxy (Nginx). Nginx sẽ tiếp nhận, kiểm tra an ninh, giải mã SSL, rồi âm thầm phân phối request tới đúng container Backend hoặc Frontend bên trong mạng riêng (Private Network).
    - Người dùng bên ngoài hoàn toàn không biết phía sau Nginx có bao nhiêu server, chạy cổng nào hay dùng IP gì.
- **Tại sao PWB_MiNi bắt buộc phải có Nginx đứng đầu?**:
  1. **Chấm dứt SSL/TLS tại Biên (TLS Termination)**:
     - Quá trình mã hóa và giải mã HTTPS bằng các thuật toán mã hóa bất đối xứng (RSA / Elliptic Curve Diffie-Hellman) tiêu tốn rất nhiều chu kỳ CPU.
     - Thay vì bắt Java Spring Boot và Node.js Next.js phải ôm chứng chỉ SSL và tự giải mã từng gói tin, Nginx (được viết bằng C siêu tối ưu) sẽ giải mã HTTPS ngay tại cửa ngõ. Sau đó, Nginx chuyển tiếp request dạng HTTP thuần trong mạng nội bộ Docker (`pwb-network`). Việc này giải phóng 100% gánh nặng CPU mã hóa cho Backend JVM.
  2. **Điều phối 2 tên miền trên cùng 1 địa chỉ IP máy chủ (Virtual Host Routing)**:
     - VPS chỉ có đúng 1 địa chỉ IPv4 công cộng, nhưng hệ thống có 2 tên miền độc lập:
       - `producerworkbench.online` $\longrightarrow$ Nginx định tuyến vào container `frontend:3000` (Next.js App).
       - `api.producerworkbench.online` $\longrightarrow$ Nginx định tuyến vào container `backend:8080` (Spring Boot API & WebSocket).
  3. **Cơ chế cấp chứng chỉ Let's Encrypt tự động không gián đoạn**:
     - Cổng 80 chỉ mở một đường dẫn duy nhất: `/.well-known/acme-challenge/` trỏ vào volume chia sẻ với container `certbot`. Cứ mỗi 60 ngày, Certbot tự động xác thực tên miền với tổ chức Let's Encrypt để gia hạn chứng chỉ SSL mà không bao giờ cần dừng hệ thống hay can thiệp thủ công.
- **Đồng bộ hóa giới hạn Upload File (`client_max_body_size 100m`)**:
  - Trong Nginx, mặc định `client_max_body_size` chỉ là **1MB**. Bất kỳ request nào có file tải lên lớn hơn 1MB sẽ bị Nginx lập tức từ chối bằng mã lỗi `HTTP 413 Request Entity Too Large` dạng trang HTML mặc định của Nginx.
  - Trong PWB_MiNi, tính năng tải lên bài hát lớn đã được tối ưu đẩy trực tiếp lên S3 bằng Presigned URL (không qua server). Tuy nhiên, các tệp âm thanh giọng đọc (Voice Tag) và ảnh đại diện Avatar vẫn tải lên qua API backend.
  - Nginx phải cấu hình `client_max_body_size 100m;` để **khớp tuyệt đối** với thuộc tính `spring.servlet.multipart.max-file-size: 100MB` bên trong Spring Boot. Sự đồng bộ này đảm bảo Nginx không bao giờ chặn nhầm các file hợp lệ, giúp request đi trọn vẹn vào Backend để trả về mã lỗi JSON chuẩn hóa nếu có vi phạm.

---

##### Luận điểm 2: Cơ chế Nâng cấp WebSocket qua Reverse Proxy — Khắc phục Cạm bẫy Phá hỏng HTTP Keep-Alive

- **Tại sao WebSocket không thể đi qua Nginx theo cách Proxy thông thường?**:
  - Nginx theo mặc định hoạt động ở giao thức `HTTP/1.0` khi giao tiếp với máy chủ upstream phía sau.
  - Trong giao thức HTTP, có 2 loại Header:
    - *End-to-end Headers*: Được giữ nguyên và chuyển tiếp qua tất cả các proxy trung gian.
    - *Hop-by-hop Headers*: Chỉ có ý nghĩa giữa 2 thiết bị mạng liền kề và **bị proxy xóa bỏ**, tiêu biểu là: `Upgrade` và `Connection`.
  - Khi trình duyệt gửi yêu cầu bắt tay WebSocket:
    ```http
    GET /ws HTTP/1.1
    Host: api.producerworkbench.online
    Upgrade: websocket
    Connection: Upgrade
    ```
    Nếu ta chỉ dùng lệnh `proxy_pass http://backend:8080;` thông thường, Nginx sẽ xóa sạch 2 header `Upgrade` và `Connection` trước khi gửi tiếp vào Spring Boot. Backend nhận được request nhưng thấy thiếu header nâng cấp nên chỉ coi đây là một request HTTP GET bình thường $\longrightarrow$ **Bắt tay WebSocket thất bại hoàn toàn!**
- **Cạm bẫy phá hỏng HTTP Keep-Alive nếu Hard-Code cấu hình**:
  - Để giải quyết vấn đề trên, nhiều người thường lên mạng copy đoạn cấu hình sau vào Nginx:
    ```nginx
    # CẠM BẪY CHẾT NGƯỜI: Hard-code ép buộc Upgrade
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    ```
  - **Hậu quả thảm khốc**: Nếu bạn đặt cấu hình này ở cấp độ chung (`server` hoặc `location /`), thì **mọi request HTTP thông thường** (như GET danh sách bài hát, POST đăng nhập) gửi lên Nginx cũng sẽ bị Nginx tự ý gắn thêm header `Connection: upgrade` khi chuyển tiếp vào Backend!
  - Backend Spring Boot nhận được một request lấy dữ liệu bình thường nhưng lại thấy header đòi upgrade, dẫn đến việc đóng kết nối ngay sau khi phản hồi và **phá hủy hoàn toàn cơ chế HTTP Keep-Alive** (tái sử dụng kết nối TCP). Hiệu năng hệ thống bị tụt dốc nghiêm trọng vì mỗi request đều phải mở lại kết nối TCP từ đầu.
- **Giải pháp Under The Hood với Nginx Map Directive**:
  - PWB_MiNi áp dụng mẫu thiết kế chuẩn quốc tế bằng cách khai báo một bảng ánh xạ động (`map`) ở khối `http`:
    ```nginx
    map $http_upgrade $connection_upgrade {
        default upgrade;
        ''      close;
    }
    ```
  - **Nguyên lý hoạt động thông minh**:
    - Nếu request từ trình duyệt có mang header `Upgrade` (đang xin nâng cấp WebSocket) $\longrightarrow$ Biến `$connection_upgrade` sẽ nhận giá trị `"upgrade"`.
    - Nếu request từ trình duyệt là HTTP thông thường (biến `$http_upgrade` rỗng `''`) $\longrightarrow$ Biến `$connection_upgrade` sẽ nhận giá trị `"close"`.
  - Tại vị trí `location /ws`, ta khai báo:
    ```nginx
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    ```
  - Nhờ cơ chế ánh xạ có điều kiện này, Nginx chỉ nâng cấp kết nối đúng lúc, đúng chỗ cho các gói tin WebSocket, trong khi bảo vệ an toàn 100% cơ chế Keep-Alive cho toàn bộ các API HTTP còn lại.

---

##### Luận điểm 3: Bẫy "Tử thần" Chuyển Hướng 301 khi thiếu dấu gạch chéo cuối (`/ws` vs `/ws/`)

- **Bản chất của bài toán định tuyến Prefix trong Nginx**:
  - Đây là một trong những "hố tử thần" (Gotchas) kinh điển và khó phát hiện nhất trong lập trình hệ thống Realtime, được đội ngũ kỹ sư ghi chú rất sâu sắc trong file cấu hình.
  - Phía Client Frontend (trong file `liveroom-destinations.ts`), đường dẫn WebSocket được định nghĩa chính xác tuyệt đối là:
    ```typescript
    const LIVEROOM_WS_HTTP_URL = "https://api.producerworkbench.online/ws"; // Không có dấu / ở cuối
    ```
- **Kịch bản sự cố khi cấu hình Nginx có dấu gạch chéo cuối (`location /ws/`)**:
  - Giả sử lập trình viên viết: `location /ws/ { proxy_pass http://backend:8080; }`.
  - Khi trình duyệt gửi request bắt tay tới URL: `/ws` (không có dấu `/` cuối):
    1. Nginx đối chiếu request `/ws` với location `/ws/`.
    2. Theo tài liệu chuẩn của Nginx: Nếu một location tiền tố kết thúc bằng dấu `/` mà nhận một request không có dấu `/`, Nginx sẽ mặc định hiểu rằng đây là một thư mục và tự động gửi phản hồi chuyển hướng HTTP:
       ```http
       HTTP/1.1 301 Moved Permanently
       Location: https://api.producerworkbench.online/ws/
       ```
- **Tại sao chuyển hướng 301 lại "giết chết" WebSocket?**:
  - Đặc tả chuẩn của giao thức WebSocket quốc tế (**RFC 6455**) quy định nghiêm ngặt: Trình duyệt gửi request bắt tay nâng cấp chỉ chấp nhận **duy nhất một phản hồi hợp lệ là `HTTP 101 Switching Protocols`**.
  - Nếu trình duyệt nhận được bất kỳ mã nào khác — bao gồm cả mã chuyển hướng `301` hoặc `302` — **trình duyệt coi như quá trình bắt tay đã thất bại và lập tức đóng kết nối, tuyệt đối không bao giờ tự động gửi request theo URL mới!**
  - **Triệu chứng âm thầm đánh lừa lập trình viên**:
    - Khi kết nối Native WebSocket tới `/ws` bị chết vì lỗi 301, thư viện SockJS ở client sẽ thử lại lần thứ 2, sau đó tự động kích hoạt cơ chế Fallback sang đường dẫn phụ: `/ws/info` và `/ws/<server>/<session>/websocket`.
    - Vì các đường dẫn phụ này tình cờ khớp với tiền tố `/ws/`, nên cuối cùng người dùng vẫn vào được phòng! Tuy nhiên, người dùng sẽ phải chịu đựng cảnh màn hình bị quay vòng tròn (Loading/Reconnecting) mất **từ 3 đến 5 giây cho mỗi lần vào phòng** mà lập trình viên không hề hay biết nguyên nhân từ đâu.
- **Giải pháp chuẩn xác trong `nginx.conf`**:
  - Khai báo chính xác tuyệt đối:
    ```nginx
    location /ws {
        set $backend_ws backend:8080;
        proxy_pass http://$backend_ws$request_uri;
        ...
    }
    ```
  - Bỏ hoàn toàn dấu gạch chéo cuối. Mọi gói tin bắt tay Native WebSocket gửi tới `/ws` được chuyển tiếp ngay lập tức sang Spring Boot, đạt mã `101 Switching Protocols` chỉ trong **vài chục mili-giây**, triệt tiêu hoàn toàn độ trễ 3 giây chờ đợi vô nghĩa.

---

##### Luận điểm 4: Giữ sống phòng nghe nhạc bằng Timeout 3600s & Tắt đệm luồng (`proxy_buffering off`)

- **Bản chất của Idle Timeout trong Nginx**:
  - Nginx được thiết kế để phục vụ hàng chục ngàn kết nối web cùng lúc. Để tránh việc các client bị treo chiếm dụng bộ nhớ worker, Nginx thiết lập 2 thông số mặc định:
    - `proxy_read_timeout: 60s` (Nếu sau 60 giây không có dữ liệu nào từ backend gửi về cho client $\longrightarrow$ Nginx tự động ngắt kết nối).
    - `proxy_send_timeout: 60s` (Nếu sau 60 giây client không gửi gì lên $\longrightarrow$ Nginx ngắt kết nối).
- **Thảm họa đối với phòng Live Room nghe nhạc (Listening Session)**:
  - Hãy tưởng tượng một phòng học tập hoặc phòng nghe nhạc thư giãn có 5 người tham gia. Chủ phòng bật một bản nhạc dài 5 phút. Trong suốt 5 phút đó, mọi người chỉ tập trung nghe nhạc, không ai gõ phím chat và không ai bật mic nói chuyện.
  - Đúng ở giây thứ 60, Nginx thấy đường truyền im ắng không có gói tin nào qua lại $\longrightarrow$ **Nginx âm thầm gửi gói tin TCP RST đóng kết nối WebSocket của toàn bộ mọi người!**
  - Mọi người trong phòng bỗng dưng thấy nhạc bị dừng, video bị ngắt và hiện thông báo mất kết nối.
- **Giải pháp Under The Hood trong `nginx.conf`**:
  - Tăng thời gian chờ lên mức tối đa:
    ```nginx
    proxy_read_timeout 3600s; # Cho phép giữ kết nối nhàn rỗi trong 1 giờ
    proxy_send_timeout 3600s;
    ```
  - Kết hợp với cơ chế **STOMP Heartbeat** (ở tầng ứng dụng định kỳ gửi nhịp đập tim 10 giây/lần mà ta đã phân tích ở Giai đoạn 4), đường truyền socket qua Nginx được đảm bảo thông suốt liên tục trong nhiều giờ mà không bao giờ bị ngắt kết nối oan.
- **Tắt cơ chế đệm luồng (`proxy_buffering off;`)**:
  - Mặc định khi proxy dữ liệu, Nginx sẽ kích hoạt bộ đệm (Buffer): gom các gói tin nhỏ lại thành một khối dữ liệu lớn trong bộ nhớ RAM rồi mới xả ra cho client để tối ưu thông lượng mạng (Throughput).
  - Nhưng đối với hệ thống Realtime (Live Audio, Chat, Tọa độ mạng ICE WebRTC), **độ trễ (Latency) là yếu tố sống còn**. Việc Nginx giữ lại các gói tin nhỏ trong buffer vài chục mili-giây sẽ làm giật tiếng audio và làm chậm quá trình bắt tay video.
  - Cấu hình `proxy_buffering off;` bắt buộc Nginx phải chuyển tiếp gói tin ngay lập tức (Zero-buffering / Pass-through), giữ trọn vẹn đặc tính thời gian thực cho phòng live.

---

##### Luận điểm 5: Chống giả mạo IP (IP Spoofing) Tại Biên & Docker Embedded DNS (`resolver 127.0.0.11`)

- **1. Kỹ thuật chống giả mạo IP bằng cách Ghi đè ở tầng Biên (Edge Overwrite)**:
  - **Lỗ hổng chết người của `$proxy_add_x_forwarded_for`**:
    - Nhiều tài liệu trên mạng hướng dẫn viết: `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`.
    - Biến này có cơ chế: **Nối thêm** IP của client vào danh sách header mà client tự gửi lên.
    - Giả sử một hacker muốn phá hoại tính năng TTS Preview (hạn mức 20 lần/phút). Hacker dùng Postman tự tạo một header giả:
      ```http
      X-Forwarded-For: 1.2.3.4
      ```
    - Khi đi qua Nginx dùng `$proxy_add_x_forwarded_for`, Nginx sẽ chuyển tiếp: `X-Forwarded-For: 1.2.3.4, <IP_Thật_Của_Hacker>`.
    - Bộ lọc `ForwardedHeaderFilter` của Spring Boot mặc định đọc phần tử đầu tiên của chuỗi $\longrightarrow$ Backend tin rằng request đến từ IP `1.2.3.4`! Kẻ tấn công chỉ cần mỗi giây đổi một số IP giả là có thể qua mặt hoàn toàn bộ lọc Rate Limit, tiêu tốn cạn kiệt ngân sách Google Cloud của hệ thống.
  - **Giải pháp phòng thủ tuyệt đối trong PWB_MiNi**:
    - Nginx nằm ở vị trí **Biên ngoài cùng (The Edge)** tiếp xúc trực tiếp với Internet. Biến `$remote_addr` trong Nginx chính là địa chỉ IP vật lý thực tế của kết nối mạng TCP socket mà không một hacker nào có thể làm giả được.
    - Nginx cấu hình ghi đè hoàn toàn:
      ```nginx
      proxy_set_header X-Real-IP       $remote_addr;
      proxy_set_header X-Forwarded-For $remote_addr; # Ghi đè thô bạo, vứt bỏ toàn bộ header từ client
      ```
    - Mọi header `X-Forwarded-For` do client tự bịa ra đều bị Nginx xóa sạch và thay thế bằng chính địa chỉ IP socket thực tế. Nhờ đó, bộ đếm Rate Limiter ở Phần 1 luôn nhận được địa chỉ IP chính xác 100%.
- **2. Khắc phục lỗi 502 Bad Gateway bằng Docker Embedded DNS Resolver**:
  - **Bản chất vấn đề trong mạng Docker**:
    - Trong `docker-compose.prod.yml`, các container giao tiếp với nhau bằng tên service (như `backend`, `frontend`). Khi container khởi động, Docker Daemon sẽ cấp cho container một địa chỉ IP nội bộ ngẫu nhiên (ví dụ: `172.18.0.5`).
    - Nếu Nginx cấu hình `proxy_pass http://backend:8080;` trực tiếp, Nginx chỉ hỏi DNS phân giải chữ `backend` ra IP `172.18.0.5` **đúng một lần duy nhất khi Nginx khởi động**, sau đó lưu cứng địa chỉ IP này vào bộ nhớ RAM mãi mãi.
  - **Sự cố 502 khi Deploy phiên bản mới**:
    - Khi ta cập nhật code và chạy `docker compose up -d --build backend`, Docker sẽ xóa container cũ và dựng container mới. Container mới được cấp IP mới (ví dụ: `172.18.0.9`).
    - Lúc này, Nginx vẫn khư khư gửi toàn bộ request của người dùng vào IP cũ `172.18.0.5` (vốn đã bị tiêu hủy).
    - Hậu quả: **Toàn bộ website bị tê liệt và trả về lỗi `502 Bad Gateway`** cho đến khi có người đăng nhập vào server gõ lệnh khởi động lại Nginx bằng tay!
  - **Giải pháp Under The Hood trong `nginx.conf`**:
    - Khai báo máy chủ DNS nội bộ của Docker kết hợp thời gian sống cache 30 giây:
      ```nginx
      resolver 127.0.0.11 valid=30s ipv6=off;
      ```
      (`127.0.0.11` là địa chỉ IP máy chủ DNS nhúng mặc định của mọi mạng Docker).
    - Đồng thời, đưa địa chỉ upstream vào một biến động:
      ```nginx
      set $backend_upstream backend:8080;
      proxy_pass http://$backend_upstream$request_uri;
      ```
    - **Cơ chế hoạt động**: Khi `proxy_pass` sử dụng một biến (thay vì chuỗi cố định), Nginx bị ép buộc phải hỏi máy chủ DNS `127.0.0.11` cứ mỗi 30 giây một lần để lấy IP mới nhất của container. Nhờ đó, khi hệ thống deploy container mới, Nginx tự động thích ứng chỉ sau vài giây mà không bao giờ bị dính lỗi 502.

---

##### Elevator Pitch (Tóm tắt bỏ túi 1 phút cho phỏng vấn - Phần 3)

> *"Tại hạ tầng biên của PWB_MiNi, Nginx Reverse Proxy được cấu hình chuẩn mực cao cấp giải quyết trọn vẹn 5 thách thức cốt lõi của hệ thống:
> 
> 1. **Kiến trúc Chấm dứt TLS & Tách 2 Tên Miền**: Nginx giải mã HTTPS cho cả web và api bằng chứng chỉ Let's Encrypt tự động gia hạn, giải phóng 100% tài nguyên tính toán mã hóa cho Backend Java; đồng bộ giới hạn tải file 100MB tránh lỗi 413 thô.
> 2. **Cơ chế Nâng cấp WebSocket bằng Map Directive**: Sử dụng `map $http_upgrade $connection_upgrade` để chỉ bật header nâng cấp khi có bắt tay WebSocket thực sự, bảo vệ 100% cơ chế HTTP Keep-Alive cho các API thông thường.
> 3. **Fix triệt để bẫy chuyển hướng 301 (`/ws`)**: Cấu hình chuẩn xác `location /ws` không có gạch chéo cuối để khớp trực tiếp request của client, triệt tiêu lỗi trình duyệt đóng kết nối do nhận mã 301 và loại bỏ hoàn toàn độ trễ 3 giây SockJS fallback.
> 4. **Giữ sống kết nối Realtime 3600s & Tắt đệm luồng**: Tăng timeout lên 1 giờ để duy trì phòng nghe nhạc nhàn rỗi không bị Nginx tự ngắt kết nối, kết hợp `proxy_buffering off` để truyền dữ liệu thời gian thực không độ trễ.
> 5. **Bảo vệ IP thực & Docker DNS Resolver**: Dùng `$remote_addr` ghi đè header `X-Forwarded-For` ở biên để chặn 100% thủ thuật IP Spoofing qua mặt Rate Limit, đồng thời khai báo `resolver 127.0.0.11 valid=30s` để Nginx tự cập nhật IP container khi redeploy, ngăn ngừa triệt để lỗi 502 Bad Gateway."*

---

#### 4. Đóng Gói Docker Compose, Tối Ưu Bộ Nhớ VPS Khắc Nghiệt & Quy Trình Triển Khai CI/CD Không Downtime (`docker-compose.prod.yml`, `deploy.yml`, `ci.yml`)

- **Các file mã nguồn cốt lõi**:
  - `docker-compose.prod.yml` (Root): Bản thiết kế ngăn xếp 8 container dịch vụ production, áp đặt giới hạn bộ nhớ (`mem_limit`) và cô lập mạng nội bộ.
  - `.github/workflows/deploy.yml` (`.github/workflows`): Kịch bản tự động hóa CI/CD triển khai code lên VPS, quản lý tag ảnh và chốt chặn kiểm tra sức khỏe hệ thống (Healthcheck Quality Gate).
  - `.github/workflows/ci.yml` (`.github/workflows`): Kịch bản kiểm thử tự động (Unit test, Lint, Cài đặt FFmpeg thật trên GitHub Runner).
  - `Backend/Dockerfile` & `Frontend/Dockerfile`: Công thức đóng gói ứng dụng thành các Container Image độc lập và tối ưu kích thước.

---

##### Luận điểm 1: Lý thuyết nền tảng: Bài toán Khắc nghiệt trên VPS 7.6GB RAM & Quyết định Gỡ bỏ Elasticsearch (OOM Killer Defense)

- **Bản chất của Container và Docker**:
  - Ngày xưa, để chạy ứng dụng, ta phải cài trực tiếp Java, Node.js, PostgreSQL lên hệ điều hành của máy chủ VPS. Nhược điểm: Xung đột phiên bản phần mềm (Dependency Hell), khó quản lý và cực kỳ khó chuyển giao giữa các môi trường ("Máy em chạy được mà trên server không chạy được").
  - **Docker (Containerization)**: Cho phép đóng gói toàn bộ mã nguồn, thư viện và môi trường chạy vào một chiếc hộp biệt lập gọi là **Container**. Các container chia sẻ chung nhân hệ điều hành (OS Kernel) nhưng hoàn toàn cô lập về bộ nhớ, tiến trình và hệ thống tệp.
  - **Docker Compose**: Là công cụ điều phối giúp định nghĩa và khởi chạy đồng thời nhiều container dịch vụ liên kết với nhau chỉ bằng một tệp cấu hình duy nhất (`docker-compose.yml`).
- **Hiểm họa kinh hoàng mang tên Linux OOM Killer (Out-Of-Memory Killer)**:
  - Máy chủ VPS của dự án là một máy ảo có cấu hình phần cứng cố định: **4 vCPU, 7.6GB RAM và 19GB ổ đĩa SSD**.
  - Trong hệ điều hành Linux, bộ nhớ RAM là tài nguyên tối thượng. Khi các tiến trình ngốn hết sạch RAM vật lý và bộ nhớ ảo Swap, nhân Linux Kernel sẽ tự động kích hoạt một tiến trình cứu sinh khẩn cấp gọi là **`oom-killer`**.
  - Cơ chế của `oom-killer`: Nó sẽ tự động quét danh sách các ứng dụng đang chạy, tìm tiến trình nào đang chiếm dụng nhiều RAM nhất và **bắn chết ngay lập tức (Gửi tín hiệu SIGKILL -9)** mà không hề đưa ra bất kỳ cảnh báo nào!
  - Hãy tưởng tượng: Nếu một ngày cơ sở dữ liệu PostgreSQL hoặc cụm Kafka đang ghi dở dữ liệu mà bị `oom-killer` bắn chết bất đắc kỳ tử, dữ liệu bài hát và phòng live sẽ bị hỏng hoàn toàn (Data Corruption) và toàn bộ trang web sập nguồn.
- **Bản đồ phân bổ RAM trên VPS (Sự thật về 6GB trong 7.6GB)**:
  - Trên chiếc máy 7.6GB đó, hệ thống phải gánh trọn vẹn 8 dịch vụ:
    - `kafka`: Giới hạn cứng `mem_limit: 1g` (1024MB).
    - `backend` (Spring Boot 3 + JVM): Giới hạn cứng `mem_limit: 1500m` (1500MB).
    - `coturn` (TURN Server cho WebRTC): Giới hạn `mem_limit: 256m`.
    - `postgres`: Ngốn khoảng 1GB - 1.5GB cho bộ nhớ đệm và kết nối.
    - `redis`: Ngốn khoảng 256MB.
    - `frontend` (Next.js Node process) + `nginx` + `certbot`: Ngốn khoảng 1GB.
    - Hệ điều hành Linux Kernel + Systemd daemon + Page cache: Cần tối thiểu 1.5GB.
  - Tổng cộng: **Hơn 6.5GB RAM đã có chủ**, hệ thống chỉ còn dư chưa đầy 1GB bộ nhớ đệm đề phòng rủi ro.
- **Quyết định sinh tử về việc gỡ bỏ Elasticsearch (2026-08-10)**:
  - Đây là câu chuyện kỹ thuật kinh điển về tư duy kiến trúc của Senior/Lead: Trước đây, tính năng tìm kiếm người dùng (User Search) được dự định chạy trên Elasticsearch.
  - Tuy nhiên, Elasticsearch là một cỗ máy tìm kiếm viết bằng Java cực kỳ "háo ăn RAM", yêu cầu cấu hình tối thiểu `mem_limit: 1280m` — ngốn nhiều RAM hơn cả cụm Kafka!
  - Giữ lại Elasticsearch đồng nghĩa với việc đẩy toàn bộ VPS vào nguy cơ bị OOM Killer bắn hạ bất cứ lúc nào. Vì vậy, đội ngũ kỹ sư đã đưa ra một quyết định dũng cảm: **Xóa bỏ hoàn toàn Elasticsearch khỏi ngăn xếp**, chuyển tính năng tìm kiếm người dùng sang sử dụng công cụ **PostgreSQL Full-Text Search kết hợp Trigram Index (`pg_trgm`)** có sẵn trong database.
  - **Bài học phỏng vấn**: Một Senior Engineer giỏi không phải là người nhồi nhét thật nhiều công nghệ thời thượng, mà là người biết hy sinh công nghệ hào nhoáng để bảo toàn tính ổn định và sự sống còn của hệ thống dưới ràng buộc phần cứng thực tế.

---

##### Luận điểm 2: Mô hình Triển Khai "Build on Runner, VPS Pull-Only" & Ghim ảnh theo Commit SHA (`IMAGE_TAG`)

- **Bản chất của CI/CD (Continuous Integration / Continuous Deployment)**:
  - **CI (Tích hợp liên tục)**: Mỗi khi lập trình viên tạo Pull Request hoặc đẩy code lên Git, máy chủ tự động tải code về, chạy unit test, kiểm tra định dạng code (lint). Nếu có lỗi thì chặn không cho merge.
  - **CD (Triển khai liên tục)**: Khi code được merge vào nhánh chính (`main`), hệ thống tự động đóng gói ứng dụng và đưa lên máy chủ thật cho người dùng sử dụng mà không cần con người can thiệp thủ công.
- **Tại sao TUYỆT ĐỐI KHÔNG build code trực tiếp trên máy chủ VPS?**:
  - Nhiều bạn sinh viên hoặc Junior thường SSH vào VPS rồi gõ lệnh: `mvn clean package` hoặc `npm run build` hoặc `docker compose up -d --build`.
  - **Thảm họa sập máy**:
    1. Quá trình biên dịch Java của Maven (Maven Reactor) cho 11 modules Spring Boot và quá trình build TypeScript của Next.js tiêu tốn 100% CPU và ăn ngốn thêm **từ 2GB đến 4GB RAM** trong quá trình compile.
    2. Nếu build ngay trên VPS, việc biên dịch này sẽ tranh chấp trực tiếp CPU và RAM với PostgreSQL và Kafka đang phục vụ người dùng thực tế $\longrightarrow$ VPS bị nghẽn đơ hoàn toàn, khách hàng không thể nghe nhạc hay gọi video.
    3. Việc tải các layer Docker và rác biên dịch sẽ làm **đầy ổ cứng 19GB** của VPS chỉ sau vài lần deploy!
- **Giải pháp Kiến trúc: Tận dụng GitHub Actions Runner làm cỗ máy thợ hồ**:
  - GitHub Actions cung cấp miễn phí các máy ảo Runner cấu hình rất mạnh (4 vCPU / 16GB RAM) và bị tiêu hủy ngay sau khi dùng xong:
    $$\text{Lập trình viên push Git} \longrightarrow \text{GitHub Runner biên dịch & đóng gói} \longrightarrow \text{Đẩy Image lên GHCR} \longrightarrow \text{VPS chỉ Pull về chạy}$$
  - VPS chỉ làm một thao tác duy nhất là kéo ảnh đóng gói sẵn (~700MB) về qua lệnh `docker compose pull`. Quá trình này chỉ tốn một chút băng thông mạng, hoàn toàn không tốn CPU hay RAM của máy chủ production.
- **Cơ chế ghim ảnh bằng Commit SHA (`IMAGE_TAG`) trong `.env.deploy`**:
  - Nhiều dự án mắc sai lầm dùng tag `:latest` (`image: pwb-backend:latest`). Nhược điểm: Không ai biết container đang chạy commit nào, và khi bản mới có bug thì **hoàn toàn không thể Rollback (quay xe)** về bản cũ được.
  - PWB_MiNi ghim cứng ảnh theo mã commit Git:
    ```bash
    printf 'IMAGE_TAG=%s\n' "$TAG" > .env.deploy
    ```
  - Trong `docker-compose.prod.yml`, ảnh được định nghĩa: `image: ghcr.io/.../pwb-backend:${IMAGE_TAG:-main}`.
  - **Lợi ích thực tế**: Việc lưu `IMAGE_TAG` vào tệp vật lý `.env.deploy` trên VPS giúp việc chạy lệnh thủ công (Manual Run) luôn luôn đồng bộ với phiên bản đang chạy. Nếu ai đó SSH vào máy gõ `docker compose up -d`, hệ thống vẫn nhận diện đúng mã tag commit hiện hành mà không bị kéo nhầm code đang phát triển.

---

##### Luận điểm 3: Nguyên tắc Vàng "Chỉ Nginx Mở Cổng" & Triết lý Backend Chạy Đúng 1 Instance

- **Bản chất Bảo mật: "Only Nginx Publishes Ports"**:
  - Trong file compose phát triển ở máy cá nhân (`application-dev.yml`), các cổng dịch vụ thường được công khai ra ngoài: Postgres mở cổng `5432:5432`, Redis mở `6379:6379`, Kafka mở `9092:9092` để lập trình viên tiện dùng DBeaver kiểm tra.
  - Nhưng trên máy chủ VPS có địa chỉ IP công cộng (Public IP), nếu bạn mở cổng 5432 và 6379, **toàn bộ cơ sở dữ liệu của bạn sẽ bị phơi trần ra Internet toàn cầu**! Hàng ngàn bot tự động của hacker liên tục quét cổng để tấn công vét cạn mật khẩu hoặc mã hóa tống tiền (Ransomware).
  - **Chốt chặn an ninh trong `docker-compose.prod.yml`**:
    - Chỉ duy nhất container `nginx` được phép ánh xạ cổng ra Internet: `80:80` (HTTP) và `443:443` (HTTPS).
    - Toàn bộ PostgreSQL, Redis, Kafka, Backend và Coturn **hoàn toàn không có dòng `ports:` nào mở ra máy chủ host**. Chúng chỉ giao tiếp với nhau qua mạng ảo nội bộ Docker `pwb-network`.
    - Kẻ tấn công từ ngoài Internet không có bất kỳ cách nào chạm được tới database hay Redis, triệt tiêu 100% rủi ro bị quét cổng trái phép.
- **Tại sao Backend Spring Boot bắt buộc phải chạy đúng 1 Instance duy nhất (`replicas: 1`)?**:
  - Đây là câu hỏi phỏng vấn phân loại Senior cực hay: *"Trong production, tại sao em không scale Backend lên 2 hoặc 3 container để chia tải?"*.
  - Câu trả lời nằm ở **bản chất Message Broker của Module Live Room**:
    1. Module Live Room của PWB_MiNi sử dụng kiến trúc **In-Memory SimpleBroker** của Spring WebSocket STOMP (mọi thông tin tin nhắn, trạng thái phòng và danh sách người tham gia được lưu trong RAM của tiến trình Java Backend đó).
    2. Nếu ta chạy 2 container Backend (Backend A và Backend B) sau một Load Balancer:
       - Khi người dùng Alice kết nối vào Backend A.
       - Người dùng Bob kết nối vào Backend B.
       - Dù cả 2 bạn cùng truy cập vào một phòng nghe nhạc, Alice và Bob sẽ **hoàn toàn không nhìn thấy nhau, không nhận được tin nhắn chat của nhau và không nghe thấy nhạc của nhau**! Lý do là vì In-Memory Broker của Backend A hoàn toàn cô lập với Backend B.
    3. Để chạy được nhiều Backend replica, hệ thống bắt buộc phải thay In-Memory SimpleBroker bằng một cụm Message Broker phân tán bên ngoài (như RabbitMQ Stomp Relay hoặc Kafka Event Bridge).
    4. Nhưng dưới ràng buộc VPS chỉ có 7.6GB RAM, việc dựng thêm một cụm Message Broker ngoài sẽ làm sập bộ nhớ. Vì vậy, quyết định **chạy đúng 1 instance Backend duy nhất** là một sự tính toán kiến trúc hoàn toàn nhất quán, bảo đảm tính toàn vẹn dữ liệu cho toàn bộ phòng Realtime.

---

##### Luận điểm 4: Quy trình Triển Khai An Toàn bằng `git reset --hard`, `nginx -s reload` & Quản lý Bí mật (Secrets)

- **Tại sao kịch bản Deploy dùng `git reset --hard origin/main` thay vì `git pull`?**:
  - Đây là một chi tiết thực chiến đắt giá được đúc kết từ những lần sự cố thật:
    - Trong quá trình vận hành, người quản trị có thể SSH vào VPS để kiểm tra lỗi, vô tình gõ lệnh phân quyền `chmod +x run.sh`, hoặc một script nào đó tạo ra file log tạm trong thư mục làm việc.
    - Nếu script CI/CD sử dụng lệnh `git pull` (hoặc `git pull --ff-only`): Git sẽ phát hiện có sự thay đổi quyền file (File mode change) hoặc xung đột và **lập tức từ chối chạy (Abort)**.
    - Sự cố này sẽ **làm tắc nghẽn (Jam) toàn bộ các lần deploy tự động tiếp theo**! Dù lập trình viên có push code bao nhiêu lần thì CI/CD vẫn báo lỗi, cho đến khi có ai đó vào server dọn rác bằng tay.
  - **Giải pháp `git reset --hard origin/main`**:
    - Ép buộc cây thư mục mã nguồn trên VPS luôn luôn là bản sao hoàn hảo 100% của nhánh `main`. Mọi thay đổi vô tình phát sinh trên VPS đều bị xóa bỏ không thương tiếc.
    - Các file nhạy cảm đặc thù của máy chủ (`.env.prod`, `secrets/`, `.env.deploy`) đều được khai báo trong `.gitignore` nên lệnh `reset --hard` hoàn toàn không chạm tới chúng, đảm bảo an toàn tuyệt đối.
- **Nginx không tự nạp lại cấu hình: Bắt buộc chạy `nginx -s reload`**:
  - Tệp `nginx.conf` được gắn từ ổ đĩa máy chủ vào container Nginx thông qua cơ chế Volume Mount.
  - Tuy nhiên, tiến trình Nginx đang chạy trong RAM **hoàn toàn không tự động đọc lại file trên đĩa khi file bị thay đổi**.
  - Nếu trong script deploy không có lệnh:
    ```bash
    docker compose exec -T nginx nginx -s reload
    ```
    thì dù bạn có sửa đổi cấu hình Nginx bao nhiêu lần, Nginx vẫn tiếp tục chạy với cấu hình cũ từ ngày đầu tiên! Lệnh `nginx -s reload` sẽ ra lệnh cho Nginx đọc lại file, tạo các tiến trình worker mới với cấu hình mới và đóng êm các worker cũ mà không làm gián đoạn bất kỳ một kết nối mạng nào của người dùng (Zero Downtime Config Reload).
- **Nguyên tắc "Không bao giờ nướng bí mật vào Image" (Never Bake Secrets)**:
  - Tệp `Backend/.dockerignore` loại trừ tuyệt đối file thông tin tài khoản Google Cloud (`google-tts-credentials.json`) và các file môi trường `.env`.
  - Lý do: Một khi Docker Image được đẩy lên GitHub Container Registry (GHCR), bất kỳ ai có quyền truy cập đều có thể dùng công cụ để bóc tách từng layer của image và đọc được toàn bộ file bên trong.
  - Toàn bộ mật khẩu cơ sở dữ liệu, JWT Secret và khóa API của Google đều được lưu trữ trực tiếp trên VPS và truyền vào container tại thời điểm chạy (Runtime Injection) thông qua `env_file: .env.prod` và volume `secrets/:ro`, bảo vệ an ninh tuyệt đối cho hệ thống.

---

##### Luận điểm 5: Cổng Kiểm Soát Chất Lượng (Quality Gate): Chờ Backend Báo `UP` trong 300s & Chiến Lược Migration Flyway

- **Hiểm họa của quy trình Deploy "mù quáng"**:
  - Rất nhiều kịch bản CI/CD ngây thơ kết thúc bằng lệnh: `docker compose up -d` rồi báo dấu tích xanh ✅ thành công.
  - **Cạm bẫy trong thế giới thực**: Lệnh `docker compose up -d` chỉ có nghĩa là container đã được khởi tạo. Sau khi container bật lên, tiến trình Java Spring Boot bên trong cần:
    1. Mất 30 đến 60 giây để khởi động lạnh máy ảo JVM và nạp Application Context.
    2. Kết nối tới database PostgreSQL và tự động thực thi các tệp SQL của **Flyway Database Migration**.
  - Nếu một lập trình viên viết sai cú pháp SQL, hoặc cố tình thêm một ràng buộc khóa ngoại (Foreign Key) bị xung đột dữ liệu: Flyway sẽ ném ngoại lệ và **tiến trình Backend sẽ tự sập nguồn (Crash) ngay ở giây thứ 45**!
  - Nếu không có bước kiểm tra, GitHub Actions sẽ báo xanh hoàn hảo trong khi toàn bộ trang web thực tế đã bị sập hoàn toàn mà không ai hay biết.
- **Cơ chế Under The Hood: Vòng lặp Quality Gate thăm dò `/actuator/health` trong 300s**:
  - Kịch bản `deploy.yml` thiết lập một vòng lặp kiểm tra sức khỏe nghiêm ngặt:
    ```bash
    for i in $(seq 1 30); do
      if docker compose exec -T backend wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        echo "backend UP after $((i * 10))s"; exit 0
      fi
      sleep 10
    done
    echo "::error::backend did not report UP within 300s"; exit 1
    ```
  - Cứ mỗi 10 giây, script dùng `wget` gọi trực tiếp vào cổng nội bộ `http://localhost:8080/actuator/health` bên trong container backend.
  - Endpoint `/actuator/health` của Spring Boot kiểm tra toàn bộ: kết nối Database sống hay chết, Redis sống hay chết, Kafka sống hay chết, và Flyway đã chạy xong chưa.
  - Chỉ khi nào endpoint trả về đúng chuỗi `"status":"UP"`, quy trình deploy mới được phép đóng dấu hoàn thành. Ngân sách 300 giây (5 phút) đảm bảo máy chủ có đủ thời gian khởi động lạnh ngay cả khi CPU đang bị tải nặng. Nếu quá 300s mà backend không báo UP, script lập tức báo lỗi đỏ và gửi cảnh báo thất bại.
- **Chiến lược Flyway Migration chia dải theo Module độc lập**:
  - Các tệp migration SQL nằm phân tán trong từng module độc lập:
    - `V1`–`V99`: Module `iam` (Tài khoản, quyền hạn, Refresh token).
    - `V100`–`V199`: Module `shared-infrastructure` (Bảng Outbox, Audit logs).
    - `V200`–`V299`: Module `audio` (Bài hát, tệp xử lý âm thanh, Voice tags).
    - `V300`–`V399`: Module `liveroom` (Phòng live, người tham gia).
  - Cấu hình bắt buộc: `spring.flyway.out-of-order: true`.
  - **Ý nghĩa sống còn**: Vì các module do các lập trình viên phát triển song song trên các nhánh Git khác nhau, hoàn toàn có thể xảy ra tình huống nhánh Live Room merge vào trước (đã chạy bản `V304`), sau đó nhánh IAM mới merge vào sau (chứa bản `V15`). Cấu hình `out-of-order: true` cho phép Flyway thực thi bản `V15` một cách bình thường mà không bị báo lỗi từ chối vì sai thứ tự thời gian.

---

##### Elevator Pitch (Tóm tắt bỏ túi 1 phút cho phỏng vấn - Phần 4)

> *"Tại hạ tầng triển khai và vận hành của PWB_MiNi, hệ thống được thiết kế tối ưu hóa xuất sắc dưới ràng buộc phần cứng khắt khe của một VPS 7.6GB RAM thông qua 5 giải pháp chuẩn mực:
> 
> 1. **Kiểm soát bộ nhớ & Gỡ bỏ Elasticsearch**: Áp đặt `mem_limit` cho từng container (Kafka 1GB, Backend 1.5GB, Coturn 256MB) và thay thế Elasticsearch bằng PostgreSQL Trigram Search để bảo vệ hệ thống khỏi sự hủy diệt của Linux OOM Killer.
> 2. **Mô hình CI/CD 'Build on Runner, VPS Pull-Only'**: Tận dụng máy ảo GitHub Actions biên dịch code và đóng gói image gắn tag theo Git Commit SHA (`IMAGE_TAG`), VPS chỉ pull ảnh về chạy nhằm giải phóng 100% CPU/RAM cho máy chủ production.
> 3. **Nguyên tắc 'Only Nginx Publishes Ports' & Backend 1 Instance**: Cô lập tuyệt đối PostgreSQL, Redis, Kafka trong mạng nội bộ Docker để chống quét cổng Internet; duy trì Backend 1 replica để bảo toàn tính toàn vẹn của In-Memory STOMP Broker.
> 4. **Triển khai an toàn với `git reset --hard` & `nginx -s reload`**: Loại bỏ hoàn toàn lỗi tắc nghẽn deploy do thay đổi quyền file, đồng thời reload Nginx êm ái không làm rớt kết nối và không bao giờ nướng bí mật vào image.
> 5. **Cổng kiểm soát chất lượng 300s & Flyway out-of-order**: Vòng lặp thăm dò `/actuator/health` đảm bảo Flyway migration hoàn tất và JVM sẵn sàng trước khi đóng job, kết hợp cơ chế migration phân dải cho phép các module tiến hóa độc lập."*

---

---

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 4: Module Live Room](04-giai-doan-4-module-liveroom.md) | [Giai đoạn 6: Tài Liệu Đối Chiếu ➡️](06-giai-doan-6-tai-lieu-doi-chieu.md)
