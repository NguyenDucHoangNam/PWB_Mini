# Đặc tả Giao diện các trang Xác thực (Auth Pages Design)

Tài liệu này đặc tả thiết kế chi tiết (Wireframe), trạng thái Validation, xử lý Tải dữ liệu (Loading) và cơ chế chống nhấn đúp cho các màn hình: Đăng ký, Đăng nhập, Xác thực OTP, và Quên/Đặt lại mật khẩu.

---

## 1. Giao diện Đăng nhập (Login Page)

### Bố cục Thích ứng (Responsive Layout)
* **Desktop (>1024px)**: Chia đôi màn hình (50% trái hiển thị banner hoặc artwork nghệ thuật monochrome, 50% phải chứa form đăng nhập).
* **Tablet (640px - 1024px)**: Form đăng nhập căn giữa màn hình, chiều rộng cố định `450px`.
* **Mobile (<640px)**: Form đăng nhập chiếm toàn bộ chiều rộng màn hình, loại bỏ banner nghệ thuật để tập trung điền dữ liệu.

### Giao diện Wireframe (Mobile & Desktop Form)
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|                   ĐĂNG NHẬP HỆ THỐNG                   |
|       Chào mừng bạn quay lại. Hãy nhập thông tin       |
|                                                        |
|  Tên đăng ký hoặc Email *                              |
|  [ nhập username hoặc email                         ]  |
|                                                        |
|  Mật khẩu *                                            |
|  [ **********                                    [o] ]  |
|  <Quên mật khẩu?>                                      |
|                                                        |
|  [                 ĐĂNG NHẬP                      ]  |
|                                                        |
|  ------------------- HOẶC ---------------------------  |
|                                                        |
|  [ G  Tiếp tục với Google                         ]  |
|                                                        |
|  Chưa có tài khoản? <Đăng ký ngay>                     |
|                                                        |
+--------------------------------------------------------+
```
*(Ghi chú: Nút `[o]` là icon hiển thị/ẩn mật khẩu)*

### Đặc tả Chi tiết tương tác UI/UX:
1. **Chống Click đúp (Double Submit)**:
   - Ngay sau khi nhấn nút **"ĐĂNG NHẬP"**, nút này lập tức chuyển thành `disabled = true`, màu nền đổi thành xám nhạt (`bg-neutral-200`), văn bản đổi thành "Đang đăng nhập..." và hiển thị Spinner xoay mượt mà ở giữa.
   - Nút Google Login cũng đồng thời bị khóa (`disabled = true`) để ngăn người dùng thao tác song song.
   - Nếu sau 10 giây không nhận được kết quả từ máy chủ, nút bấm sẽ tự động được mở khóa lại kèm thông báo lỗi kết nối.
2. **Khả năng tiếp cận (Accessibility - A11y)**:
   - Tất cả các trường nhập liệu có thuộc tính `tabindex`. Phím `Tab` di chuyển từ Username -> Password -> Quên mật khẩu -> Nút Đăng nhập.
   - Nhấn phím `Enter` khi đang ở bất kỳ ô nhập liệu nào sẽ kích hoạt submit form đăng nhập.
3. **Phản hồi lỗi (Error Handling UI)**:
   - Nếu đăng nhập sai mật khẩu (lỗi `BAD_CREDENTIALS`), viền của ô mật khẩu sẽ đổi sang xám sẫm/đỏ nhạt, bên dưới hiển thị thông điệp đỏ nhỏ: *"Mật khẩu không chính xác. Bạn còn 4 lần thử trước khi tài khoản bị khóa 15 phút"*.
   - Hỗ trợ ARIA: Thêm thuộc tính `aria-invalid="true"` vào input bị lỗi.

---

## 2. Giao diện Đăng ký (Register Page)

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                     ĐĂNG KÝ TÀI KHOẢN                  |
|                                                        |
|  Tên hiển thị (Họ và tên) *                            |
|  [ Họ và tên của bạn                                ]  |
|                                                        |
|  Tên đăng nhập * (Chỉ chữ, số, tối thiểu 3 ký tự)      |
|  [ tên đăng nhập mong muốn                          ]  |
|  (i) Đang kiểm tra tên đăng nhập...                    |
|                                                        |
|  Địa chỉ Email *                                       |
|  [ email của bạn                                    ]  |
|                                                        |
|  Mật khẩu * (Tối thiểu 8 ký tự, 1 hoa, 1 thường, 1 số, 1 đặc biệt)  |
|  [ **********                                    [o] ]  |
|                                                        |
|  Nhập lại mật khẩu *                                   |
|  [ **********                                    [o] ]  |
|                                                        |
|  [                 ĐĂNG KÝ                        ]  |
|                                                        |
|  Đã có tài khoản? <Đăng nhập ngay>                     |
+--------------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Debounce Kiểm tra Username trùng lặp**:
   - Khi người dùng gõ vào ô "Tên đăng nhập", hệ thống **chờ 300ms** (không gửi request ngay lập tức sau mỗi phím bấm).
   - Nếu người dùng dừng gõ quá 300ms, hệ thống mới gửi request `GET /api/v1/auth/check-username?username=...`.
   - Trong quá trình kiểm tra, hiển thị dòng text xám nhỏ dưới input: *"Đang kiểm tra..."*.
   - Nếu username hợp lệ -> viền xanh lục nhạt/xám nhạt và dòng chữ: *"Tên đăng nhập khả dụng"*. Nếu trùng lặp -> viền đỏ nhạt và dòng chữ: *"Tên đăng nhập đã được sử dụng"*.
2. **Double Submit Prevention**:
   - Nút **"ĐĂNG KÝ"** bị khóa hoàn toàn (`disabled = true`) khi đang gửi yêu cầu và hiện Spinner.
3. **Bàn phím ảo trên Di động**:
   - Ô nhập Email bắt buộc khai báo `type="email" inputmode="email"` để thiết bị di động tự động hiển thị nút `@` trên bàn phím ảo.

---

## 3. Giao diện Xác thực OTP (OTP Verification Page)

Thiết kế tối ưu cho di động để người dùng dễ dàng nhập mã OTP gồm 6 chữ số gửi qua email.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                     XÁC THỰC MÃ OTP                    |
|       Mã OTP đã được gửi đến email: te**@gmail.com     |
|                                                        |
|     [ 1 ]  [ 2 ]  [ 3 ]  [ 4 ]  [ 5 ]  [ 6 ]           |
|                                                        |
|                     (i) 04:59                          |
|                                                        |
|  [                 XÁC THỰC                       ]  |
|                                                        |
|  Chưa nhận được mã? <Gửi lại OTP> (sau 60s)            |
+--------------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Thiết kế ô nhập OTP (6-Digit Fields)**:
   - Bao gồm 6 ô nhập chữ số riêng biệt.
   - Mỗi ô nhập có thuộc tính `type="text" inputmode="numeric" pattern="[0-9]*"` để điện thoại hiển thị **bàn phím số lớn**.
   - **Tự động chuyển tiêu điểm (Auto-focus & Next)**: Khi người dùng gõ vào ô 1, con trỏ tự động nhảy sang ô 2. Khi nhấn phím `Backspace` (xóa), con trỏ tự động lùi về ô trước đó.
   - Cho phép paste mã OTP (6 chữ số) sao chép từ email vào ô đầu tiên, hệ thống sẽ tự động phân tách và điền vào 6 ô.
2. **Bộ đếm thời gian (Countdown Timer)**:
   - Hiển thị thời gian đếm ngược 5 phút (300 giây) của mã OTP.
   - Nút **"Gửi lại OTP"** mặc định bị tắt (`disabled = true`) kèm nhãn đếm ngược cooldown 60 giây. Chỉ khi hết 60 giây, nút này mới chuyển sang trạng thái kích hoạt để người dùng nhấn gửi lại.
3. **Double Submit Prevention**:
   - Khi điền đủ 6 số hoặc nhấn nút "XÁC THỰC", toàn bộ 6 ô nhập và nút xác thực lập tức bị khóa (`disabled = true`) để gửi API verify lên backend.

---

## 4. Giao diện Quên mật khẩu (Forgot Password Page)

### Bố cục Thích ứng (Responsive Layout)
* **Desktop (>1024px)**: Chia đôi màn hình (50% trái hiển thị banner monochrome, 50% phải chứa form) — đồng nhất với trang Login.
* **Tablet (640px - 1024px)**: Form căn giữa màn hình, chiều rộng cố định `450px`.
* **Mobile (<640px)**: Form chiếm toàn bộ chiều rộng, loại bỏ banner.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|                   QUÊN MẬT KHẨU                        |
|      Nhập email đã đăng ký để nhận liên kết            |
|                khôi phục mật khẩu                      |
|                                                        |
|  Địa chỉ Email *                                       |
|  [ nhập email của bạn                               ]  |
|                                                        |
|  [              GỬI LIÊN KẾT KHÔI PHỤC            ]  |
|                                                        |
|  < Quay về Đăng nhập                                   |
|                                                        |
+--------------------------------------------------------+
```

