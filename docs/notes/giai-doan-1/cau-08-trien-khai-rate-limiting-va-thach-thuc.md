# Câu 8: Triển Khai Rate Limiting & Các Thách Thức Kỹ Thuật Khi Chống Lạm Dụng Request

### ❓ Câu hỏi:
> *"Hãy chia sẻ về cách bạn triển khai cơ chế Rate Limiting trong dự án? Những thách thức kỹ thuật lớn nhất bạn gặp phải khi bảo vệ hệ thống khỏi lạm dụng request là gì và bạn đã xử lý chúng ra sao?"*

---

### 💡 Câu trả lời:

#### 1. Tổng Quan Kiến Trúc Rate Limiting trong PWB_MiNi

Trong dự án `PWB_MiNi`, Rate Limiting không chỉ là một thuật toán chặn request đơn lẻ, mà là một **hệ thống phòng thủ đa tầng (Defense-in-Depth)** bảo vệ ứng dụng khỏi 3 mối nguy:
1. **Lạm dụng tài nguyên (DoS / Resource Exhaustion)**: Client spam request liên tục làm cạn kiệt CPU, RAM và chiếm giữ Tomcat Worker Threads.
2. **Tấn công dò quét (Brute-force)**: Dò mật khẩu đăng nhập, dò mã xác thực OTP hoặc quét mã PIN phòng Live Room.
3. **Bùng nổ chi phí bên thứ ba (Financial Drain)**: Spam các endpoint gọi dịch vụ AI/Cloud tính phí (như Google Cloud Text-to-Speech API).

Trọng tâm của tầng HTTP là sự phối hợp giữa hai thành phần tại `shared-web`:
- **`HttpRateLimitFilter`**: Một Servlet Filter (`OncePerRequestFilter`) nằm trong chuỗi `SecurityFilterChain`, chịu trách nhiệm chặn bắt request, nhận diện danh tính (User vs IP), khớp rule đường dẫn và tiêm các HTTP Headers chuẩn hóa (`X-RateLimit-*`, `RateLimit-*`, `Retry-After`).
- **`HttpRateLimitService`**: Tầng Service thực thi kiểm tra hạn ngạch phân tán dựa trên **Redis kết hợp Lua Script** với trần mặc định **500 request/phút** cho toàn bộ API và các bộ hạn ngạch riêng biệt cho từng endpoint nhạy cảm.

```
Client Request 
     │
     ▼
[ Servlet Container: Tomcat ]
     │
     ▼ (Spring Security Filter Chain)
[ JwtAuthenticationFilter ] ───> Xác định danh tính (SecurityContext có AuthenticatedUser)
     │
     ▼
[ HttpRateLimitFilter ]     ───> 1. Trích xuất Client IP (ClientIpResolver)
     │                           2. Xác định Subject (u:<userId> hoặc ip:<clientIp>)
     │                           3. Khớp Scope rule (AntPathMatcher: global hoặc tts-preview)
     │                           4. Gọi HttpRateLimitService (Redis Lua Script)
     │                           5. Tiêm Response Headers (X-RateLimit-*, RateLimit-*, Retry-After)
     │
     ├───> [Bị từ chối: HTTP 429] ──> Trả về JSON ApiResponse kèm retryAfterSeconds
     │
     ▼ [Được chấp thuận]
[ AuthorizationFilter ]     ───> Kiểm tra quyền hạn (Role / Authority)
     │
     ▼
[ Controller / API Layer ]
```

---

#### 2. Sáu (06) Thách Thức Kỹ Thuật Lớn Nhất & Giải Pháp Thực Chiến

