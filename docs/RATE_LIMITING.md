# Rate Limiting - Hướng dẫn Frontend

Document này mô tả cách Backend `pwb-refactor` enforce rate limiting và cách Frontend nên handle để UX mượt mà.

---

## 1. Kiến trúc 4 lớp

Backend áp dụng rate limiting ở 4 tầng:

| Tầng | Áp dụng cho | Cơ chế |
|---|---|---|
| **L1 - Global HTTP filter** | Mọi request | Per-IP, default 100 req/phút |
| **L2 - Per-use-case throttle** | Login, refresh, verify-otp, change-password, complete-profile, google-login | Per-IP + per-user-id/email |
| **L3 - Account lockout** | Login fail | Sau 5 fail liên tiếp (per email) hoặc 20 fail (per IP) → khóa |
| **L4 - Cooldown** | Register, resend-otp, forgot-password | Per-email, thời gian chờ cố định |

---

## 2. Error codes cần biết

Khi rate-limited, Backend trả `HTTP 429` với body:

```json
{
  "success": false,
  "code": "IAM_014",
  "message": "Quá nhiều yêu cầu. Vui lòng thử lại sau.",
  "error": {
    "retryAfterSeconds": 45
  },
  "traceId": "...",
  "timestamp": 1722339000000
}
```

| Code | HTTP | Nghĩa | UX nên làm |
|---|---|---|---|
| `RATE_LIMITED` | 429 | Global filter chặn (L1) | Hiển thị banner "Quá nhiều yêu cầu", auto-retry sau `Retry-After` giây |
| `IAM_014` | 429 | Per-use-case throttle (L2) | Disable button tương ứng + countdown |
| `IAM_005` | 429 | Account locked do login fail (L3) | Disable nút login + hiển thị "Tài khoản tạm khóa, mở lại sau X phút" |
| `IAM_017` | 429 | Password reset cooldown (L4) | Disable nút forgot-password + countdown |
| `IAM_026` | 429 | OTP resend rate-limit (L4) | Disable nút resend OTP + countdown |
| `IAM_027` | 429 | OTP daily limit (L4) | Hiển thị "Bạn đã đạt giới hạn OTP hôm nay" |

---

## 3. Headers cần đọc

Mọi response từ Backend đều có headers sau (kể cả thành công):

| Header | Nghĩa | Type |
|---|---|---|
| `RateLimit-Limit` | Tổng quota trong window | integer |
| `RateLimit-Remaining` | Còn lại bao nhiêu | integer |
| `RateLimit-Reset` | Số giây đến khi reset | integer |
| `Retry-After` | Số giây nên chờ (chỉ khi 429) | integer |
| `X-RateLimit-Limit` | Legacy header (giống RateLimit-Limit) | integer |
| `X-RateLimit-Remaining` | Legacy header | integer |
| `X-RateLimit-Reset` | Legacy header | integer |

**Lưu ý**: Cả `RateLimit-*` (IETF draft-09) và `X-RateLimit-*` (legacy) đều được emit để tương thích client cũ. Nên ưu tiên đọc `RateLimit-*` trước, fallback về `X-RateLimit-*`.

---

## 4. UX flow chuẩn

### 4.1 Khi nhận 429 (Rate Limited)

```
1. Parse error body → lấy retryAfterSeconds
2. Disable button ngay lập tức
3. Hiển thị countdown: "Vui lòng thử lại sau X giây"
4. Sau khi countdown = 0 → enable button, cho phép retry
5. KHÔNG retry tự động trước khi countdown kết thúc (sẽ bị chặn tiếp)
```

Code mẫu (TypeScript):

```typescript
async function callApiWithRateLimit<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (err) {
    if (err.response?.status === 429) {
      const retryAfter = err.response.headers['retry-after']
        ?? err.response.data?.error?.retryAfterSeconds
        ?? 60;
      showCountdownBanner(retryAfter);
      throw new RateLimitError(retryAfter);
    }
    throw err;
  }
}
```

### 4.2 Khi nhận `IAM_005` (Account Locked)

```
1. Disable form login
2. Hiển thị: "Tài khoản tạm khóa do đăng nhập sai nhiều lần"
3. Đọc retryAfterSeconds từ error.retryAfterSeconds
4. Countdown → enable lại
5. Khuyến nghị: dẫn user đến forgot-password
```

