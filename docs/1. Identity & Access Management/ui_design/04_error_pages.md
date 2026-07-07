# Đặc tả Trang Lỗi & Trạng thái HTTP (Error & Status Pages Design)

Tài liệu này đặc tả thiết kế chi tiết (Wireframe) các trang xử lý lỗi HTTP cho ứng dụng PWB MiNi, bao gồm: 401 Unauthorized, 403 Forbidden, 404 Not Found, và 500 Internal Server Error.

---

## Quy tắc chung (Áp dụng cho tất cả Error Pages)

### Bố cục & Thiết kế
* **Responsive**: Căn giữa nội dung cả Desktop, Tablet và Mobile. Tối đa chiều rộng `480px`.
* **Header tối giản**: Chỉ hiển thị Logo PWB ở phía trên (click → điều hướng về `/`). **Không hiển thị** Navbar đầy đủ, Sidebar, hoặc Footer.
* **Monochrome palette**: Nền `bg-white` / `bg-black` (dark mode). Text đen/trắng. Icon/Illustration monochrome.
* **HTTP Status Code**: Hiển thị mã số lớn (`text-8xl font-bold text-neutral-200`) làm nền trang nhã phía sau nội dung chính.

### Tương tác chung
* **CTA**: Mỗi trang có ít nhất 1 nút hành động chính (primary button).
* **Touch target**: Tối thiểu `44x44px` cho tất cả nút trên mobile.
* **Animation**: Fade-in nhẹ 300ms khi trang load.
* **Khả năng tiếp cận (A11y)**:
  - `<title>` tag phản ánh lỗi: *"401 - Phiên hết hạn | PWB MiNi"*.
  - `<h1>` chứa thông điệp lỗi chính.
  - CTA có `tabindex` và hỗ trợ `Enter` để kích hoạt.

---

## 1. 401 Unauthorized (Phiên đăng nhập hết hạn)

### Kịch bản kích hoạt
- Access Token hết hạn + Silent Refresh (`POST /api/v1/auth/refresh`) thất bại.
- Refresh Token bị thu hồi hoặc hết hạn 7 ngày.
- Phát hiện Token Theft (Reuse Detection).

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                      [Logo PWB]                        |
|                                                        |
|                        401                             |
|                                                        |
|                    [Icon Khóa 🔒]                      |
|                                                        |
|           PHIÊN ĐĂNG NHẬP ĐÃ HẾT HẠN                  |
|                                                        |
|    Phiên làm việc của bạn đã hết hạn hoặc bị           |
|    thu hồi. Vui lòng đăng nhập lại để tiếp tục.       |
|                                                        |
|          [ ĐĂNG NHẬP LẠI ]                             |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả:
- Mã `401` hiển thị lớn, nhạt (`text-neutral-200`) làm nền.
- Nút **"ĐĂNG NHẬP LẠI"** → điều hướng `/login`.
- Tự động xóa Access Token trong Zustand Store và Refresh Token Cookie trước khi hiển thị trang.

---

## 2. 403 Forbidden (Truy cập bị từ chối)

### Kịch bản kích hoạt
- User cố truy cập trang/API yêu cầu role cao hơn (ví dụ: ROLE_USER truy cập Admin panel).
- User `BANNED` cố truy cập resource.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                      [Logo PWB]                        |
|                                                        |
|                        403                             |
|                                                        |
|                   [Icon Cấm ⛔]                        |
|                                                        |
|        BẠN KHÔNG CÓ QUYỀN TRUY CẬP TRANG NÀY          |
|                                                        |
|    Tài khoản của bạn không có đủ quyền hạn để          |
|    xem nội dung này. Nếu bạn cho rằng đây là lỗi,     |
|    vui lòng liên hệ quản trị viên.                    |
|                                                        |
|          [ QUAY VỀ DASHBOARD ]                         |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả:
- Nút **"QUAY VỀ DASHBOARD"** → điều hướng `/dashboard`.
- Nếu user chưa đăng nhập → hiển thị trang 401 thay vì 403.

---

## 3. 404 Not Found (Trang không tồn tại)

### Kịch bản kích hoạt
- URL không khớp với bất kỳ route nào đã định nghĩa.
- Resource đã bị xóa hoặc chưa tồn tại.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                      [Logo PWB]                        |
|                                                        |
|                        404                             |
|                                                        |
|              [Illustration Monochrome]                  |
|           (Hình minh họa trang trống/lost)             |
|                                                        |
|        TRANG BẠN TÌM KIẾM KHÔNG TỒN TẠI               |
|                                                        |
|    Đường dẫn có thể đã bị thay đổi, xóa bỏ hoặc       |
|    chưa bao giờ tồn tại.                              |
|                                                        |
|          [ QUAY VỀ TRANG CHỦ ]                         |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả:
- Illustration monochrome: hình minh họa nghệ thuật kiểu line-art, kích thước `160x160px`.
- Nút **"QUAY VỀ TRANG CHỦ"** → điều hướng `/` (nếu chưa đăng nhập) hoặc `/dashboard` (nếu đã đăng nhập).
- Next.js: sử dụng file `not-found.tsx` tại `app/not-found.tsx`.

---

## 4. 500 Internal Server Error (Lỗi hệ thống)

### Kịch bản kích hoạt
- Backend trả về HTTP 500.
- Exception không được xử lý.
- Lỗi kết nối database, Redis, hoặc Kafka.

### Giao diện Wireframe
```
+--------------------------------------------------------+
|                      [Logo PWB]                        |
|                                                        |
|                        500                             |
|                                                        |
|                [Icon Cảnh báo ⚠]                      |
|                                                        |
|           HỆ THỐNG ĐANG GẶP SỰ CỐ                     |
|                                                        |
|    Chúng tôi đang xử lý vấn đề này. Vui lòng          |
|    thử lại sau ít phút hoặc liên hệ hỗ trợ.           |
|                                                        |
|   [ THỬ LẠI ]      < Liên hệ hỗ trợ >                 |
|                                                        |
+--------------------------------------------------------+
```

### Đặc tả:
- Nút **"THỬ LẠI"** (primary): reload trang hiện tại (`window.location.reload()`).
- Link **"Liên hệ hỗ trợ"** (secondary text): mở `mailto:support@pwbmini.com`.
- Next.js: sử dụng file `error.tsx` tại `app/error.tsx` với `'use client'`.
- Component nhận prop `reset` từ Next.js Error Boundary để thử lại.

---

## 5. Tổng hợp Điều hướng Error Pages

| Mã HTTP | Thông điệp chính | CTA chính | Điều hướng |
| :--- | :--- | :--- | :--- |
| **401** | Phiên đăng nhập đã hết hạn | Đăng nhập lại | `/login` |
| **403** | Không có quyền truy cập | Quay về Dashboard | `/dashboard` |
| **404** | Trang không tồn tại | Quay về Trang chủ | `/` hoặc `/dashboard` |
| **500** | Hệ thống đang gặp sự cố | Thử lại | Reload trang |
