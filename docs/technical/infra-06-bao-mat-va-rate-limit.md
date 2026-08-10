# Hạ tầng — Bảo mật HTTP & giới hạn tần suất

> `shared-web`: `HttpRateLimitFilter`, `HttpRateLimitService`, `ClientIpResolver`, `CookieUtils`, `@CurrentUser` · `iam`: `SecurityConfig`, `JwtAuthenticationFilter`
> Chuỗi filter và vị trí rate limiter đã mô tả ở [01 §5](01-architecture-overview.md); luồng xác thực ở [02](02-lat-cat-doc-dang-nhap.md). File này gom phần hạ tầng dùng chung.

---

## 1. Chuỗi filter, tóm tắt

```
LocaleFilter                    HIGHEST_PRECEDENCE
CorrelationIdFilter             HIGHEST_PRECEDENCE + 10
── Spring Security chain (order -100) ──
    JwtAuthenticationFilter     addFilterBefore UsernamePasswordAuthenticationFilter
    HttpRateLimitFilter         addFilterAfter JwtAuthenticationFilter
    AuthorizationFilter
DispatcherServlet → controller
```

`SecurityConfig` (nằm trong module `iam`) dựng chain: CSRF tắt, CORS mặc định, session `STATELESS`, `OPTIONS /**` permitAll, danh sách public từ cấu hình, còn lại `authenticated()`.

CSRF tắt được vì xác thực là **Bearer token trong header**, không phải cookie phiên — trình duyệt không tự gắn header vào request từ site khác. Refresh token *có* nằm trong cookie, nhưng nó `SameSite=Lax` và chỉ dùng ở đúng một endpoint.

---

## 2. Vì sao rate limiter nằm **trong** chain

Đây là chi tiết đắt nhất của file, và [01 §5.1](01-architecture-overview.md) đã kể đầy đủ. Tóm lại: nó **phải** nằm sau `JwtAuthenticationFilter` (để có principal) và trước `AuthorizationFilter` (để request không token vẫn bị đếm). Lệch bên nào cũng hỏng **lặng lẽ**.

Ràng buộc này bắc qua hai artifact — config ở `iam`, filter ở `shared-web` — nên không module nào tự kiểm được. Nó được chốt bằng `SecurityFilterOrderTest` ở tầng bootstrap.

Kèm theo là mẹo huỷ đăng ký servlet tự động:

```java
registration.setEnabled(false);   // bean vẫn sống trong chain, chỉ không map vào /* nữa
```

Không có nó, filter chạy **hai lần mỗi request từ hai vị trí**, và bản chạy sớm chính là bản ẩn danh mà thay đổi này sinh ra để dẹp.

---

## 3. Đếm theo ai: tài khoản trước, địa chỉ sau

```java
static final String SUBJECT_IP_PREFIX = "ip:";
// và một tiền tố tương ứng cho tài khoản

private String resolveSubject(String clientIp) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    …
}
```

Đã đăng nhập thì đếm theo **id tài khoản**; chưa thì đếm theo **địa chỉ**.

Comment giải thích vì sao có hai tiền tố: không có chúng, một tài khoản mà id tình cờ đọc giống một địa chỉ sẽ dùng chung bộ đếm với địa chỉ đó.

Comment trong `HttpRateLimitService` nói rõ cái giá của việc đếm theo địa chỉ:

> *shared by everybody on the same public address, with no way for an affected user to tell why*

Cả một văn phòng sau NAT dùng chung một bộ đếm, và người bị chặn không có cách nào biết vì sao. Đó chính là lý do đếm theo tài khoản khi có thể — và cũng là lý do rate limiter phải nằm sau filter JWT.

---

## 4. Trần toàn cục và luật riêng cho từng endpoint

```yaml
pwb.rate-limit:
  # globalLimitPerMinute = 500 (mặc định trong RateLimitProperties)
  endpoint-limits:
    tts-preview:
      pattern: /api/v1/voice-tags/tts/preview
      methods: [POST]
      limit: 20
      window-seconds: 60
```

**500 request/phút** cho mọi endpoint, và **đúng một** luật riêng — thấy được trong header của mọi response:

```
X-RateLimit-Limit: 500
X-RateLimit-Remaining: 499
X-RateLimit-Reset: 60
```

Header phát cả hai dạng: `X-RateLimit-*` (quy ước cũ, phổ biến) và `RateLimit-*` (bản chuẩn hoá).

Luật riêng duy nhất là `tts-preview`, và lý do nằm trong comment: **mỗi lần xem thử là một lần Google tính tiền**. Đây là luật rate limit duy nhất trong hệ thống tồn tại vì **tiền**, không vì bảo mật ([audio-02 §3.2](audio-02-voice-tag.md)).

Luật riêng khớp theo `pattern` + `methods` bằng `AntPathMatcher`.

---

## 5. Bốn tầng giới hạn, không phải một

