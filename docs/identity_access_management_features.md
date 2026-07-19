# Module 1: Identity & Access Management — Mô tả Chức năng Nghiệp vụ

Tài liệu tổng hợp mô tả các chức năng nghiệp vụ của phân hệ **Quản lý Danh tính & Quyền truy cập (IAM)**. Module này quản lý toàn bộ vòng đời tài khoản người dùng: từ đăng ký, xác thực OTP, đăng nhập (cục bộ & Google OAuth2), quản lý phiên đăng nhập, khôi phục/đổi mật khẩu, đến yêu cầu xóa tài khoản và ẩn danh hóa tự động — tất cả đều tuân thủ tiêu chuẩn bảo mật GDPR.

---

## Mục lục

1. [Đăng ký Tài khoản & Xác thực OTP](#1-đăng-ký-tài-khoản--xác-thực-otp)
2. [Đăng nhập (Local & Google OAuth2)](#2-đăng-nhập-local--google-oauth2)
3. [Gia hạn Phiên Đăng nhập (Silent Refresh)](#3-gia-hạn-phiên-đăng-nhập-silent-refresh)
4. [Đăng xuất & Vô hiệu hóa Token](#4-đăng-xuất--vô-hiệu-hóa-token)
5. [Quên Mật khẩu & Đổi Mật khẩu](#5-quên-mật-khẩu--đổi-mật-khẩu)
6. [Thông tin Cá nhân & Yêu cầu Xóa Tài khoản](#6-thông-tin-cá-nhân--yêu-cầu-xóa-tài-khoản)
7. [Quản lý Phiên Hoạt động](#7-quản-lý-phiên-hoạt-động)
8. [Ẩn danh hóa Tài khoản Tự động (30 ngày)](#8-ẩn-danh-hóa-tài-khoản-tự-động-30-ngày)

---

## 1. Đăng ký Tài khoản & Xác thực OTP

### Đối tượng sử dụng
Khách vãng lai muốn trở thành **Producer** trên hệ thống PWB MiNi.

### Mô tả chức năng
Cho phép người dùng đăng ký tài khoản mới thông qua form thông tin cá nhân. Sau khi đăng ký, hệ thống gửi mã OTP 6 chữ số qua email để kích hoạt tài khoản. Toàn bộ quy trình gửi email được đảm bảo tính nhất quán tuyệt đối giữa cơ sở dữ liệu và hàng đợi tin nhắn thông qua cơ chế **Transactional Outbox**.

### Quy trình nghiệp vụ

#### Giai đoạn 1: Đăng ký & Gửi OTP
1. Người dùng điền thông tin đăng ký: Username, Email, Password, Confirm Password, FullName.
2. Hệ thống kiểm tra tính hợp lệ dữ liệu và lọc email tạm thời (disposable email).
3. Kiểm tra trùng lặp username/email với tài khoản đang hoạt động.
4. Xử lý tài khoản chưa kích hoạt trùng lặp (xem ràng buộc bên dưới).
5. Băm mật khẩu bằng BCrypt và sinh OTP 6 chữ số ngẫu nhiên.
6. Lưu tài khoản mới ở trạng thái `PENDING_VERIFICATION` và ghi sự kiện gửi OTP vào bảng Outbox trong cùng một giao dịch.
7. Sau khi giao dịch thành công → lưu OTP vào cache (5 phút) kèm cooldown (60 giây) → đẩy sự kiện sang Kafka → Mail Worker gửi email chứa mã OTP.
8. Phản hồi thành công cho Frontend, chuyển người dùng sang giao diện nhập OTP.

#### Giai đoạn 2: Xác thực kích hoạt tài khoản
1. Người dùng nhập 6 chữ số OTP nhận từ email.
2. Hệ thống kiểm tra số lần nhập sai (tối đa 5 lần), đối khớp OTP.
3. Xác thực thành công → kích hoạt tài khoản (`ACTIVE`) → sinh cặp Access Token & Refresh Token → tự động đăng nhập.
4. Điều hướng người dùng vào Dashboard.

#### Giai đoạn 3: Gửi lại mã OTP
1. Người dùng nhấn "Gửi lại mã" khi hết cooldown 60 giây.
2. Hệ thống kiểm tra tài khoản hợp lệ, kiểm tra cooldown chưa hết.
3. Sinh OTP mới, xóa bộ đếm nhập sai cũ, lưu OTP mới vào cache, gửi email qua Outbox + Kafka.
4. Frontend reset giao diện nhập và bắt đầu đếm ngược 60 giây mới.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Username | 3-50 ký tự, chỉ gồm chữ cái, số và dấu gạch dưới |
| Email | Đúng định dạng RFC 5322, tự động chuẩn hóa chữ thường |
| Lọc email tạm | Từ chối tên miền email rác (tempmail, yopmail, mailinator...) |
| Mật khẩu | 8-100 ký tự, tối thiểu 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt |
| OTP hợp lệ | 5 phút |
| Cooldown gửi lại OTP | 60 giây giữa 2 lần gửi |
| Giới hạn nhập sai OTP | 5 lần, vượt quá → hủy OTP, bắt buộc gửi mã mới |
| Tài khoản chưa xác thực không được dùng API nghiệp vụ | Chỉ được tương tác API xác thực OTP, resend OTP, logout |
| Dọn dẹp tài khoản rác | Job chạy mỗi giờ, xóa cứng tài khoản `PENDING_VERIFICATION` quá 24 giờ |

### Xử lý tài khoản chưa xác thực trùng lặp

| Trường hợp | Xử lý |
|:---|:---|
| Tài khoản cũ tạo **> 5 phút** trước | Ghi đè thông tin mới lên bản ghi cũ (giữ nguyên ID), sinh OTP mới |
| Tài khoản cũ tạo **≤ 5 phút** trước | Chặn đăng ký, trả lỗi `REGISTRATION_IN_PROGRESS` (chống DoS ghi đè) |
| Hai request đồng thời (Race Condition) | Sử dụng khóa bi quan `SELECT ... FOR UPDATE` để tuần tự hóa |

### Cơ chế đảm bảo gửi email (Transactional Outbox)

Hệ thống kết hợp 3 tuyến gửi email để đảm bảo sự kiện không bao giờ bị thất lạc:

| Tuyến | Mô tả | Ưu tiên |
|:---|:---|:---|
| **Tức thời (After Commit)** | Gửi ngay sau khi giao dịch DB thành công | Chính — phản hồi nhanh nhất |
| **Debezium CDC** | Lắng nghe WAL stream của PostgreSQL, phát hiện INSERT Outbox → gửi Kafka theo thời gian thực | Dự phòng tuyến 1 |
| **Polling Scheduler (30 giây)** | Quét bảng Outbox tìm sự kiện còn sót do server sập | Dự phòng cuối cùng |

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Kiểm tra username | 20 requests / phút / IP |
| Đăng ký | 5 requests / phút / IP |
| Xác thực OTP | 10 requests / phút / IP |
| Gửi lại OTP | 3 requests / phút / IP |

---

## 2. Đăng nhập (Local & Google OAuth2)

### Đối tượng sử dụng
Người dùng đã có tài khoản trên hệ thống (User, User Pro, Admin).

### Mô tả chức năng
Hỗ trợ hai phương thức đăng nhập:
- **Đăng nhập cục bộ (Local)**: Nhập Username/Email + Mật khẩu.
- **Đăng nhập Google (OAuth2)**: Xác thực qua Google SDK, tự động liên kết hoặc tạo mới tài khoản.

### Quy trình đăng nhập cục bộ
1. Người dùng nhập Username/Email và mật khẩu.
2. Hệ thống tìm tài khoản trong DB → kiểm tra khóa tạm thời (lockout) → kiểm tra trạng thái tài khoản → so khớp mật khẩu BCrypt.
3. Đúng mật khẩu → xóa bộ đếm sai → kiểm tra giới hạn 3 phiên đồng thời → sinh cặp Access Token & Refresh Token → trả kết quả.
4. Xử lý ngầm (bất đồng bộ): phân tích GeoIP, phát hiện đăng nhập bất thường, gửi email cảnh báo nếu cần.

### Quy trình đăng nhập Google
1. Frontend hiển thị popup Google → nhận `idToken` → gửi lên Backend.
2. Backend xác thực idToken (issuer, audience, expiration, email_verified, chữ ký số).
3. Xử lý liên kết tài khoản (xem bảng bên dưới).
4. Kiểm tra lockout, trạng thái tài khoản → cấp token → trả kết quả.

### Kiểm soát trạng thái tài khoản khi đăng nhập

| Trạng thái | Hành vi |
|:---|:---|
| `ACTIVE` | Đăng nhập bình thường, truy cập đầy đủ |
| `PENDING_VERIFICATION` | Từ chối, điều hướng sang trang xác thực OTP |
| `BANNED` | Từ chối vĩnh viễn |
| `PENDING_DELETION` | Cho phép đăng nhập, ép điều hướng sang trang khôi phục tài khoản (chỉ được Hủy xóa hoặc Đăng xuất) |

### Cơ chế chống Brute-force (Account Lockout)

| Quy tắc | Chi tiết |
|:---|:---|
| Số lần thử tối đa | 5 lần nhập sai liên tiếp |
| Khóa định danh | Dùng `userId` duy nhất (chống bypass bằng xen kẽ Username/Email) |
| Thời gian khóa | 15 phút |
| Bảo vệ CPU | Kiểm tra lockout **trước** khi chạy BCrypt (tránh DoS qua hàm băm nặng) |

### Cơ chế liên kết tài khoản Google

| Trường hợp | Xử lý |
|:---|:---|
| Email chưa tồn tại | Tạo tài khoản mới (`ACTIVE`, password=null, username tự sinh từ email) |
| Email trùng tài khoản Local đang `ACTIVE` | Liên kết Google OAuth vào tài khoản cũ, đồng bộ avatar, cho phép đăng nhập cả 2 cách |
| Email trùng tài khoản Local đang `PENDING_VERIFICATION` | Liên kết + kích hoạt trực tiếp (giữ nguyên ID, xóa mật khẩu, dọn OTP Redis) |
| Tài khoản bị `BANNED` | Từ chối (không bypass BANNED qua Google) |
| Tài khoản đang lockout | Từ chối (không bypass lockout qua Google) |

### Giới hạn phiên đồng thời

| Quy tắc | Chi tiết |
|:---|:---|
| Tối đa phiên song song | 3 thiết bị / tài khoản |
| Vượt giới hạn | Tự động đá phiên cũ nhất (thiết bị cũ tự logout khi Access Token hết hạn) |
| Tính nguyên tử | Kiểm tra + đá phiên + thêm phiên mới đóng gói trong Redis Lua Script |

### Phát hiện đăng nhập bất thường (Anomalous Login Detection)
- Xử lý hoàn toàn **bất đồng bộ** (không làm chậm phản hồi cho người dùng).
- Phân tích vị trí địa lý qua GeoIP MaxMind, so sánh với lần đăng nhập trước.
- Nếu phát hiện thiết bị mới hoặc vị trí bất thường → gửi email cảnh báo bảo mật kèm nút "Khóa tài khoản ngay".

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Đăng nhập Local | 10 requests / phút / IP |
| Đăng nhập Google | 10 requests / phút / IP |

---

## 3. Gia hạn Phiên Đăng nhập (Silent Refresh)

### Đối tượng sử dụng
Người dùng đã đăng nhập có Access Token hết hạn và Refresh Token còn hiệu lực.

### Mô tả chức năng
Khi Access Token (JWT, 15 phút) hết hạn, hệ thống tự động âm thầm gia hạn phiên bằng cách gửi Refresh Token để nhận cặp token mới. Người dùng không nhận biết bất kỳ sự gián đoạn nào. Mỗi lần gia hạn, Refresh Token cũ bị hủy và thay thế bằng token mới (**Rotation**).

### Quy trình nghiệp vụ
1. Frontend gửi API yêu cầu dữ liệu → nhận lỗi 401 (JWT hết hạn).
2. Axios Interceptor tự động tạm dừng các request khác, gửi `POST /auth/refresh` kèm Access Token cũ (đã hết hạn) trong Header và Refresh Token trong Cookie.
3. Backend xác thực: kiểm tra chữ ký JWT (chỉ bỏ qua lỗi hết hạn), đối khớp userId giữa JWT và Refresh Token.
4. Sinh cặp token mới, xoay vòng Refresh Token (hủy cũ, cấp mới).
5. Frontend nhận token mới, cập nhật Zustand Store, gửi lại request ban đầu.

### Cơ chế ân hạn (Grace Period — 10 giây)

Khi mạng chập chờn gây nhiều request refresh đồng thời:

| Giai đoạn | Mô tả |
|:---|:---|
| Request đầu tiên | Xoay vòng thành công, token cũ chuyển thành **Shadow Key** (lưu trữ token mới) trong 10 giây |
| Request song song (trong 10 giây) | Nhận diện Shadow Key → trả về cùng cặp token mới đã sinh trước đó |
| Sau 10 giây | Shadow Key tự hủy → token cũ chuyển vào danh sách đen (Revoked List) |

### Phát hiện chiếm đoạt Token (Token Theft Detection)

| Thứ tự kiểm tra | Hành vi |
|:---|:---|
| **1. Token Active** | Còn hiệu lực → xoay vòng bình thường |
| **2. Shadow Key** (ưu tiên) | Tìm thấy → trả kết quả cũ (Grace Period) |
| **3. Revoked Key** | Tìm thấy → **Token Theft!** → hủy toàn bộ phiên mọi thiết bị, buộc đăng nhập lại |
| **4. Không tìm thấy** | Token không tồn tại → từ chối |

### Bảo mật chống CSRF (Dual-Token Validation)
- Frontend **bắt buộc** gửi kèm Access Token cũ (đã hết hạn) trong Header `Authorization`.
- Backend so khớp userId từ JWT với userId liên kết Refresh Token → chống CSRF hoàn toàn.

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Refresh Token | 20 requests / phút / IP |

---

## 4. Đăng xuất & Vô hiệu hóa Token

### Đối tượng sử dụng
Người dùng đã đăng nhập muốn thoát khỏi hệ thống.

### Mô tả chức năng
Cho phép người dùng đăng xuất an toàn. Hệ thống vô hiệu hóa đồng thời cả Access Token (đưa vào danh sách đen) và Refresh Token (xóa khỏi cache), đảm bảo không có token nào còn hiệu lực sau khi thoát.

### Quy trình nghiệp vụ
1. Người dùng nhấn "Đăng xuất".
2. Frontend gửi `POST /auth/logout` kèm Access Token (Header) và Refresh Token (Cookie).
3. Backend:
   - Trích xuất chữ ký JWT → đưa vào danh sách đen với TTL động (thời gian còn lại + buffer 15-30 giây chống lệch đồng hồ server).
   - Xóa Refresh Token khỏi cache.
   - Cập nhật danh sách phiên của người dùng.
   - Gửi lệnh xóa Cookie qua header `Set-Cookie` (Max-Age=0).
4. Frontend xóa token trong bộ nhớ Zustand, reset state, broadcast logout qua BroadcastChannel (đồng bộ các tab khác), điều hướng về `/login`.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Cho phép JWT hết hạn | API Logout chấp nhận JWT đã hết hạn (chỉ lấy thông tin, không reject) |
| Idempotent Logout | Nếu cookie Refresh Token đã bị xóa trước đó → bỏ qua, chỉ blacklist JWT |
| Danh sách đen JWT | Lưu chữ ký (Signature) thay vì toàn bộ JWT → tiết kiệm RAM |
| TTL động + Buffer | Bù lệch đồng hồ giữa các node server (15-30 giây) |
| Đăng xuất cưỡng chế phía Client | Nếu API lỗi hoặc mất mạng → Frontend bắt buộc xóa sạch state cục bộ và về `/login` |
| Đồng bộ Multi-tab | Broadcast event logout qua `BroadcastChannel` để tất cả tab đều logout |

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Đăng xuất | 20 requests / phút / IP |

---

## 5. Quên Mật khẩu & Đổi Mật khẩu

### Đối tượng sử dụng
- **Quên mật khẩu**: Người dùng không nhớ mật khẩu, cần khôi phục qua email.
- **Đổi mật khẩu**: Người dùng đang đăng nhập muốn đổi mật khẩu định kỳ.

### Mô tả chức năng
Quên mật khẩu: Gửi link khôi phục qua email chứa Reset Token (UUID, 10 phút), người dùng click link đặt mật khẩu mới. Đổi mật khẩu: Nhập mật khẩu cũ + mới, hệ thống đối khớp và cập nhật. Cả hai đều cưỡng chế thoát phiên trên các thiết bị khác sau khi hoàn tất.

### Quy trình Quên mật khẩu
1. Người dùng nhập email → nhấn "Khôi phục mật khẩu".
2. Hệ thống **luôn trả về cùng một phản hồi** bất kể email có tồn tại hay không (chống dò quét tài khoản).
3. Nếu email hợp lệ: sinh Reset Token (UUID, TTL 10 phút), lưu cache, gửi email chứa link khôi phục qua Outbox + Kafka.
4. Người dùng click link → nhập mật khẩu mới → hệ thống xác thực token, băm mật khẩu mới, cập nhật DB.
5. Xóa Reset Token, xóa khóa lockout (cho phép đăng nhập ngay), hủy toàn bộ phiên trên mọi thiết bị.

### Quy trình Đổi mật khẩu
1. Người dùng đang đăng nhập → nhập mật khẩu cũ + mật khẩu mới + xác nhận.
2. Hệ thống đối khớp mật khẩu cũ (BCrypt), kiểm tra mật khẩu mới khác mật khẩu cũ.
3. Cập nhật mật khẩu mới → hủy phiên trên các thiết bị khác (giữ lại phiên hiện tại) → xóa bộ đếm lockout.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Phòng chống dò quét email | Phản hồi giống nhau cho mọi email (tồn tại hay không) |
| Phòng chống Timing Attack | Giả lập thời gian xử lý ở nhánh email không tồn tại (Response Time Flattener) |
| Reset Token | UUID, TTL 10 phút, xóa sau khi dùng thành công (chỉ xóa sau khi DB commit) |
| Tài khoản OAuth-only | Không được phép đổi mật khẩu (password = null) |
| Mật khẩu mới khác cũ | Bắt buộc khi đổi mật khẩu |
| Cưỡng chế thoát phiên | Quên MK → hủy tất cả phiên; Đổi MK → hủy phiên thiết bị khác, giữ phiên hiện tại |
| Dọn dẹp Lockout | Sau khi đặt lại mật khẩu → xóa khóa lockout và bộ đếm, cho phép đăng nhập ngay |

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Yêu cầu khôi phục (Forgot) | 3 requests / phút / IP |
| Đặt lại mật khẩu (Reset) | 5 requests / phút / IP |
| Đổi mật khẩu (Change) | 5 requests / phút / IP |

---

## 6. Thông tin Cá nhân & Yêu cầu Xóa Tài khoản

### Đối tượng sử dụng
Người dùng đã đăng nhập (ACTIVE).

### Mô tả chức năng

#### Quản lý hồ sơ cá nhân
- Xem thông tin: Username, Email, FullName, Role, Status, AvatarUrl, Phone.
- Chỉnh sửa: chỉ được phép sửa **FullName**, **Phone**, **AvatarUrl**. Các trường định danh cốt lõi (username, email, role, status) không được phép chỉnh sửa.

#### Yêu cầu xóa tài khoản (Soft Delete & 30-day Grace Period)
Cho phép người dùng yêu cầu xóa tài khoản với cơ chế đóng băng 30 ngày, bảo vệ khỏi việc vô tình xóa hoặc bị phá hoại.

### Quy trình Yêu cầu xóa tài khoản
1. Người dùng nhấn "Xóa tài khoản" → nhập xác nhận.
2. **Xác thực lại bắt buộc**: nhập mật khẩu (tài khoản Local) hoặc re-auth Google SDK (tài khoản OAuth).
3. Hệ thống chuyển trạng thái sang `PENDING_DELETION`, ghi mốc thời gian `deletion_requested_at`.
4. Thu hồi toàn bộ phiên đăng nhập trên mọi thiết bị, dọn dẹp metadata cache.
5. Gửi email xác nhận lịch xóa (30 ngày) qua Outbox + Kafka.
6. Phát sự kiện Soft-Hide tới các module khác (Music, Live) để ẩn tạm thời dữ liệu công khai.
7. Frontend xóa state, điều hướng về `/login`.

### Cơ chế đóng băng 30 ngày

| Hành động | Kết quả |
|:---|:---|
| Đăng nhập lại trong 30 ngày | Cho phép đăng nhập, ép sang trang khôi phục (chỉ 2 lựa chọn: Hủy xóa hoặc Đăng xuất) |
| Nhấn "Hủy yêu cầu xóa" | Tài khoản khôi phục `ACTIVE`, dữ liệu công khai hiện lại bình thường |
| Quá 30 ngày | Background Job tự động ẩn danh hóa vĩnh viễn (xem mục 8) |

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Xác thực lại bắt buộc | Chống XSS/Session Hijacking: phải nhập lại mật khẩu hoặc re-auth Google |
| Chống Brute-force mật khẩu | Sai mật khẩu khi xác thực lại → tăng bộ đếm + kích hoạt lockout (như luồng đăng nhập) |
| Ẩn dữ liệu công khai | Ngay khi `PENDING_DELETION` → Soft-Hide nhạc demo, phòng live khỏi trang công khai |
| Tách biệt Module | IAM không truy cập DB module khác; phát sự kiện Kafka để module chuyên trách tự xử lý |
| Thu hồi phiên toàn bộ | Xóa tất cả Refresh Token, metadata phiên, thông tin đăng nhập gần nhất trên cache |

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Lấy hồ sơ cá nhân | 60 requests / phút / IP |
| Cập nhật hồ sơ | 10 requests / phút / IP |
| Xóa tài khoản | 2 requests / phút / IP |

---

## 7. Quản lý Phiên Hoạt động

### Đối tượng sử dụng
Người dùng đã đăng nhập (ACTIVE).

### Mô tả chức năng
Cho phép người dùng tự theo dõi danh sách thiết bị đang online và chủ động đăng xuất từ xa bất kỳ thiết bị nào, hoặc đăng xuất toàn bộ thiết bị khác chỉ giữ lại phiên hiện tại.

### Ba thao tác chính

#### A. Xem danh sách phiên
- Hiển thị tất cả phiên đang hoạt động với thông tin: Thiết bị/Trình duyệt, Địa chỉ IP, Vị trí (GeoIP), Thời điểm đăng nhập.
- Đánh dấu phiên hiện tại (*Thiết bị này*).

#### B. Đăng xuất một thiết bị cụ thể
1. Người dùng nhấn "Đăng xuất" cạnh thiết bị lạ.
2. Hệ thống kiểm tra thiết bị thuộc quyền sở hữu của người dùng.
3. Xóa Refresh Token và metadata của phiên đó.
4. Đưa Access Token (chữ ký JWT) vào danh sách đen → thiết bị bị chặn truy cập ngay lập tức.

#### C. Đăng xuất tất cả thiết bị khác
1. Người dùng nhấn "Đăng xuất tất cả thiết bị khác".
2. Hệ thống sử dụng Redis Lua Script nguyên tử: quét danh sách phiên, lọc bỏ phiên hiện tại, xóa token & metadata của tất cả phiên khác, thu thập chữ ký JWT để blacklist.
3. Chỉ giữ lại duy nhất phiên đang sử dụng.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Chặn tự hủy phiên hiện tại | Không được phép đăng xuất thiết bị đang sử dụng (trả lỗi `CANNOT_REVOKE_CURRENT_SESSION`) |
| Vô hiệu hóa tức thì | Đưa chữ ký JWT vào blacklist → thiết bị bị đá ngay, không cần đợi token hết hạn |
| Kế thừa metadata khi xoay vòng | Khi Silent Refresh xoay vòng token → RENAME metadata key (tránh hiển thị "Unknown") |
| Cập nhật chữ ký JWT | Mỗi khi Login/Refresh cấp Access Token mới → cập nhật `active_jwt_signature` trong metadata |
| Tính nguyên tử | Lua Script đảm bảo xóa hàng loạt không bị Race Condition |

### Giới hạn tần suất API

| API | Giới hạn |
|:---|:---|
| Xem danh sách phiên | 30 requests / phút / IP |
| Xóa phiên cụ thể | 10 requests / phút / IP |
| Xóa tất cả phiên khác | 5 requests / phút / IP |

---

## 8. Ẩn danh hóa Tài khoản Tự động (30 ngày)

### Đối tượng sử dụng
Hệ thống (Background Job tự động).

### Mô tả chức năng
Tiến trình nền chạy tự động hàng ngày, quét các tài khoản `PENDING_DELETION` đã quá 30 ngày đóng băng để thực hiện ẩn danh hóa vĩnh viễn — tuân thủ tiêu chuẩn bảo mật GDPR (Quyền được lãng quên).

### Nguyên tắc xử lý

| Nguyên tắc | Chi tiết |
|:---|:---|
| Không xóa cứng | Giữ nguyên bản ghi để bảo toàn khóa ngoại (lịch sử mua bán, thanh toán, thống kê) |
| Ẩn danh hóa PII | Ghi đè thông tin cá nhân bằng giá trị ngẫu nhiên/null, không thể truy vết ngược |
| Cho phép tái ký | Email/Username cũ được giải phóng, người dùng mới có thể đăng ký lại |

### Quy trình ẩn danh hóa (cho mỗi tài khoản)

1. **Hủy phiên**: Xóa toàn bộ Refresh Token, metadata phiên, thông tin đăng nhập gần nhất, khóa lockout/attempts trên cache.
2. **Ẩn danh hóa DB**:
   - `username` → `deleted_user_{userId}`
   - `email` → `deleted_{userId}@pwbmini.com`
   - `password`, `fullName`, `phone`, `avatarUrl`, `oauth_provider`, `oauth_id` → `null`
   - `status` → `DELETED`, `deleted` → `true`
3. **Phát sự kiện `ACCOUNT_ANONYMIZED`** sang Kafka → các module Music/Live tự dọn dẹp (xóa file S3, hard delete dữ liệu liên quan).

### Ràng buộc vận hành

| Quy tắc | Chi tiết |
|:---|:---|
| Lịch chạy | Cron hàng ngày (ví dụ: 02:00 AM) |
| Chạy trên 1 instance duy nhất | Sử dụng ShedLock (Redis) để điều phối cluster |
| Khóa tối đa | 10 phút (tự giải phóng nếu instance crash) |
| Khóa tối thiểu | 30 giây (chống Clock Skew kích hoạt lại Job trên node khác) |
| Batch size | Tối đa 100 users/batch (tránh vượt ShedLock TTL) |
| Tách biệt Module | Job chỉ ẩn danh hóa bảng `users`; phát sự kiện Kafka để module khác tự dọn dẹp |
| Kích hoạt thủ công | Admin có thể trigger Job qua API nội bộ khi cần |

---

## Tổng quan Luồng Vòng đời Tài khoản

```
Khách vãng lai → Đăng ký (1) → Xác thực OTP → ACTIVE
                                                  ↓
                                 Đăng nhập Local/Google (2)
                                                  ↓
                         Sử dụng hệ thống ← Silent Refresh (3)
                                                  ↓
                              Đăng xuất (4) / Đổi mật khẩu (5)
                                                  ↓
                          Yêu cầu xóa tài khoản (6) → PENDING_DELETION
                                                          ↓
                                          ┌─ Đăng nhập lại trong 30 ngày → Khôi phục → ACTIVE
                                          └─ Quá 30 ngày → Ẩn danh hóa (8) → DELETED (vĩnh viễn)
```

---

## Hệ thống Phân quyền (Role-Based Access)

| Vai trò | Mô tả | Quyền hạn nổi bật |
|:---|:---|:---|
| `ROLE_USER` | Mặc định khi đăng ký thành công | Xem/Nghe nhạc, tham gia Live Room |
| `ROLE_USER_PRO` | Nâng cấp (Producer) sau khi mua gói dịch vụ | Tạo Live Room, Upload nhạc demo, Voice Tag |
| `ROLE_ADMIN` | Quản trị viên tối cao | Kiểm soát và điều phối toàn hệ thống |

---

## Cơ chế Bảo mật Token

| Loại Token | Định dạng | TTL | Lưu trữ Client | Mô tả |
|:---|:---|:---|:---|:---|
| Access Token | JWT | 15 phút | Zustand Store (in-memory) | Phi trạng thái, chứa userId và role |
| Refresh Token | UUID | 7 ngày | HttpOnly Cookie (Secure, SameSite=Strict) | Dùng để gia hạn phiên, không thể truy cập từ JavaScript |

---

## Tổng hợp Giới hạn & Quota

| Hạng mục | Giới hạn |
|:---|:---|
| Phiên đăng nhập đồng thời / tài khoản | 3 thiết bị |
| OTP hợp lệ | 5 phút |
| Cooldown gửi lại OTP | 60 giây |
| Nhập sai OTP tối đa | 5 lần |
| Nhập sai mật khẩu tối đa | 5 lần liên tiếp |
| Khóa tạm thời khi sai mật khẩu | 15 phút |
| Reset Token (quên mật khẩu) | 10 phút |
| Access Token (JWT) | 15 phút |
| Refresh Token | 7 ngày |
| Grace Period (Silent Refresh) | 10 giây |
| Đóng băng xóa tài khoản | 30 ngày |
| Dọn dẹp tài khoản rác (PENDING) | 24 giờ |
| Ẩn danh hóa tài khoản (PENDING_DELETION) | 30 ngày |
