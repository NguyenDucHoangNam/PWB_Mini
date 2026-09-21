# Câu 2: Đăng Xuất (Logout), Redis Blacklist & Cơ Chế Refresh Token Rotation (RTR) Chống Đánh Cắp Phiên

### ❓ Câu hỏi:
> *"Bản chất JWT là Stateless, vậy hệ thống của bạn xử lý bài toán Đăng xuất (Logout) và Thu hồi token (Token Revocation) như thế nào? Cơ chế Refresh Token Rotation (RTR) kết hợp bẫy phát hiện token bị đánh cắp hoạt động ra sao?"*

---

### 💡 Câu trả lời:

#### 1. Bài Toán Thu Hồi Token Trong Kiến Trúc Stateless

Một trong những thách thức lớn nhất của JSON Web Token (JWT) là tính **không trạng thái (Stateless)**: Server không duy trì phiên trong bộ nhớ, do đó một khi Access Token đã được ký và phát hành, nó sẽ hợp lệ tuyệt đối cho đến giây cuối cùng của thời hạn (15 phút).

Nếu người dùng bấm **Đăng xuất (Logout)** ở phút thứ 2, việc chỉ đơn thuần xóa token ở phía trình duyệt (Client-side) là chưa đủ. Nếu kẻ tấn công đã kịp sao chép chuỗi token đó trước đó, chúng vẫn có thể gửi request lên server và thao tác bình thường trong 13 phút còn lại.

Hệ thống `PWB_MiNi` giải quyết bài toán này bằng **sự phối hợp giữa hai kỹ thuật chuyên biệt**:
1. **Redis Blacklist**: Vô hiệu hóa tức thì Access Token ngắn hạn (15 phút).
2. **Refresh Token Rotation (RTR) & Bẫy Tombstone**: Xoay vòng và phát hiện truy cập trái phép đối với Refresh Token dài hạn (14 ngày).

---

#### 2. Cơ Chế Đăng Xuất (Logout) & Redis Blacklist Cho Access Token

Khi người dùng gọi API `POST /api/v1/auth/logout`:

```
Client (Bấm Logout)
  │
  ├─ Gửi: Header Authorization: Bearer <AccessToken>
  │       Cookie: pwb_refresh_token
  ▼
[ AuthController / LogoutUseCaseImpl ]
  │
  ├─ 1. Thu hồi Refresh Token: Xóa khỏi Redis (iam:refresh:token:<sha256>)
  │
  ├─ 2. Trích xuất 'jti' (JWT ID) và 'expiresInSeconds' còn lại của Access Token
  │
  ├─ 3. Đưa vào Redis Blacklist:
  │      SET iam:jwt:blacklist:<jti> = "1" EX <thời gian còn lại>
  │
  ├─ 4. Xóa Cookie Refresh Token trên trình duyệt (Set-Cookie Max-Age=0)
  ▼
Client nhận 200 OK (Đăng xuất thành công)
```

##### 🔍 Cách `JwtAuthenticationFilter` Chặn Đứng Token Bị Blacklist:
Trong mỗi HTTP request tiếp theo, khi đi qua `JwtAuthenticationFilter`:
```java
// JwtAuthenticationFilter.java
String jti = result.claims().getId();
if (jti != null && blacklistService.isAccessTokenBlacklisted(jti)) {
    log.debug("Token is blacklisted: jti={}", jti);
    SecurityContextHolder.clearContext(); // Xóa sạch ngữ cảnh xác thực
}
```
- Server kiểm tra nhanh sự tồn tại của key `iam:jwt:blacklist:<jti>` trên Redis.
- Nếu key tồn tại $\rightarrow$ Xóa sạch `SecurityContext` $\rightarrow$ Request bị chặn lại ở tầng `AuthorizationFilter` với mã **HTTP 401 Unauthorized**.
- **Tối ưu bộ nhớ Redis**: TTL của key blacklist được gán đúng bằng **thời gian sống còn lại của token** (tối đa 15 phút). Sau 15 phút, key tự động bốc hơi, danh sách đen không bao giờ bị phình to vô hạn.

---

#### 3. Cơ Chế Refresh Token Rotation (RTR) & Bẫy Phát Hiện Trộm Token

Refresh Token có thời hạn dài (14 ngày), nếu bị kẻ gian đánh cắp thì hậu quả vô cùng nghiêm trọng. Để triệt tiêu rủi ro này, dự án áp dụng kỹ thuật **Refresh Token Rotation (Xoay vòng token)** kết hợp **Tombstone Key (Bia mộ lưu vết)** trong `TokenManagerServiceAdapter.java`:

##### 🔄 Kịch Bản 1: Luồng Xoay Vòng Bình Thường (Người Dùng Hợp Lệ)
Mỗi lần client gọi API `POST /api/v1/auth/refresh`:
1. Client gửi `pwb_refresh_token` cũ (ví dụ: `Token_1`).
2. Server băm `hash_1 = sha256(Token_1)` và kiểm tra trong Redis `iam:refresh:token:<hash_1>`.
3. Nếu hợp lệ, server thực thi một khối lệnh **nguyên tử (Redis MULTI/EXEC)**:
   - Tạo token mới tinh `Token_2` (`hash_2`), lưu vào Redis: `iam:refresh:token:<hash_2> -> userId` (TTL 14 ngày).
   - **Xóa token cũ**: Xóa key `iam:refresh:token:<hash_1>`.
   - **Lưu bia mộ (Tombstone)**: Lưu vết token cũ vừa bị hủy vào `iam:refresh:rotated:<hash_1> -> userId` với TTL bằng phần đời còn lại.