### Giao diện sau khi gửi thành công (Confirmation Screen)
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|                  [Icon Email ✓]                        |
|                                                        |
|              KIỂM TRA HÒM THƯ CỦA BẠN                 |
|                                                        |
|   Nếu email tồn tại trên hệ thống, hướng dẫn đặt     |
|   lại mật khẩu đã được gửi đến bạn. Vui lòng kiểm    |
|   tra hộp thư đến (và thư rác).                       |
|                                                        |
|  [              QUAY VỀ ĐĂNG NHẬP                 ]  |
|                                                        |
+--------------------------------------------------------+
```
*(Ghi chú: Hiển thị cùng thông điệp bất kể email có tồn tại hay không — Enumeration Defense)*

### Đặc tả Chi tiết tương tác UI/UX:
1. **Chống Click đúp (Double Submit)**:
   - Khi nhấn nút **"GỬI LIÊN KẾT KHÔI PHỤC"**, nút lập tức `disabled = true`, text đổi thành "Đang gửi..." kèm Spinner.
   - Sau khi nhận phản hồi (luôn 200 OK): chuyển sang Confirmation Screen.
   - Nếu sau 10 giây không nhận được phản hồi: mở khóa nút và hiện Toast lỗi kết nối.
2. **Khả năng tiếp cận (A11y)**:
   - `tabindex` theo thứ tự: Email → Nút Gửi → Link Quay về.
   - Nhấn `Enter` khi đang ở ô Email sẽ kích hoạt submit form.
   - `aria-invalid="true"` khi email không đúng định dạng.
3. **Phản hồi lỗi (Error Handling UI)**:
   - Validation client-side: nếu email không đúng định dạng, viền ô email đổi xám sẫm, hiển thị thông điệp dưới input: *"Vui lòng nhập đúng định dạng email"*.
   - Rate limit (HTTP 429): Toast warning *"Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau X giây"*.

---

## 5. Giao diện Đặt lại mật khẩu (Reset Password Page)

Trang này được truy cập từ link trong email: `https://pwbmini.com/reset-password?token={token}`.

