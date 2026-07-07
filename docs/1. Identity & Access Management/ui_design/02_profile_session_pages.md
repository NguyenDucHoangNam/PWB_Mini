# Đặc tả Giao diện Hồ sơ & Quản lý phiên (Profile & Sessions Design)

Tài liệu này đặc tả thiết kế chi tiết (Wireframe), cách hiển thị thích ứng đa thiết bị, và tương tác cảm ứng cho các màn hình: cập nhật hồ sơ cá nhân, đổi mật khẩu, quản lý các thiết bị đang đăng nhập, quy trình xóa tài khoản an toàn, và khôi phục tài khoản đang chờ xóa.

---

## 1. Giao diện Hồ sơ & Đổi mật khẩu (Profile Settings)

### Thiết kế Thích ứng (Responsive Layout)
- **Desktop/Tablet (>1024px)**: Chia làm 2 Panel (Bên trái: Thông tin cá nhân & Đổi Avatar. Bên phải: Form đổi mật khẩu).
- **Mobile (<640px)**: Xếp chồng thành 1 cột đứng (Phần Avatar và Thông tin cá nhân ở trên, Form đổi mật khẩu ở dưới).

### 1.1. Giao diện Thông tin cá nhân (Profile Form)

#### Giao diện Wireframe (Desktop — Panel trái)
```
+--------------------------------------------------+
|  < Quay lại                     CÀI ĐẶT HỒ SƠ   |
|                                                  |
|        +------------------+                      |
|        |                  |                      |
|        |   [Ảnh đại diện] |   [Đổi ảnh]          |
|        |                  |   (Kéo thả hoặc nhấn) |
|        +------------------+                      |
|                                                  |
|  Tên đăng nhập                                   |
|  [ hoangnam511                    ] (Không sửa)  |
|                                                  |
|  Email                                           |
|  [ hoangnam@gmail.com             ] (Không sửa)  |
|                                                  |
|  Vai trò                                         |
|  [ ROLE_USER                      ] (Không sửa)  |
|                                                  |
|  Họ và tên *                                     |
|  [ Nguyễn Đức Hoàng Nam                       ]  |
|                                                  |
|  Số điện thoại (Tùy chọn — 10 chữ số VN)        |
|  [ 0987654321                                 ]  |
|                                                  |
|  [              LƯU THAY ĐỔI                 ]  |
|                                                  |
+--------------------------------------------------+
```

#### Phân loại trường dữ liệu

| Trường | Trạng thái | Kiểu hiển thị | Ghi chú |
| :--- | :--- | :--- | :--- |
| Username | **Read-only** | Input disabled, nền `bg-neutral-100` | Không cho phép sửa |
| Email | **Read-only** | Input disabled, nền `bg-neutral-100` | Không cho phép sửa |
| Role | **Read-only** | Input disabled, nền `bg-neutral-100` | Không cho phép sửa |
| Full Name | **Editable** | Input bình thường, viền `border-neutral-300` | Bắt buộc, tối đa 50 ký tự |
| Phone | **Editable** | Input bình thường, viền `border-neutral-300` | Tùy chọn, 10 chữ số VN |
| Avatar | **Editable** | Vùng kéo thả / nhấn chọn file | Tối đa 2MB, .jpg/.jpeg/.png |

#### Giao diện Tải ảnh đại diện (Avatar Upload UX)
- **Kéo thả (Drag & Drop)**: Hỗ trợ vùng nét đứt monochrome để người dùng kéo thả file ảnh từ máy tính vào.
- **Mobile Fallback**: Khi nhấn vào vùng Avatar, hệ thống sẽ mở trình chọn file/camera mặc định của thiết bị di động (tối ưu hóa cảm ứng).
- **Validation Client-side**: Giới hạn file tải lên tối đa là `2MB` và chỉ chấp nhận định dạng `.jpg`, `.jpeg`, `.png`. Hiển thị lỗi tức thời bằng Toast nếu file không hợp lệ mà không cần gửi lên server.
- **Preview trước khi lưu**: Sau khi chọn file hợp lệ, hiển thị ảnh xem trước (thumbnail tròn) thay cho ảnh cũ.