Rất dễ nhầm bốn cơ chế này là một. Chúng nằm ở bốn tầng khác nhau và có thể cùng chặn một request:

| Tầng | Cơ chế | Phạm vi | Khoá Redis |
|---|---|---|---|
| 1 | `HttpRateLimitFilter` | Mọi endpoint HTTP | `pwb:ratelimit:global:{subject}` |
| 2 | `RateLimitGuard` (IAM) | Từng thao tác auth | `iam:ratelimit:<scope>:<key>` |
| 3 | Cooldown (IAM) | Đăng ký, gửi lại OTP, đặt lại mật khẩu | `iam:cooldown:…` |
| 4 | `LoginAttemptChecker` | Chỉ đăng nhập | `iam:login:fail\|lock:…` |

Cộng thêm **hai** cơ chế nữa ngoài HTTP: throttle tra mã phòng ([liveroom-01 §2.2](liveroom-01-vong-doi-phong.md)) và `StompRateLimitInterceptor` ([13 §5.3](13-realtime-stomp.md)).

Sáu cơ chế, và chúng khác nhau ở câu hỏi mỗi cái trả lời:

- "Client này có gửi quá nhanh không?" → tầng 1
- "Thao tác này có bị gọi quá nhiều không?" → tầng 2
- "Đã đủ lâu kể từ lần trước chưa?" → tầng 3
- "Có ai đang dò mật khẩu không?" → tầng 4

Chi tiết ba cái sau ở [iam-00 §4.2](iam-00-tour.md); kỹ thuật Redis ở [infra-02 §2](infra-02-redis.md).

---

## 6. Địa chỉ client: chỉ tin proxy đã khai báo

```java
public static String resolve(HttpServletRequest request, List<String> trustedProxies) {
    …
    if (isProxyTrusted(remoteAddr, trustedProxies)) {
        // lúc này mới đọc X-Forwarded-For
    }
}

private static boolean isProxyTrusted(String remoteAddr, List<String> trustedProxies) {
    if (trustedProxies == null || trustedProxies.isEmpty() || remoteAddr == null) return false;
    return trustedProxies.contains(remoteAddr);
}
```

`X-Forwarded-For` **chỉ được đọc khi kết nối đến từ một địa chỉ nằm trong `pwb.iam.security.trusted-proxies`**.

Đây là chi tiết bảo mật quan trọng: header đó do client đặt được. Tin nó vô điều kiện nghĩa là ai cũng tự khai địa chỉ của mình, và mọi giới hạn theo IP trở nên vô nghĩa — chỉ cần đổi một header là có bộ đếm mới.

Danh sách rỗng (mặc định) là **an toàn**: bỏ qua header, lấy địa chỉ socket. Đúng cho môi trường không proxy.

Nhưng nó cũng là một chỗ dễ cấu hình sai theo hướng ngược lại: sau Nginx mà quên khai proxy thì **mọi request trông như đến từ Nginx**, và cả hệ thống dùng chung một bộ đếm IP.

Việc giải địa chỉ nằm **trong `JwtAuthenticationFilter`** — kể cả với request ẩn danh ([02 §2](02-lat-cat-doc-dang-nhap.md)). Nên `@CurrentClientIp` chỉ có giá trị vì filter JWT đã chạy.

---

## 7. Ba argument resolver tự viết

`WebMvcConfig` đăng ký `@CurrentUser`, `@CurrentClientIp`, `@CurrentUserAgent`.

Không có chúng, mọi controller cần ngữ cảnh HTTP phải nhận `HttpServletRequest` — và tầng `api` dính vào servlet API ở khắp nơi. Với chúng, chữ ký phương thức nói đúng cái nó cần:

```java
public ResponseEntity<…> login(@Valid @RequestBody LoginRequest request,
                               @CurrentClientIp String clientIp,
                               @CurrentUserAgent String userAgent, …)
```

`@CurrentUser` trả về `UUID`. Comment trong `AuthController` ghi rõ: các route đã xác thực **không** nằm trong `public-endpoints`, nên Spring Security từ chối khách vãng lai trước khi handler chạy — `@CurrentUser` không bao giờ null, và một phép kiểm null ở đó sẽ là code chết.

---

## 8. Cookie refresh token

`CookieUtils` đặt và xoá cookie với cùng bộ thuộc tính, lấy từ `RefreshTokenProperties.CookieConfig`:

```
pwb_refresh_token=…; Path=/; Max-Age=1209600; Secure; HttpOnly; SameSite=Lax
```

Comment ở `clearRefreshTokenCookie` chỉ ra một bẫy thật: trình duyệt chỉ thay thế cookie khi **name, path và thuộc tính khớp**. Hard-code `path="/"` sẽ âm thầm không xoá được cookie nếu cấu hình path là thứ khác — người dùng bấm đăng xuất mà phiên vẫn còn.

---