##### ⚡ Thách thức 1: Bài toán "Vạ lây" sau mạng NAT (Carrier-Grade NAT / Office / School)
- **Vấn đề**: Nếu chỉ rate limit theo địa chỉ IP (`clientIp`), toàn bộ nhân viên trong cùng một công ty hoặc sinh viên trong một trường đại học sẽ dùng chung một IP Public ra ngoài Internet.
  - Khi một người vô tình gọi hết quota của API nghe thử giọng đọc (20 lượt/phút), **toàn bộ văn phòng bị khóa oan (HTTP 429)** mà không ai hiểu lý do.
  - Ngoài ra, khi người dùng di chuyển giữa Wifi và mạng di động 4G, IP bị thay đổi làm hạn ngạch bị reset không nhất quán.
- **Giải pháp trong `HttpRateLimitFilter.resolveSubject`**:
  Hệ thống áp dụng cơ chế **Nhận diện Danh tính Kép (Dual Identity Resolution)**:
  ```java
  private String resolveSubject(String clientIp) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.isAuthenticated()
              && authentication.getPrincipal() instanceof AuthenticatedUser user
              && user.getUserId() != null && !user.getUserId().isBlank()) {
          return SUBJECT_USER_PREFIX + user.getUserId(); // u:<userId>
      }
      return SUBJECT_IP_PREFIX + clientIp;             // ip:<clientIp>
  }
  ```
  - **Đã đăng nhập**: Khóa hạn ngạch tính riêng theo **ID tài khoản (`u:<userId>`)**. Dù cả công ty chung IP hay người dùng đổi từ Wifi sang 4G, hạn ngạch của từng cá nhân vẫn độc lập tuyệt đối.
  - **Khách ẩn danh**: Fallback về địa chỉ IP (`ip:<clientIp>`) để bảo vệ các trang công khai (Đăng nhập, Đăng ký, Quên mật khẩu).
  - Hai tiền tố `u:` và `ip:` giúp ngăn chặn tuyệt đối việc trùng lặp key nếu ID tài khoản vô tình có chuỗi ký tự giống địa chỉ IP.

---

##### ⚡ Thách thức 2: Vị trí "Bánh Kẹp" trong Security Chain & Cạm Bẫy Filter Chạy 2 Lần
- **Vấn đề**: Vị trí của `HttpRateLimitFilter` trong chuỗi filter của Spring Boot là một điểm nhạy cảm bậc nhất. Lệch về bên nào cũng gây ra lỗi ngầm (Silent Bug) cực kỳ nguy hiểm:
  1. *Nếu đặt trước `JwtAuthenticationFilter`*: Lúc này `SecurityContext` hoàn toàn rỗng, mọi request đều trông như khách vãng lai, hệ thống bị ép đếm theo IP -> Tái diễn lỗi nghẽn mạng NAT ở Thách thức 1.
  2. *Nếu đặt sau `AuthorizationFilter`*: Những request không có token sẽ bị quăng lỗi `401 Unauthorized` trước khi được đếm -> Kẻ tấn công có thể spam hàng triệu request ẩn danh mà không bao giờ bị rate limit chặn lại.
  3. *Cạm bẫy Servlet Auto-Registration*: Khi khai báo `HttpRateLimitFilter` là một Spring Bean, Spring Boot mặc định tự động đăng ký nó vào Servlet Chain (chạy ngoài cùng mép Servlet). Nếu ta lại thêm nó vào `SecurityFilterChain` bằng `addFilterAfter`, filter sẽ bị **thực thi 2 lần cho mỗi request**: Lần 1 chạy sớm ở mép ngoài (chưa có token -> đếm nhầm theo IP), lần 2 chạy trong Security (đếm theo User).
- **Giải pháp kiến trúc**:
  - Đặt chính xác sau JWT Filter và trước Authorization Filter:
    ```java
    http.addFilterAfter(httpRateLimitFilter, JwtAuthenticationFilter.class);
    ```
  - **Vô hiệu hóa đăng ký Servlet tự động** trong cấu hình:
    ```java
    @Bean
    public FilterRegistrationBean<HttpRateLimitFilter> httpRateLimitFilterRegistration(
            HttpRateLimitFilter filter) {
        FilterRegistrationBean<HttpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Ngăn map tự động vào /*, chỉ giữ lại trong SecurityChain
        return registration;
    }
    ```
  - Ràng buộc thứ tự này được bảo vệ nghiêm ngặt bằng bài kiểm thử `SecurityFilterOrderTest`.

