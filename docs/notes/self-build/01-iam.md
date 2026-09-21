# F1–F12 · Tài khoản, đăng nhập, người dùng

Bảng cần có trước F1 (Flyway `V1__init.sql`, rút gọn từ `V1__create_iam_schema.sql`):

`roles(id, name, description)` · `users(id, email UNIQUE, password, full_name, avatar_url, phone, status, role_id, oauth_provider, oauth_id, ban_reason, banned_at, banned_by)` · `otp_codes(id, user_id, purpose, code_hash, status, attempts, expires_at, verified_at)` · `password_reset_tokens(id, user_id, token_hash, expires_at, used_at)` · `password_history(id, user_id, password_hash)`

Tất cả đều cộng thêm 6 cột của `BaseEntity`. Seed 2 role `USER`, `ADMIN` (bản gốc `V2__seed_iam_roles.sql`).

`UserStatus`: `PENDING_VERIFICATION, ACTIVE, BANNED, PENDING_DELETION, DELETED`.

---

## F1 · Đăng ký — `POST /api/v1/auth/register`

### Luồng thật
1. Chuẩn hoá email: `trim().toLowerCase()`. Mọi chỗ so sánh/đếm về sau đều dùng bản chuẩn hoá này.
2. Kiểm tra **cooldown đăng ký** theo email (Redis). Còn thời gian chờ → 429 kèm `cooldownSeconds`.
3. Kiểm tra **độ mạnh mật khẩu**. Trước mọi thứ khác, vì đây là phép kiểm rẻ nhất — để sau thì đã trả tiền một vòng BCrypt cho mật khẩu sắp bị từ chối.
4. Tìm user theo email:
   - Chưa có → tạo mới, status `PENDING_VERIFICATION`, role `USER`, hash mật khẩu bằng BCrypt.
   - Đã có và đang `PENDING_VERIFICATION` → **ghi đè** mật khẩu + tên (đăng ký lại người chưa từng xác thực).
   - Đã có và status khác → 409 `EMAIL_ALREADY_REGISTERED`.
5. Phát hành OTP (xem F2 chặng A).
6. Trả **201** với `{ userId, message }`. Không trả token — chưa xác thực thì chưa được vào.

### Tự code
- **A.** `entity/User` + `entity/Role` + `repository/UserRepository` + `AuthService.register()` chỉ làm bước 1, 4, 6. Chưa OTP, chưa cooldown, chưa policy mật khẩu.
- **B.** Thêm `PasswordPolicyService`: độ dài tối thiểu, chữ hoa, chữ thường, số, ký tự đặc biệt. Trả **danh sách** vi phạm chứ không phải boolean — client cần biết thiếu cái gì.
- **C.** Thêm cooldown Redis (`SETNX` + TTL). Nếu chưa muốn dùng Redis, làm bằng một bảng/`ConcurrentHashMap` trước rồi đổi sau.

### Bẫy
- **Value object `EmailAddress`**: bản gốc tự viết regex và chặn thêm 4 trường hợp regex bỏ lọt: `..`, bắt đầu bằng `.`, kết thúc bằng `.`, và **dấu chấm ngay trước `@`** (`foo.@example.com`). Cái cuối quan trọng vì nó hỏng âm thầm: đăng ký thành công, ghi row `PENDING_VERIFICATION`, rồi mail OTP bị server người nhận từ chối — người dùng ngồi nhìn màn hình nhập mã cho một mail không bao giờ tới.
- Đăng ký lại người `PENDING_VERIFICATION` phải **ghi đè**, không phải báo trùng. Nếu báo trùng, ai gõ sai email lần đầu sẽ kẹt vĩnh viễn.

### Kiểm chứng
Đăng ký 2 lần cùng email → lần 2 vẫn 201 và mật khẩu đã đổi. Xác thực xong đăng ký lần 3 → 409.

---

## F2 · Xác thực OTP — `POST /api/v1/auth/verify-otp`

### Luồng thật
1. Rate limit theo **cả IP lẫn userId** (2 key riêng, cùng ngưỡng).
2. Tìm user; tìm OTP `PENDING` theo `(userId, purpose)`. Không có → `AUTH_OTP_EXPIRED`.
3. Kiểm mã, theo đúng thứ tự: đã khoá? → hết hạn? → hash có khớp không?
4. Sai → tăng `attempts`; chạm `maxAttempts` thì chuyển status `LOCKED`. Ném lỗi.
5. Đúng → `status = VERIFIED`, `verifiedAt = now`; user `PENDING_VERIFICATION` → `ACTIVE`.
6. Phát access token + refresh token — **xác thực OTP xong là đăng nhập luôn**, không bắt quay lại màn login.