#### Đặc tả Chi tiết tương tác UI/UX:
1. **Loading Skeleton khi tải trang**:
   - Khi đang fetch `GET /api/v1/auth/me`, hiển thị placeholder shimmer:
     - Avatar: hình tròn `bg-neutral-200` nhấp nháy.
     - Các ô text: thanh ngang `bg-neutral-200` nhấp nháy.
2. **Chống Click đúp (Double Submit)**:
   - Nút **"LƯU THAY ĐỔI"** lập tức `disabled = true` kèm Spinner khi submit `PUT /api/v1/auth/profile`.
3. **Validation Client-side**:
   - Full Name: bắt buộc, tối đa 50 ký tự. Nếu để trống: viền xám sẫm + *"Họ và tên là bắt buộc"*.
   - Phone: nếu có giá trị phải đúng định dạng 10 chữ số VN (`0xxxxxxxxx` hoặc `+84xxxxxxxxx`). Nếu sai: *"Số điện thoại không đúng định dạng"*.
4. **Khả năng tiếp cận (A11y)**:
   - Các trường read-only có thuộc tính `aria-readonly="true"` và con trỏ `cursor: not-allowed`.
   - `tabindex` chỉ đặt cho các trường editable: Full Name → Phone → Nút Lưu.

---

### 1.2. Giao diện Đổi mật khẩu (Change Password Form)

#### Giao diện Wireframe (Desktop — Panel phải)
```
+--------------------------------------------------+
|              ĐỔI MẬT KHẨU                        |
|                                                  |
|  Mật khẩu hiện tại *                             |
|  [ **********                              [o] ]  |
|                                                  |
|  Mật khẩu mới * (8+ ký tự, 1 hoa, 1 thường,     |
|                  1 số, 1 ký tự đặc biệt)         |
|  [ **********                              [o] ]  |
|  [===------] Trung bình                          |
|                                                  |
|  Nhập lại mật khẩu mới *                         |
|  [ **********                              [o] ]  |
|                                                  |
|  [              ĐỔI MẬT KHẨU                ]  |
|                                                  |
+--------------------------------------------------+
```

#### Xử lý tài khoản OAuth-only (Ẩn form)
Nếu người dùng đăng nhập hoàn toàn qua Google (`oauthProvider != null && password == null`), toàn bộ panel Đổi mật khẩu được **ẩn hoàn toàn** và thay bằng thông báo:
```
+--------------------------------------------------+
|              ĐỔI MẬT KHẨU                        |
|                                                  |
|  [Icon Google]                                   |
|                                                  |
|  Tài khoản của bạn được liên kết với Google.     |
|  Vui lòng quản lý mật khẩu tại tài khoản         |
|  Google của bạn.                                 |
|                                                  |
+--------------------------------------------------+
```

#### Đặc tả Chi tiết tương tác UI/UX:
1. **Password Strength Indicator**:
   - Thanh đo 4 cấp monochrome (Yếu / Trung bình / Mạnh / Rất mạnh) giống đặc tả tại trang Reset Password.
   - Cập nhật real-time khi gõ vào ô "Mật khẩu mới".
2. **Validation Client-side**:
   - So sánh mật khẩu mới ≠ mật khẩu cũ: nếu trùng, viền xám sẫm + *"Mật khẩu mới không được trùng mật khẩu cũ"*.
   - So khớp xác nhận mật khẩu real-time (onBlur).
3. **Chống Click đúp (Double Submit)**:
   - Nút **"ĐỔI MẬT KHẨU"** lập tức `disabled = true` kèm Spinner.
4. **Phản hồi thành công**:
   - Toast success: *"Đổi mật khẩu thành công. Các thiết bị khác đã bị đăng xuất."*