4. Trả về `Token_2` mới trong Cookie cho client.

```
Lần 1: Client dùng Token_1  ──> Server cấp Token_2 (Token_1 biến thành Bia mộ)
Lần 2: Client dùng Token_2  ──> Server cấp Token_3 (Token_2 biến thành Bia mộ)
```

---

##### 🚨 Kịch Bản 2: Bẫy Phát Hiện Token Bị Đánh Cắp (Token Theft / Replay Attack)
Giả sử kẻ tấn công (Attacker) đánh cắp được `Token_1` từ máy nạn nhân (Victim):

```
                       [NẠN NHÂN HỢP LỆ]                 [KẺ TẤN CÔNG (HACKER)]
                              │                                    │
    (1. Đổi token bình thường)│                                    │
        Gửi Token_1 ─────────>│ (Server hủy Token_1,               │
                              │  cấp Token_2 cho Nạn nhân,         │
                              │  ghi Token_1 vào Bia Mộ)           │
                              │                                    │
                              │                    (2. Cố tình gửi lại Token_1 cũ)
                              │                     Gửi Token_1 ──>│
                              │                                    ▼
                              │                    [ Server kiểm tra Redis ]
                              │                    • iam:refresh:token:hash_1 -> KHÔNG CÒN
                              │                    • iam:refresh:rotated:hash_1 -> TỒN TẠI!
                              │                                    │
                              │                                    ▼
                              │                    🚨 PHÁT HIỆN TẤN CÔNG REPLAY!
                              │                    Server gọi: revokeAllRefreshTokensForUser
                              ▼                                    ▼
               [Bị đá văng toàn bộ phiên]             [Bị chặn: REFRESH_TOKEN_REUSED]
```

##### 🛡️ Phân Tích Mã Nguồn Xử Lý Tại `TokenManagerServiceAdapter.rotateRefreshToken`:
```java
String userIdStr = redis.opsForValue().get(key);
if (userIdStr == null) {
    // Token không còn hiệu lực. Kiểm tra xem có phải là token đã từng bị xoay vòng không?
    String reusedOwner = redis.opsForValue().get(rotatedKey(hash));
    if (reusedOwner != null) {
        log.warn("Refresh token reuse detected, revoking all sessions: userId={}", reusedOwner);
        // HÀNH ĐỘNG TRỪNG PHẠT: Hủy sạch toàn bộ phiên của tài khoản trên mọi thiết bị!
        revokeAllRefreshTokensForUser(UUID.fromString(reusedOwner));
        throw new BusinessException(IamErrorCode.REFRESH_TOKEN_REUSED);
    }
    throw new RefreshTokenExpiredException();
}
```

- **Nguyên lý an ninh**: Khi một token đã nằm trong danh sách "Bia mộ" (`iam:refresh:rotated:*`) mà lại xuất hiện trên đường truyền, điều đó chứng minh chắc chắn **có ít nhất 2 bên đang cùng nắm giữ token này** (một bên là người dùng thật đã đổi sang `Token_2`, một bên là kẻ trộm đang cố dùng lại `Token_1`).
- **Phản ứng dứt khoát**: Hệ thống không chỉ từ chối request của kẻ trộm, mà lập tức **thu hồi sạch toàn bộ Refresh Token của tài khoản đó trên mọi thiết bị (`revokeAllRefreshTokensForUser`)**.
- **Kết quả**: Cả kẻ trộm lẫn người dùng hợp lệ đều bị ngắt phiên ngay lập tức. Người dùng hợp lệ thấy mình bị đăng xuất sẽ nhận thức được có bất thường và đăng nhập lại bằng mật khẩu để đổi thông tin, ngăn chặn triệt để kẻ gian tiếp cận dữ liệu.

---

#### 4. Đánh Đổi Kỹ Thuật (Trade-Offs) Cần Nắm Rõ Khi Đi Phỏng Vấn

Khi người phỏng vấn hỏi: *"Khi đổi mật khẩu, bị Admin Ban hoặc khi bẫy trộm token kích hoạt, liệu kẻ xấu có bị chặn ngay lập tức 100% không?"*

👉 **Câu trả lời chuẩn Senior**:
- `revokeAllRefreshTokensForUser` chỉ thu hồi được **toàn bộ Refresh Token**. Kẻ tấn công sẽ **không bao giờ có thể refresh để lấy token mới được nữa**.
- Tuy nhiên, **Access Token hiện tại mà kẻ xấu đang cầm vẫn sống nốt tối đa 15 phút** (vì Access Token là Stateless, server không duy trì danh sách tất cả các token đang lưu hành của một user để đưa hết vào blacklist).
- **Lý do đánh đổi**: Nếu mỗi request API nghiệp vụ đều phải kiểm tra trạng thái user trong Database để đảm bảo thu hồi tức thì từng micro-giây, toàn bộ lợi thế về hiệu năng (Zero DB I/O) của kiến trúc Stateless JWT sẽ bị phá hủy. Khoảng thời gian rủi ro tối đa 15 phút là sự đánh đổi chấp nhận được trong kiến trúc phân tán hiện đại.