### Tự code
- **A. Phát OTP** (`OtpService.issue`): sinh mã n chữ số bằng `SecureRandom`, **hash** rồi mới lưu (không bao giờ lưu mã thô), `expiresAt = now + ttl`, **vô hiệu hoá mọi OTP cũ cùng `(userId, purpose)`** trước khi lưu cái mới, gửi mail. Nối vào cuối F1.
- **B. Xác thực**: bước 2–5, trả `{ userId, status }`. Chưa có token.
- **C.** Nối F4 chặng B vào để bước 6 phát token thật.

### Bẫy
- **Đây là bẫy khó nhất của cả module.** Mã sai thì ném exception → transaction rollback → `attempts++` bị xoá theo. Kết quả: đoán mã không giới hạn, mà không có gì báo lỗi.
  Bản gốc tách một bean riêng (`OtpAttemptRecorder`) với `@Transactional(propagation = REQUIRES_NEW)` để ghi lần sai vào transaction khác, **rồi mới** để exception bay lên. Tự viết lại đúng chỗ này là mục đích chính của F2.
- Gọi `REQUIRES_NEW` từ một method cùng class **không có tác dụng** — proxy của Spring không chặn self-invocation. Phải là bean khác.
- OTP phải vô hiệu hoá bản cũ khi phát bản mới, nếu không hai mã cùng sống và "gửi lại" trở thành cách nhân đôi số lần đoán.

### Kiểm chứng
Nhập sai `maxAttempts` lần → lần cuối trả lỗi kèm `maxAttemptsReached: true`, và trong DB `attempts` đúng bằng max, `status = LOCKED`. Đây là chỗ để chứng minh `REQUIRES_NEW` hoạt động.

---

## F3 · Gửi lại OTP — `POST /api/v1/auth/resend-otp`

### Luồng thật
1. Tìm user theo `userId`.
2. **Cooldown** theo email (ví dụ 60s) → còn thì 429 kèm `cooldownSeconds`.
3. **Hạn mức ngày**: đếm số OTP đã phát cho `(userId, purpose)` trong 24h; chạm trần → `AUTH_OTP_DAILY_LIMIT_EXCEEDED`.
4. Phát OTP mới (dùng lại `OtpService.issue` của F2 chặng A).

### Tự code
Một method. Đây là chức năng ngắn nhất — làm để thấy **hai tầng chặn khác nhau**: cooldown chặn spam liên tiếp, hạn mức ngày chặn spam rải đều.

### Bẫy
Chỉ có cooldown thì gửi 1 mail mỗi 60s, cả ngày là 1440 mail. Chỉ có hạn mức ngày thì bấm 10 lần trong 10 giây vẫn lọt. Phải có cả hai.

---

## F4 · Đăng nhập — `POST /api/v1/auth/login`

### Luồng thật
1. Chuẩn hoá email. Rate limit theo IP và theo email.
2. Hỏi **bộ đếm khoá tài khoản** (Redis): `(email, ip)` đang bị khoá không? Khoá → 429 kèm `retryAfterSeconds`.
3. Tìm user. **Không thấy user, hoặc user không có mật khẩu, hoặc mật khẩu sai → cùng một lỗi `LOGIN_BAD_CREDENTIALS`** và cùng ghi một lần thất bại.
4. **Chỉ sau khi mật khẩu đã đúng** mới xét status: `BANNED`/`DELETED` → `ACCOUNT_INACTIVE`; `PENDING_VERIFICATION` → `ACCOUNT_NOT_VERIFIED`.
5. Reset bộ đếm thất bại cho cả email lẫn IP.
6. Phát access token + refresh token.

### Tự code
- **A.** Bước 1, 3, 4, 6 với `TokenService` tạm thời chỉ ký JWT, chưa refresh token.
- **B. `TokenService.issueAccessToken`**: JWT ký HMAC-SHA256, claim gồm `jti` (UUID), `iss`, `aud`, `sub` = userId, `iat`, `exp`, cộng `email`, `role`, `status`, `oauth`. `jti` bắt buộc — F6 cần nó để thu hồi.
- **C. Bộ đếm khoá**: Redis `INCR` + TTL, khoá sau N lần sai trong cửa sổ.