---

##### ⚡ Thách thức 3: Xử Lý Race Condition Bằng Redis Lua Script Nguyên Tử (Atomic Counter)
- **Vấn đề**: Trong môi trường chịu tải cao (hàng ngàn request/giây trên cụm phân tán), nếu kiểm tra rate limit bằng mã nguồn Java (gọi `redis.get()`, kiểm tra, rồi `redis.incr()`, `redis.expire()`), sẽ phát sinh **Race Condition** và tiêu tốn nhiều vòng mạng (Network Round-Trip Time - RTT). Hơn nữa, nếu server bị sập giữa lệnh `INCR` và `EXPIRE`, key sẽ tồn tại vĩnh viễn không có TTL (Memory Leak trên Redis).
- **Giải pháp trong `HttpRateLimitService`**:
  Toàn bộ logic kiểm tra và trừ hạn ngạch được thực thi gói gọn trong một **Lua Script nguyên tử chạy trực tiếp trên Redis**:
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
      return {0, ttl, 0}           -- Bị từ chối: {allowed=0, retryAfter=ttl, remaining=0}
  else
      return {1, limit - count, ttl} -- Được duyệt: {allowed=1, remaining, resetSeconds}
  end
  ```
  - `INCR` và `EXPIRE` thực thi trong 1 chu kỳ máy trên Redis, đảm bảo tính nguyên tử tuyệt đối (ACID) mà không cần distributed lock phức tạp.
  - Trả về đủ 3 thông số: trạng thái duyệt/chặn, số lượt còn lại (`remaining`), và thời gian reset (`resetSeconds`) chỉ trong đúng **1 vòng mạng duy nhất**.

---

##### ⚡ Thách thức 4: Độ Sẵn Sàng Cao (High Availability) & Chiến Lược Fail-Open
- **Vấn đề**: Redis là một hệ thống đệm phụ trợ (Auxiliary Service). Điều gì sẽ xảy ra nếu cụm Redis bị sập, khởi động lại hoặc nghẽn mạng? Nếu chọn chính sách chặn toàn bộ (Fail-Closed), toàn bộ người dùng sẽ bị từ chối và hệ thống chính sập theo.
- **Giải pháp**:
  Hệ thống áp dụng triết lý **Fail-Open**:
  ```java
  try {
      // Gọi Redis Lua Script
  } catch (Exception ex) {
      recordMetric("allow", "fail-open");
      log.warn("Redis unavailable for rate limit check: subject={} reason={}", subject, ex.getMessage());
      return RateLimitResult.allow(limit, window.getSeconds()); // Cho phép đi qua!
  }
  ```
  Khi Redis gặp sự cố, hệ thống tự động cho phép request đi qua, ghi log cảnh báo và tăng counter giám sát Micrometer/Prometheus. Mục tiêu: **Ưu tiên tính sẵn sàng của dịch vụ cốt lõi hơn là hy sinh người dùng vì một lỗi của bộ lọc giới hạn**.

---

##### ⚡ Thách thức 5: Nguy Cơ "Bucket Bleeding" (Lẫn Lộn Hạn Ngạch Giữa Các Endpoint)
- **Vấn đề**: Nếu cấu trúc key Redis chỉ có `pwb:ratelimit:<subject>`, một lượt lướt web bình thường (thuộc trần 500 req/phút) sẽ vô tình trừ luôn vào hạn mức 20 req/phút của API `tts-preview`. Ngược lại, hạn mức 20 req/phút không thể kìm hãm được nếu so với trần 500.
- **Giải pháp**:
  Phân tách cấu trúc Key theo **Scope độc lập**:
  ```text
  pwb:ratelimit:<scope>:<subject>
  ```
  - Yêu cầu thông thường: `scope = "global"` (Trần 500 req/phút).
  - Yêu cầu xem thử giọng đọc TTS: `scope = "tts-preview"` (Trần 20 req/phút).
  Nhờ có `scope`, người dùng có thể thoải mái lướt xem danh sách bài hát (dùng bucket `global`) mà không làm hao hụt hạn mức nghe thử TTS (dùng bucket `tts-preview`), bảo vệ triệt để chi phí Google Cloud API.

---

##### ⚡ Thách thức 6: "Góc Khuất" Ngoài HTTP — Rate Limiting Cho WebSocket / STOMP
- **Vấn đề**: `HttpRateLimitFilter` chỉ chặn được ở tầng Servlet Container. Trong phòng Live Room, sau khi bắt tay HTTP thành công, kết nối chuyển sang giao thức **WebSocket hai chiều (TCP Socket kéo dài)**. Các gói tin tua nhạc, bật mic, chat gửi qua STOMP frame **hoàn toàn không đi qua HTTP Filter nữa**! Kẻ xấu có thể mở 1 kết nối duy nhất và spam 10,000 gói STOMP/giây làm tràn bộ đệm socket và sập phòng live.
- **Giải pháp**:
  Xây dựng thêm tầng **`StompRateLimitInterceptor`** ở tầng WebSocket với 3 bucket trượt độc lập:
  - Bucket cho tín hiệu WebRTC (Signaling): Đảm bảo chất lượng thoại realtime.
  - Bucket cho tin nhắn Chat: Chống spam chat trong phòng.
  - Bucket cho các lệnh điều khiển khác (Seek, Play, Pause).

---

#### 3. Chuẩn Hóa Header Phản Hồi Cho Client & Frontend

`HttpRateLimitFilter` tự động tiêm cả 2 chuẩn header vào phản hồi:
- Chuẩn truyền thống: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- Chuẩn hiện đại của IETF: `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`.
- Khi bị từ chối (`HTTP 429`), gửi kèm header `Retry-After: <seconds>` và body JSON chuẩn:
  ```json
  {
    "success": false,
    "error": {
      "code": "RATE_LIMITED",
      "message": "Quá nhiều yêu cầu. Vui lòng thử lại sau 45 giây.",
      "retryAfterSeconds": 45
    }
  }
  ```
  Frontend sử dụng giá trị `retryAfterSeconds` này để hiển thị đồng hồ đếm ngược và khóa nút bấm trên giao diện người dùng.

---

#### 4. Bảng Tổng Hợp 6 Tầng Rate Limiting Đa Lớp Trong PWB_MiNi

| Tầng | Cơ Chế Triển Khai | Phạm Vi Bảo Vệ | Ngưỡng Giới Hạn | Vị Trí / Khoá Redis |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **`HttpRateLimitFilter`** | Toàn bộ API HTTP | 500 req/phút (toàn cục)<br>20 req/phút (`tts-preview`) | `pwb:ratelimit:<scope>:<subject>` (Redis) |
| **2** | **`RateLimitGuard`** | Thao tác IAM nhạy cảm (Quên/Đổi mật khẩu) | 10 req/phút | `iam:ratelimit:<scope>:<key>` (Redis) |
| **3** | **Cooldown Lock** | Gửi lại mã OTP, Resend Email | Giãn cách tối thiểu 60 giây | `iam:cooldown:<action>:<email>` (Redis) |
| **4** | **`LoginAttemptChecker`** | Chống Brute-force mật khẩu | Sai 5 lần liên tiếp -> Khóa 15 phút | `iam:login:fail:<ip/email>` (Redis) |
| **5** | **Room Code Throttle** | Chống quét/dò mã PIN phòng Live | Cửa sổ trượt 15 phút riêng | `pwb:ratelimit:liveroom:join:<ip>` (Redis) |
| **6** | **`StompRateLimitInterceptor`** | Kết nối thời gian thực WebSocket STOMP | 3 bucket trượt (Signaling, Chat, Khác) | In-Memory Socket Channel Interceptor |