5. **Phản hồi lỗi**:
   - Sai mật khẩu cũ (`INVALID_OLD_PASSWORD`): viền ô mật khẩu cũ đổi xám sẫm + *"Mật khẩu hiện tại không chính xác"*.
   - OAuth-only (`OAUTH_ONLY_ACCOUNT`): Frontend không bao giờ gửi request nếu đã ẩn form (defense in depth).
6. **Khả năng tiếp cận (A11y)**:
   - `tabindex`: Mật khẩu cũ → Mật khẩu mới → Nhập lại → Nút Đổi.
   - Nút `[o]` ẩn/hiện mật khẩu cho cả 3 ô.
   - `Enter` kích hoạt submit form.

---

## 2. Giao diện Quản lý phiên đăng nhập (Active Sessions)

Đây là chức năng quan trọng cần hiển thị thích ứng rất cao để tránh bị vỡ giao diện trên Mobile.

### Giao diện Desktop & Tablet ngang (>1024px)
Hiển thị dạng bảng (Table) cổ điển, sang trọng:

```
+---------------------------------------------------------------------------------------------------+
| QUẢN LÝ PHIÊN ĐĂNG NHẬP                                                    [ ĐĂNG XUẤT TẤT CẢ KHÁC ]|
|                                                                                                   |
| Thiết bị / Trình duyệt         Địa chỉ IP        Vị trí               Thời gian tạo     Thao tác   |
| ----------------------------  --------------   -------------------   ---------------   ---------  |
| Windows • Chrome (Hiện tại)    115.79.13.20     Hồ Chí Minh, VN       Vừa xong          -          |
| macOS • Safari                14.162.20.10     Hà Nội, VN            2 giờ trước       [Thu hồi]  |
| Android • Chrome              171.244.5.15     Unknown               1 ngày trước      [Thu hồi]  |
+---------------------------------------------------------------------------------------------------+
```

### Giao diện Mobile (<640px)
Tự động chuyển bảng thành dạng **Danh sách Thẻ (Card List)** để vừa với chiều ngang màn hình đứng:

```
+--------------------------------------------------+
| QUẢN LÝ PHIÊN ĐĂNG NHẬP                          |
|                                                  |
| [ ĐĂNG XUẤT TẤT CẢ THIẾT BỊ KHÁC ]               |
|                                                  |
| +----------------------------------------------+ |
| | Windows • Chrome                             | |
| | IP: 115.79.13.20                             | |
| | Vị trí: Hồ Chí Minh, VN                      | |
| | Vừa xong                                     | |
| | [ THIẾT BỊ HIỆN TẠI ] (Không thể thu hồi)     | |
| +----------------------------------------------+ |
|                                                  |
| +----------------------------------------------+ |
| | macOS • Safari                               | |
| | IP: 14.162.20.10                             | |
| | Vị trí: Hà Nội, VN                           | |
| | 2 giờ trước                                  | |
| | [ THU HỒI PHIÊN ]                            | |
| +----------------------------------------------+ |
+--------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Chống click đúp (Double Submit) khi Thu hồi session**:
   - Khi nhấn nút **"Thu hồi phiên"** (hoặc "Đăng xuất tất cả thiết bị khác"), nút đó lập tức chuyển sang trạng thái `disabled = true`, hiển thị Spinner nhỏ thay cho text.
   - Các nút thu hồi của các phiên khác cũng tạm thời bị khóa (`disabled = true`) cho đến khi có phản hồi từ API `DELETE /api/v1/auth/sessions/{tokenUuid}` thành công.
2. **Kích thước nút nhấn**:
   - Nút **"Thu hồi phiên"** dạng thẻ trên mobile được thiết kế với kích thước chiều cao tối thiểu `44px` và font chữ lớn để ngón tay dễ dàng chạm mà không bấm nhầm sang thẻ khác.

---

## 3. Quy trình Xóa tài khoản (Account Deletion Dialog)

Tránh việc người dùng vô tình xóa tài khoản bằng cơ chế xác nhận 2 lớp và độ trễ nút bấm.

### Quy trình hiển thị Đa thiết bị:
* **Desktop/Tablet**: Hiển thị hộp thoại xác nhận (Modal Dialog) căn giữa màn hình, làm mờ nền phía sau.
* **Mobile**: Sử dụng **Bottom Sheet** (bảng kéo từ cạnh dưới màn hình lên chiếm 60% chiều cao màn hình) để người dùng dễ chạm bằng ngón cái và bàn phím ảo không che mất nút hành động.

### 3.1. Giao diện Xóa tài khoản Local (Nhập mật khẩu)
```
+--------------------------------------------------+
|               XÓA TÀI KHOẢN VĨNH VIỄN            |
|                                                  |
|  [!] CẢNH BÁO: Tài khoản của bạn sẽ bị vô hiệu   |
|  hóa lập tức và được xóa hoàn toàn sau 30 ngày.  |
|  Để hủy yêu cầu, chỉ cần đăng nhập lại trước hạn.|
|                                                  |
|  Để xác nhận, vui lòng nhập mật khẩu hiện tại:   |
|  [ nhập mật khẩu của bạn                      ]  |
|                                                  |
|  [        XÁC NHẬN YÊU CẦU XÓA TÀI KHOẢN        ]  |
|  (Nút bấm được kích hoạt sau: 3s...)             |
|                                                  |
|  <Hủy bỏ>                                        |
+--------------------------------------------------+
```

### 3.2. Giao diện Xóa tài khoản Google OAuth (Re-authenticate)
Đối với tài khoản đăng nhập hoàn toàn qua Google (`oauthProvider = 'GOOGLE'`), thay ô nhập mật khẩu bằng nút xác thực lại:
```
+--------------------------------------------------+
|               XÓA TÀI KHOẢN VĨNH VIỄN            |
|                                                  |
|  [!] CẢNH BÁO: Tài khoản của bạn sẽ bị vô hiệu   |
|  hóa lập tức và được xóa hoàn toàn sau 30 ngày.  |
|  Để hủy yêu cầu, chỉ cần đăng nhập lại trước hạn.|
|                                                  |
|  Để xác nhận, vui lòng xác thực lại danh tính:   |
|  [ G  XÁC THỰC LẠI BẰNG GOOGLE              ]  |
|  (i) Đã xác thực thành công ✓                    |
|                                                  |
|  [        XÁC NHẬN YÊU CẦU XÓA TÀI KHOẢN        ]  |
|  (Nút bấm được kích hoạt sau: 3s...)             |
|                                                  |
|  <Hủy bỏ>                                        |
+--------------------------------------------------+
```

#### Luồng xác thực lại Google OAuth:
1. Người dùng nhấn nút **"XÁC THỰC LẠI BẰNG GOOGLE"** → Frontend trigger Google SDK popup/redirect để sinh `idToken` tươi.
2. Khi nhận được `idToken` thành công: hiển thị dấu ✓ xanh *"Đã xác thực thành công"*, nút xóa tài khoản chuyển sang trạng thái sẵn sàng (sau delay 3 giây).
3. Nếu người dùng hủy popup Google hoặc xác thực thất bại: hiển thị Toast lỗi *"Xác thực Google thất bại, vui lòng thử lại"*.
4. Khi nhấn nút xác nhận xóa: Frontend gửi `DELETE /api/v1/auth/account` kèm `idToken` trong request body (thay vì `password`).

### Đặc tả Chi tiết tương tác UI/UX:
1. **Trì hoãn kích hoạt nút xác nhận (Submit delay)**:
   - Khi hộp thoại vừa được mở, nút **"XÁC NHẬN YÊU CẦU XÓA TÀI KHOẢN"** mặc định ở trạng thái khóa `disabled = true`. Dòng chữ đếm ngược hiển thị dưới nút bấm: *"Vui lòng đọc kỹ cảnh báo (kích hoạt sau 3 giây...)"*.
   - Sau 3 giây, nút bấm mới chuyển sang trạng thái sẵn sàng nhập mật khẩu (Local) hoặc chờ xác thực Google (OAuth) rồi mới cho click.
   - UX này bắt buộc người dùng dừng lại đọc cảnh báo nguy hiểm trước khi xóa dữ liệu.
2. **Double Submit Prevention**:
   - Khi nhấn nút xác nhận, nút bị khóa ngay và hiển thị Loading Spinner cho đến khi chuyển hướng người dùng về trang Login thành công.
3. **Phản hồi lỗi**:
   - Sai mật khẩu (`INVALID_PASSWORD`): viền ô mật khẩu đổi xám sẫm + *"Mật khẩu không chính xác. Bạn còn X lần thử."*
   - Tài khoản bị khóa (`ACCOUNT_TEMPORARILY_LOCKED`): Toast error *"Tài khoản tạm thời bị khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút."*
   - Yêu cầu xóa đã tồn tại (`DELETION_ALREADY_REQUESTED`): Toast warning *"Yêu cầu xóa tài khoản đã được gửi trước đó."*

---

## 4. Giao diện Khôi phục tài khoản (Account Recovery Page)

Trang này hiển thị khi người dùng ở trạng thái `PENDING_DELETION` đăng nhập thành công. Hệ thống ép điều hướng đến trang này thay vì Dashboard.

### Bố cục Thích ứng (Responsive Layout)
* **Desktop/Tablet/Mobile**: Trang cô lập, căn giữa toàn bộ nội dung. **Không hiển thị** Navbar/Sidebar/Footer — chỉ hiện Logo PWB ở phía trên.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                                                        |
|                      [Logo PWB]                        |
|                                                        |
|               [Icon Cảnh báo ⚠ lớn]                   |
|                                                        |
|           TÀI KHOẢN ĐANG CHỜ XÓA VĨNH VIỄN            |
|                                                        |
|   Tài khoản của bạn đã được yêu cầu xóa vào ngày     |
|   01/07/2026. Nếu không có hành động khôi phục,       |
|   tài khoản sẽ bị xóa vĩnh viễn sau:                  |
|                                                        |
|                  [ 24 ngày còn lại ]                   |
|                                                        |
|   Để khôi phục tài khoản, nhấn nút bên dưới:          |
|                                                        |
|  [       HỦY YÊU CẦU XÓA & QUAY VỀ DASHBOARD    ]  |
|                                                        |
|  <Đăng xuất>                                          |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Hiển thị thời gian còn lại**:
   - Tính toán từ `deletionRequestedAt + 30 ngày - ngày hiện tại`.
   - Hiển thị số ngày còn lại bằng font lớn, in đậm, monochrome.
   - Nếu còn ≤ 3 ngày: đổi sang font đen đậm để cảnh báo khẩn cấp.
2. **Nút "HỦY YÊU CẦU XÓA & QUAY VỀ DASHBOARD"**:
   - Nút primary, nổi bật, kích thước lớn (chiều cao tối thiểu `48px`).
   - Chống Click đúp: `disabled = true` + Spinner khi đang gọi API.
   - Sau khi thành công: Toast success *"Chào mừng trở lại! Tài khoản đã được khôi phục."* → điều hướng `/dashboard`.
3. **Nút "Đăng xuất"**:
   - Nút secondary, text nhạt (`text-neutral-400`), không nổi bật.
   - Gọi API `POST /api/v1/auth/logout` → điều hướng `/login`.
4. **Chặn điều hướng**:
   - Người dùng `PENDING_DELETION` không được phép truy cập bất kỳ trang nào khác. Mọi attempt điều hướng (nhập URL, nhấn nút Back) đều bị redirect quay lại trang này.
5. **Khả năng tiếp cận (A11y)**:
   - `tabindex`: Nút Khôi phục → Nút Đăng xuất.
   - Touch target tối thiểu `44x44px` cho cả 2 nút trên mobile.