### Bẫy
- **Thứ tự bước 3 trước bước 4 là một quyết định bảo mật.** Xét status trước khi kiểm mật khẩu sẽ biến endpoint này thành máy dò: gõ email bất kỳ, thấy `ACCOUNT_NOT_VERIFIED` tức là email đó có tồn tại. Bạn sẽ rất muốn viết ngược lại vì nó "tự nhiên" hơn — đừng.
- Khoá theo **cả email lẫn IP**: chỉ theo email thì một kẻ tấn công khoá được tài khoản người khác bằng cách gõ sai liên tục (từ chối dịch vụ); chỉ theo IP thì dò rải qua nhiều IP là lọt.
- Khoá phải reset **khi đăng nhập thành công**, nếu không người dùng gõ sai 4 lần rồi đúng lần 5 vẫn mang theo 4 lần sai đó.

### Kiểm chứng
`jwt.io` decode token, đủ 4 claim tuỳ biến. Gõ sai N+1 lần → 429 kèm `retryAfterSeconds`.

---

## F5 · Làm mới token — `POST /api/v1/auth/refresh`

### Luồng thật
1. Đọc refresh token từ **cookie HttpOnly**, không phải từ body. Thiếu → `RefreshTokenInvalid`.
2. Rate limit theo IP.
3. **Xoay token** (`rotateRefreshToken`):
   - SHA-256 chuỗi thô → tra Redis key `refresh:token:{hash}`.
   - Tra được → sinh token mới, và trong **một `MULTI/EXEC`**: ghi key mới, thêm hash mới vào set của user, xoá key cũ, bỏ hash cũ khỏi set, ghi `refresh:rotated:{hash cũ}` với cùng TTL.
   - Tra không được → xem `refresh:rotated:{hash}`: **có** nghĩa là token đã bị xoay này đang được dùng lại → **thu hồi toàn bộ session của user đó** và trả `REFRESH_TOKEN_REUSED`; không có thì chỉ là hết hạn.
4. Tìm user, chặn nếu bị ban.
5. Phát access token mới, trả kèm refresh token mới trong cookie.

### Tự code
- **A.** Sinh refresh token: 32+ byte `SecureRandom`, Base64 URL. **Lưu SHA-256 của nó**, không lưu bản thô — Redis bị đọc thì kẻ đọc không dùng được gì.
- **B.** Ba key Redis: `refresh:token:{hash} → userId` (TTL), `refresh:user:{userId} → SET các hash` (để thu hồi hàng loạt), `refresh:rotated:{hash} → userId` (phát hiện tái sử dụng).
- **C.** Xoay + phát hiện tái sử dụng. Đóng gói bước 3 vào `MULTI/EXEC`.

### Bẫy
- **Phát hiện tái sử dụng là toàn bộ lý do tồn tại của việc xoay token.** Không có nó thì xoay chỉ là đổi chuỗi cho vui. Logic: token cũ mà còn ai dùng được nghĩa là nó đã bị chép — chủ hợp pháp đã cầm token mới rồi. Phản ứng đúng là **huỷ sạch session của tài khoản đó**, không phải trả 401 rồi thôi.
- Cookie phải `HttpOnly` + `Secure` + `SameSite`. Để refresh token trong body JSON là mở cửa cho XSS đọc nó.
- Thu hồi hàng loạt phải xoá **cả** `refresh:token:*` **và** `refresh:rotated:*`, gom vào một lệnh `DEL` nhiều key.

### Kiểm chứng
Gọi `/refresh` hai lần với **cùng** một cookie cũ → lần 2 trả `REFRESH_TOKEN_REUSED`, và token vừa cấp ở lần 1 cũng chết theo.

---

## F6 · Đăng xuất — `POST /api/v1/auth/logout`

### Luồng thật
1. Thu hồi refresh token (xoá key Redis + bỏ khỏi set của user).
2. **Đưa `jti` của access token vào danh sách đen** với TTL đúng bằng thời gian sống còn lại của token.
3. Xoá cookie refresh token.

### Tự code
Một method + một nhánh trong `JwtAuthFilter`: sau khi parse token thành công, kiểm `jti` có trong danh sách đen không, có thì coi như chưa đăng nhập.

### Bẫy
JWT **không thu hồi được** — đó là bản chất của nó. Access token đã phát vẫn hợp lệ tới `exp` dù người dùng đã bấm đăng xuất. Danh sách đen là cách vá, và phải đặt TTL bằng thời gian còn lại chứ không phải một hằng số: ngắn hơn thì token sống lại, dài hơn thì Redis phình vô ích.

---

## F7 · Quên mật khẩu — `POST /api/v1/auth/forgot-password`

### Luồng thật
1. Chuẩn hoá email, kiểm cooldown.
2. Dựng sẵn một `Result` **im lặng** (không có userId).
3. Ba nhánh **đều trả đúng `Result` im lặng đó**, không log ra response:
   - không có user
   - user không `ACTIVE`
   - user đăng nhập bằng Google (không có mật khẩu để đặt lại)