### 4.3 Khi nhận `IAM_017` (Password Reset Cooldown)

```
1. Disable nút "Gửi email đặt lại"
2. Hiển thị countdown
3. Sau countdown → enable nút
4. Nếu user spam click liên tục → debounce ngay từ FE
```

### 4.4 Khi nhận `IAM_026` (OTP Resend Limit)

```
1. Disable nút "Gửi lại OTP"
2. Hiển thị: "Vui lòng chờ X giây để gửi lại"
3. Sau countdown → enable nút
4. Áp dụng exponential backoff nếu user liên tục spam
```

### 4.5 Khi nhận `IAM_027` (OTP Daily Limit)

```
1. Disable hoàn toàn chức năng resend OTP đến hết ngày
2. Hiển thị: "Bạn đã đạt giới hạn OTP hôm nay. Vui lòng thử lại vào ngày mai."
3. Hướng dẫn user dùng forgot-password hoặc liên hệ support
```

---

## 5. Khuyến nghị kỹ thuật

### 5.1 Debounce button

Áp dụng debounce 300-500ms cho mọi button trigger API call. Tránh user click đúp do network chậm.

### 5.2 Exponential backoff (cho resend OTP, forgot-password)

```
attempt 1: chờ 0s
attempt 2: chờ 30s
attempt 3: chờ 60s
attempt 4: chờ 120s
```

### 5.3 KHÔNG retry tự động trên 429

Retry tự động trên 429 sẽ làm Backend càng chặt hơn. LUÔN chờ đúng `retryAfterSeconds` rồi mới thử lại.

### 5.4 Cache `RateLimit-Reset` để UX mượt hơn

Trên header của response thành công, đọc `RateLimit-Remaining`. Nếu = 0 → hiển thị warning "Bạn sắp hết quota, vui lòng giảm tần suất".

### 5.5 Xử lý đồng hồ không đồng bộ

`RateLimit-Reset` là số giây tương đối (không phải timestamp tuyệt đối). Đếm ngược theo `Date.now() + retryAfterSeconds * 1000 - performance.now()` để chính xác.

---

## 6. Endpoint cụ thể - rate limits

| Endpoint | Limit | Window | Strategy |
|---|---|---|---|
| `POST /api/v1/auth/register` | 5 | 1 phút | Per-IP |
| `POST /api/v1/auth/login` | 10 | 1 phút | Per-IP + per-email |
| `POST /api/v1/auth/refresh` | 30 | 1 phút | Per-IP |
| `POST /api/v1/auth/google-login` | 10 | 1 phút | Per-IP + per-email |
| `POST /api/v1/auth/verify-otp` | 10 | 1 phút | Per-userId + per-IP |
| `POST /api/v1/auth/change-password` | 5 | 1 phút | Per-userId + per-IP |
| `POST /api/v1/auth/complete-profile` | 10 | 1 phút | Per-userId + per-IP |
| `POST /api/v1/auth/forgot-password` | 1 | 60 giây | Per-email (cooldown) |
| `POST /api/v1/auth/resend-otp` | 1 | 60 giây | Per-email (cooldown) + 10/ngày |
| Global (mọi endpoint) | 100 | 1 phút | Per-IP |

---

## 7. Test nhanh với curl

```bash
# Login fail để trigger account lockout
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com","password":"wrong"}'
done

# Check response headers
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"wrong"}' | grep -i ratelimit
```

---

## 8. Câu hỏi thường gặp

**Q: Tại sao bị 429 dù mới bắt đầu dùng app?**
A: Có thể đang share IP với nhiều người (WiFi công ty, NAT, VPN). Kiểm tra quota per-IP. Liên hệ backend team nếu cần whitelist.

**Q: `RateLimit-Reset` và `Retry-After` khác nhau không?**
A: `RateLimit-Reset` có trên mọi response (kể cả 200 OK). `Retry-After` chỉ có trên 429. Giá trị giống nhau nếu cùng context.

**Q: User bị lockout có cách nào mở nhanh không?**
A: Không. Lockout là cơ chế bảo mật, cần chờ đủ thời gian. User có thể reset password sau khi lock hết hạn.