## 9. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Rate limiter trong security chain | Servlet filter đứng trước | Có principal → đếm theo tài khoản | Phụ thuộc thứ tự filter, phải khoá bằng test |
| Huỷ đăng ký servlet của filter | Để Spring Boot tự map | Không chạy hai lần từ hai vị trí | Một bean trông thừa nếu không đọc comment |
| Đếm theo tài khoản khi có thể | Luôn đếm theo IP | Người sau NAT không bị vạ lây | Cần principal, kéo theo ràng buộc thứ tự |
| Hai tiền tố `ip:` / tài khoản | Một namespace | Id trùng dạng địa chỉ không đụng nhau | — |
| Trần chung 500/phút | Luật riêng cho từng endpoint | Một con số đủ cho hầu hết | Endpoint đắt tiền phải tự khai luật |
| Luật riêng chỉ cho `tts-preview` | Nhiều luật | Chỉ endpoint đó tốn tiền thật | — |
| Sáu cơ chế giới hạn khác nhau | Một cơ chế chung | Mỗi cái trả lời một câu hỏi khác | Khó biết cái nào đang chặn mình |
| `X-Forwarded-For` chỉ tin proxy đã khai | Tin vô điều kiện | Client không tự khai IP được | Quên khai proxy thì cả hệ thống chung một bộ đếm |
| Danh sách proxy rỗng là mặc định | Tin sẵn | Mặc định an toàn | Phải nhớ khai khi triển khai sau Nginx |
| Giải IP trong filter JWT | Filter riêng | Một chỗ, chạy cho cả request ẩn danh | Phụ thuộc ngầm: gỡ filter JWT là mất IP |
| CSRF tắt | Bật | Xác thực bằng header, không bằng cookie phiên | Refresh cookie phải dựa vào `SameSite` |
| Argument resolver tự viết | Nhận `HttpServletRequest` | Tầng `api` không dính servlet | Ba lớp phải đăng ký và bảo trì |

---

## 10. Tự kiểm chứng

**Xem header rate limit trên mọi response:**

```bash
curl -s -D - -o /dev/null http://localhost:8080/actuator/health | grep -i ratelimit
```

`500`, `499`, `60`.

**Thấy luật riêng của `tts-preview`** — gọi endpoint đó và so header: `X-RateLimit-Limit` đổi từ `500` sang `20`.

**Thấy đếm theo tài khoản chứ không theo IP** — gọi cùng một endpoint bằng hai token của hai tài khoản khác nhau, từ cùng một máy. `X-RateLimit-Remaining` của mỗi bên giảm **độc lập**. Rồi gọi không kèm token: bộ đếm thứ ba, theo địa chỉ.

**Thấy request không token vẫn bị đếm** — gọi một endpoint cần xác thực mà không kèm token. Nhận 401, nhưng header rate limit vẫn có và `Remaining` vẫn giảm. Đây chính là điều sẽ mất nếu filter nằm sau `AuthorizationFilter`.

**Xem khoá trong Redis:**

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning --scan --pattern 'pwb:ratelimit:*'
```

**Thấy `X-Forwarded-For` bị bỏ qua** — gửi kèm một địa chỉ bịa:

```bash
curl -s -D - -o /dev/null -H "X-Forwarded-For: 1.2.3.4" http://localhost:8080/actuator/health | grep -i ratelimit
```

Bộ đếm **không** reset, vì `127.0.0.1` không nằm trong `trusted-proxies`. Kiểm chéo bằng danh sách khoá Redis ở trên — không có khoá nào cho `1.2.3.4`.

**Xem thuộc tính cookie:**

```bash
curl -s -D - -o /dev/null -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"user1@gmail.com","password":"@NamHoang511"}' | grep -i set-cookie
```

`HttpOnly`, `Secure`, `SameSite=Lax`, `Max-Age=1209600`.

**Xem danh sách endpoint public:**

```bash
grep -n "public-endpoints" -A 14 Backend/bootstrap/src/main/resources/application.yml
```

---

## 11. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Sáu cơ chế giới hạn khác nhau | Không có chỗ nào tổng hợp; khó biết cái nào đang chặn |
| Quên khai `trusted-proxies` sau Nginx | Cả hệ thống dùng chung một bộ đếm IP, hỏng lặng lẽ — mục 6 |
| Rate limit STOMP đếm trong bộ nhớ | Không dùng Redis như đường HTTP; nhân instance là nhân hạn mức ([13 §10](13-realtime-stomp.md)) |
| Chỉ một luật riêng theo endpoint | Endpoint đắt tiền khác phải nhớ tự khai |
| Không có allowlist | Không miễn trừ được cho health check hay giám sát nội bộ |
| Thứ tự filter chỉ được một test giữ | Đúng chỗ, nhưng là điểm duy nhất |
| Người bị chặn theo IP không biết vì sao | Comment trong code thừa nhận; không có thông điệp phân biệt |
| Không có chỉ số cho rate limit HTTP | IAM có `pwb.ratelimit.iam.decision`; tầng HTTP chung thì không |