4. Chỉ khi qua hết 3 nhánh mới thật sự: sinh token, vô hiệu hoá token cũ của user, lưu **hash** token, gửi mail kèm link.
5. Trả cùng một body trong mọi trường hợp.

### Tự code
- **A.** Bước 1–3 trả body cố định. Chưa gửi mail.
- **B.** Token đặt lại: chuỗi ngẫu nhiên + **chữ ký HMAC** (`{raw}.{signature}`). Lưu `sha256(raw)` vào DB. Xác minh chữ ký trước khi tra DB — chặn được việc dò token bằng cách bắn DB liên tục.

### Bẫy
Bốn nhánh cùng một câu trả lời nghe rất "sai" khi viết, nhưng bất kỳ khác biệt nào — lỗi khác, có/không có `userId`, thậm chí thời gian phản hồi khác — đều biến endpoint công khai này thành máy dò tài khoản. Đây là chỗ để hiểu vì sao "UX tốt" và "an toàn" đôi khi ngược nhau.

---

## F8 · Đặt lại mật khẩu — `POST /api/v1/auth/reset-password`

### Luồng thật
1. Rate limit theo IP.
2. **Xác minh chữ ký** token → tách phần thô → tra `password_reset_tokens` theo hash, còn hạn và chưa dùng.
3. Tìm user; chặn nếu là tài khoản OAuth.
4. Kiểm policy mật khẩu.
5. **Kiểm không trùng lặp**: so mật khẩu mới với hash hiện tại và với N hash gần nhất trong `password_history`.
6. Đổi mật khẩu → lưu → **đẩy hash cũ vào `password_history`** → cắt bớt cho đủ N.
7. Đánh dấu token đã dùng.
8. **Thu hồi toàn bộ refresh token của user.**
9. Gửi mail báo mật khẩu đã đổi.

### Tự code
- **A.** Bước 1–4, 6, 7.
- **B.** `PasswordHistoryGuard`: `assertNotReused` + `record`. Lưu ý `record` chạy **sau** khi đã đổi, và lưu hash **cũ**.
- **C.** Bước 8, 9.

### Bẫy
- Bước 8 là bắt buộc: đặt lại mật khẩu thường xảy ra vì tài khoản **đã bị chiếm**. Không huỷ session thì kẻ chiếm vẫn ngồi trong đó với refresh token cũ.
- Thứ tự bước 4 trước bước 5 có lý do đo được: bước 4 là kiểm chuỗi, bước 5 chạy **một phép BCrypt cho mỗi bản ghi lịch sử**. Đảo lại là trả tiền cho việc sắp bị vứt đi.

---

## F9 · Đổi mật khẩu — `POST /api/v1/auth/change-password`

Giống F8 nhưng người dùng đã đăng nhập, nên thay bước 1–2 bằng: lấy `userId` từ token, chặn tài khoản OAuth, **kiểm mật khẩu hiện tại**. Từ bước 4 trở đi giống hệt F8 — dùng lại đúng `PasswordHistoryGuard` đó.

### Bẫy
Sai mật khẩu hiện tại và không có mật khẩu (`password == null`) **cùng** trả `AUTH_INVALID_CURRENT_PASSWORD`. Tách ra là lộ thông tin về cách tài khoản được tạo.

---

## F10 · Đăng nhập Google — `POST /api/v1/auth/google-login`

### Luồng thật
1. Rate limit theo IP.
2. Xác minh `idToken` với Google (thư viện `google-api-client`), lấy `sub`, `email`, `name`, `picture`.
3. Tìm user theo `(provider=GOOGLE, oauthId=sub)`.
4. Không có → tìm theo **email**:
   - có user LOCAL cùng email → **liên kết** thêm OAuth vào tài khoản đó
   - không có → tạo mới, `ACTIVE` ngay (Google đã xác thực email, không cần OTP)
5. Phát token như F4.

### Bẫy
Khớp theo email ở bước 4 là con dao hai lưỡi: nó tránh được tài khoản trùng, nhưng **chỉ an toàn khi Google đã xác nhận email đó** (`email_verified`). Bỏ qua cờ đó là cho phép chiếm tài khoản bằng một email giả mạo.

---

## F11 · Hồ sơ và ảnh đại diện — `/api/v1/profile`

`GET /profile` · `PATCH /profile` · `POST /profile/avatar` (multipart)