### Bố cục Thích ứng (Responsive Layout)
* **Desktop (>1024px)**: Chia đôi màn hình (50% trái banner monochrome, 50% phải chứa form).
* **Tablet (640px - 1024px)**: Form căn giữa, chiều rộng cố định `450px`.
* **Mobile (<640px)**: Form chiếm toàn bộ chiều rộng.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|                 ĐẶT LẠI MẬT KHẨU                      |
|           Nhập mật khẩu mới cho tài khoản              |
|                                                        |
|  Mật khẩu mới * (8+ ký tự, 1 hoa, 1 thường, 1 số,     |
|                  1 ký tự đặc biệt)                     |
|  [ **********                                    [o] ]  |
|  [===------] Trung bình                                |
|                                                        |
|  Nhập lại mật khẩu mới *                               |
|  [ **********                                    [o] ]  |
|                                                        |
|  [              ĐẶT LẠI MẬT KHẨU                 ]  |
|                                                        |
+--------------------------------------------------------+
```

### Thước đo Độ mạnh Mật khẩu (Password Strength Indicator)
Hiển thị thanh 4 cấp monochrome ngay dưới ô mật khẩu mới, cập nhật theo thời gian thực khi người dùng gõ:

| Cấp độ | Điều kiện | Thanh | Màu sắc |
| :--- | :--- | :--- | :--- |
| Yếu | Chỉ có chữ thường | `[==--------]` | `bg-neutral-300` (xám nhạt) |
| Trung bình | Đủ chữ hoa + thường + số | `[=====-----]` | `bg-neutral-400` |
| Mạnh | Đủ chữ hoa + thường + số + đặc biệt | `[========--]` | `bg-neutral-600` |
| Rất mạnh | Đủ tiêu chí + ≥12 ký tự | `[==========]` | `bg-neutral-800` (gần đen) |

### Giao diện khi Token hết hạn / không hợp lệ
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|                [Icon Cảnh báo ⚠]                      |
|                                                        |
|           LIÊN KẾT ĐÃ HẾT HẠN HOẶC KHÔNG HỢP LỆ      |
|                                                        |
|    Liên kết khôi phục mật khẩu đã hết hạn hoặc đã     |
|    được sử dụng. Vui lòng yêu cầu gửi lại.            |
|                                                        |
|  [          GỬI LẠI LIÊN KẾT KHÔI PHỤC           ]  |
|                                                        |
|  < Quay về Đăng nhập                                   |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Kiểm tra Token khi tải trang**:
   - Khi trang vừa mở, Frontend đọc tham số `token` từ URL query string.
   - Nếu `token` rỗng hoặc thiếu: hiển thị ngay màn hình "Token không hợp lệ" mà không cần gọi API.
   - Nếu `token` có giá trị: hiển thị form bình thường (việc kiểm tra hợp lệ token sẽ do Backend xử lý khi submit).
2. **Password Strength Indicator**:
   - Thanh đo cập nhật real-time mỗi khi người dùng gõ vào ô "Mật khẩu mới".
   - Hiển thị nhãn text bên cạnh thanh: *"Yếu"*, *"Trung bình"*, *"Mạnh"*, *"Rất mạnh"*.
3. **So khớp mật khẩu phía Client**:
   - Khi người dùng gõ vào ô "Nhập lại mật khẩu mới", hệ thống kiểm tra real-time:
     - Nếu khớp: viền xám nhạt bình thường.
     - Nếu không khớp và ô đã mất tiêu điểm (onBlur): viền xám sẫm + thông điệp *"Mật khẩu xác nhận không khớp"*.
4. **Chống Click đúp (Double Submit)**:
   - Nút **"ĐẶT LẠI MẬT KHẨU"** lập tức `disabled = true` kèm Spinner khi submit.
5. **Phản hồi sau khi đặt lại thành công**:
   - Hiển thị Toast success: *"Mật khẩu đã được đặt lại thành công. Tất cả phiên đăng nhập đã bị thu hồi."*
   - Điều hướng tự động về `/login` sau 2 giây.
6. **Khả năng tiếp cận (A11y)**:
   - `tabindex`: Mật khẩu mới → Nhập lại → Nút Đặt lại.
   - Nút `[o]` ẩn/hiện mật khẩu cho cả 2 ô.
   - `aria-invalid="true"` khi validation thất bại.