### Luồng thật — upload avatar
1. Tìm user.
2. Kiểm `contentType` nằm trong danh sách cho phép.
3. Kiểm kích thước.
4. **Kiểm magic bytes** của file có đúng định dạng nó khai không.
5. Sinh key `avatars/{userId}/{uuid}.{ext}`, **stream** thẳng lên S3 (không đọc vào `byte[]`).
6. Lưu **key** vào `users.avatar_url` — không lưu URL.
7. Xoá ảnh cũ, **nuốt lỗi nếu xoá hỏng**.
8. Trả về **presigned URL** vừa ký từ key.

### Tự code
- **A.** `GET`/`PATCH` profile trước. Đơn giản.
- **B.** Upload ra thư mục local, đủ bước 2–4, 6.
- **C.** Đổi sang S3/MinIO + presigned URL. Đây là chỗ đáng tách `StorageService` thành interface với 2 implementation — lúc này bạn *thật sự* có hai, nên sẽ hiểu port/adapter dùng để làm gì thay vì làm theo thói quen.

### Bẫy
- **Không `@Transactional`.** Vòng gọi S3 mất vài giây; giữ một connection DB suốt thời gian đó là cách làm cạn pool khi có vài người upload cùng lúc. Mỗi lệnh repository tự mở transaction ngắn của nó.
- `Content-Type` do client gửi, giả được trong 2 giây. **Magic bytes** mới là thứ quyết định.
- Lưu key chứ không lưu URL: presigned URL hết hạn, lưu nó vào DB là lưu một chuỗi sẽ chết. Ký lại mỗi lần đọc.
- Bước 7 không được làm hỏng request: ảnh mới đã sống rồi, xoá ảnh cũ thất bại chỉ là rác trong bucket.

---

## F12 · Quản trị người dùng — `/api/v1/admin/users`

`GET /search` · `GET /suggest` · `GET /{id}` · `POST /{id}/ban` · `POST /{id}/unban` · `DELETE /{id}` · `GET /stats`

### Luồng thật
Mọi endpoint ghi đều đi qua một **guard** chung trước:
1. `adminId == targetId` → `ADMIN_CANNOT_MODIFY_SELF`
2. không tìm thấy → `USER_NOT_FOUND`
3. target có role `ADMIN` → `ADMIN_CANNOT_MODIFY_ADMIN`

Rồi mới tới nghiệp vụ, nằm **trong entity** chứ không nằm trong service:
- `ban(reason, adminId)`: đang `BANNED` rồi → `ADMIN_USER_ALREADY_BANNED`; ngược lại set status + `banReason` + `bannedAt` + `bannedBy`.
- `unban()`: không phải `BANNED` → `ADMIN_USER_NOT_BANNED`; ngược lại về `ACTIVE` và **xoá cả 3 field ban**.
- `markPendingDeletion()`: chặn nếu đã `PENDING_DELETION` hoặc `DELETED`.

Search dùng JPA `Specification` ghép động: từ khoá (email/tên), status, role, khoảng ngày tạo — mỗi tiêu chí là một `Specification` riêng, `and()` lại.

### Tự code
- **A.** `GET /search` với `Specification` + `Pageable`. Đây là chặng dài nhất.
- **B.** Guard + ban/unban.
- **C.** `DELETE` (soft delete) + `GET /stats` (đếm theo status).

### Bẫy
- **Ba phép kiểm của guard phải đúng thứ tự đó.** Đổi 1 và 2 → admin tự ban mình sẽ nhận `USER_NOT_FOUND` sai. Bỏ 3 → một admin ban được admin khác, và nếu hệ thống chỉ có 2 admin thì có thể khoá sạch quyền quản trị.
- Quy tắc trạng thái nằm **trong entity**, không trong service. Ban hai lần trả 409 là vì `User.ban()` tự biết mình đã bị ban — service không phải nhớ. Đây là khác biệt thật giữa "entity có hành vi" và "entity chỉ là túi getter/setter", và là chỗ dễ thấy nhất trong cả dự án.
- `unban()` phải xoá `banReason`/`bannedAt`/`bannedBy`. Để lại thì lần ban sau hiển thị lý do của lần trước.

---

## Xong F1–F12

Bạn đã có: JWT + refresh rotation + phát hiện tái sử dụng, OTP có chống dò, khoá đăng nhập, lịch sử mật khẩu, upload có kiểm magic bytes, search động, và một bộ mã lỗi thống nhất.

Trước khi sang [02-audio.md](02-audio.md), kiểm lại: đăng ký → OTP → đăng nhập → refresh → đổi mật khẩu → mọi session cũ chết → đăng nhập lại bằng mật khẩu mới. Chạy trọn vòng đó bằng curl không lỗi thì nền đã vững.
