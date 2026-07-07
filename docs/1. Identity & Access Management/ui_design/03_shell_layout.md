# Đặc tả Shell & Layout Components (Shell & Layout Design)

Tài liệu này đặc tả thiết kế chi tiết (Wireframe) các thành phần khung ứng dụng (Shell Components) bao gồm: Landing Page, Header/Navbar, Footer, hệ thống Toast Notification, và các trạng thái Loading/Empty/Error dùng chung cho toàn bộ ứng dụng PWB MiNi.

---

## 1. Landing Page (Trang chào `/`)

Trang đầu tiên người dùng thấy khi truy cập ứng dụng mà chưa đăng nhập. Nếu đã đăng nhập → tự động redirect sang `/dashboard`.

### Bố cục Thích ứng (Responsive Layout)
* **Desktop (>1024px)**: Hero Section chiếm toàn bộ viewport đầu tiên (100vh), bên dưới cuộn xuống hiển thị Feature Highlights.
* **Tablet (640px - 1024px)**: Cùng cấu trúc, font và khoảng cách thu nhỏ.
* **Mobile (<640px)**: Xếp chồng dọc, ẩn các illustration lớn, tập trung vào nội dung text và CTA.

### Giao diện Wireframe — Hero Section
```
+------------------------------------------------------------------------+
|  [Logo PWB]                           <Tính năng>  [Đăng nhập] [Đăng ký]|
|------------------------------------------------------------------------|
|                                                                        |
|                                                                        |
|                          PWB MiNi                                      |
|                                                                        |
|              Cộng tác Âm thanh Thời gian thực                          |
|          cho Nhà sản xuất Âm nhạc Chuyên nghiệp                       |
|                                                                        |
|     Nền tảng chia sẻ demo bảo mật & phòng nghe trực tuyến             |
|         với đồng bộ playback & thoại WebRTC trễ thấp                   |
|                                                                        |
|     [ BẮT ĐẦU NGAY → ]      < Tìm hiểu thêm ↓ >                      |
|                                                                        |
|                                                                        |
+------------------------------------------------------------------------+
```

### Giao diện Wireframe — Feature Highlights (cuộn xuống)
```
+------------------------------------------------------------------------+
|                                                                        |
|                    TẠI SAO CHỌN PWB MINI?                              |
|                                                                        |
|  +--------------------+  +--------------------+  +--------------------+ |
|  |  [Icon Bảo vệ]     |  |  [Icon Đồng bộ]    |  |  [Icon Bảo mật]    | |
|  |                    |  |                    |  |                    | |
|  |  Bảo vệ bản quyền  |  |  Nghe chung đồng bộ |  |  Quản lý phiên    | |
|  |                    |  |                    |  |  an toàn           | |
|  |  Voice Tag tự động  |  |  WebSocket Playback |  |  Token Rotation   | |
|  |  HLS mã hóa AES-128|  |  WebRTC Voice Chat  |  |  GDPR Compliant   | |
|  |  Link có thời hạn  |  |  Tối đa 7 người    |  |  Ẩn danh hóa 30d  | |
|  +--------------------+  +--------------------+  +--------------------+ |
|                                                                        |
|  ───────────────────────── © PWB MiNi 2026 ──────────────────────────── |
+------------------------------------------------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Thiết kế Monochrome**:
   - Nền trắng (`bg-white`) hoặc đen (`bg-black` dark mode). Typography đen/trắng, gradient xám subtle.
   - CTA **"BẮT ĐẦU NGAY"**: nút primary `bg-black text-white` (light mode) / `bg-white text-black` (dark mode).
2. **Animation nhẹ**:
   - Hero text: fade-in + slide-up 300ms khi trang load.
   - Feature cards: stagger fade-in khi cuộn vào viewport (Intersection Observer).
3. **Điều hướng CTA**:
   - "Bắt đầu ngay" → `/register`.
   - "Đăng nhập" → `/login`.
   - "Tìm hiểu thêm" → scroll smooth xuống phần Feature Highlights.
4. **SEO**:
   - `<h1>`: "PWB MiNi — Cộng tác Âm thanh Thời gian thực cho Nhà sản xuất Âm nhạc".
   - `<meta description>`: "Nền tảng chia sẻ demo bảo mật và phòng nghe trực tuyến với đồng bộ playback và thoại WebRTC trễ thấp."
   - Semantic HTML: `<header>`, `<main>`, `<section>`, `<footer>`.

---

## 2. Header / Navbar

### 2.1. Trạng thái Chưa đăng nhập (Logged-out)

#### Desktop (>1024px) — Sticky top bar
```
+------------------------------------------------------------------------+
|  [Logo PWB]          Trang chủ    Tính năng          [Đăng nhập] [Đăng ký]|
+------------------------------------------------------------------------+
```

#### Mobile (<640px) — Hamburger
```
+--------------------------------------------------+
|  [Logo PWB]                            [☰]       |
+--------------------------------------------------+
```

Khi nhấn `[☰]` → mở **Drawer** trượt từ phải sang trái chiếm 80% chiều rộng:
```
                    +-------------------------------+
                    |                         [✕]  |
                    |                              |
                    |  Trang chủ                    |
                    |  ─────────────────────────    |
                    |  Tính năng                    |
                    |  ─────────────────────────    |
                    |                              |
                    |  [ ĐĂNG NHẬP ]                |
                    |  [ ĐĂNG KÝ ]                 |
                    |                              |
                    +-------------------------------+
```

---

### 2.2. Trạng thái Đã đăng nhập (Logged-in)

#### Desktop (>1024px)
```
+------------------------------------------------------------------------+
|  [Logo PWB]     Dashboard    Live Rooms              [🔔]  [Avatar ▼]  |
+------------------------------------------------------------------------+
```

Khi nhấn `[Avatar ▼]` → mở **Dropdown Menu**:
```
                                        +---------------------------+
                                        |  Nguyễn Đức Hoàng Nam    |
                                        |  hoangnam@gmail.com      |
                                        |  ─────────────────────   |
                                        |  Hồ sơ cá nhân          |
                                        |  Quản lý phiên           |
                                        |  Đổi mật khẩu           |
                                        |  ─────────────────────   |
                                        |  Đăng xuất               |
                                        +---------------------------+
```

#### Mobile (<640px) — Drawer logged-in
```
                    +-------------------------------+
                    |  [Avatar] Hoàng Nam     [✕]  |
                    |  hoangnam@gmail.com           |
                    |  ─────────────────────────    |
                    |  Dashboard                    |
                    |  Live Rooms                   |
                    |  ─────────────────────────    |
                    |  Hồ sơ cá nhân               |
                    |  Quản lý phiên                |
                    |  Đổi mật khẩu                |
                    |  ─────────────────────────    |
                    |  Đăng xuất                    |
                    +-------------------------------+
```

### Đặc tả Chi tiết tương tác UI/UX:
1. **Sticky Header**:
   - Header cố định ở đỉnh viewport khi cuộn trang (`position: sticky; top: 0; z-index: 50`).
   - Chiều cao cố định: Desktop `64px`, Mobile `56px`.
   - Border bottom: `1px solid neutral-200`.
2. **Avatar Dropdown**:
   - Mở: click vào avatar.
   - Đóng: click ngoài vùng dropdown, nhấn `Escape`, hoặc click vào menu item.
   - Animation: fade-in + scale-up 150ms.
3. **Mobile Drawer**:
   - Mở: nhấn `[☰]` hamburger icon.
   - Đóng: nhấn `[✕]`, swipe phải, hoặc click backdrop tối (`bg-black/50`).
   - Animation: slide-in từ phải 200ms.
   - Backdrop tối phủ phần còn lại của màn hình.
4. **Active Page Indicator**:
   - Menu item đang active: text `font-bold text-black` + underline `border-b-2 border-black`.
   - Menu item không active: `text-neutral-500 hover:text-black`.
5. **Ẩn "Đổi mật khẩu" cho OAuth-only**:
   - Nếu user đăng nhập qua Google (`oauthProvider != null && password == null`): ẩn menu item "Đổi mật khẩu".
6. **Khả năng tiếp cận (A11y)**:
   - Dropdown và Drawer có thuộc tính `role="menu"`, `aria-expanded`.
   - Menu items có `role="menuitem"`.
   - Hỗ trợ keyboard navigation: `Arrow Up/Down` di chuyển giữa items, `Enter` chọn, `Escape` đóng.
   - Touch target tối thiểu `44x44px` cho hamburger icon và menu items.

---

## 3. Footer

### Giao diện Desktop (>1024px)
```
+------------------------------------------------------------------------+
|                                                                        |
|  [Logo PWB]              Sản phẩm           Hỗ trợ            Pháp lý  |
|  Play With Beats MiNi    Dashboard          Liên hệ           Điều khoản|
|                          Live Rooms         support@pwbmini.com Bảo mật |
|                          Demo Sharing       FAQ                         |
|                                                                        |
|  ─────────────────────────────────────────────────────────────────────  |
|  © 2026 PWB MiNi. All rights reserved.                                 |
+------------------------------------------------------------------------+
```

### Giao diện Mobile (<640px)
```
+--------------------------------------------------+
|                                                  |
|  [Logo PWB]                                      |
|  Play With Beats MiNi                            |
|                                                  |
|  Sản phẩm                                        |
|  Dashboard • Live Rooms • Demo Sharing           |
|                                                  |
|  Hỗ trợ                                         |
|  support@pwbmini.com                             |
|                                                  |
|  Pháp lý                                        |
|  Điều khoản sử dụng • Chính sách bảo mật        |
|                                                  |
|  ──────────────────────────────────────────────  |
|  © 2026 PWB MiNi. All rights reserved.           |
+--------------------------------------------------+
```

### Đặc tả Chi tiết:
1. **Thiết kế**: Nền `bg-neutral-50` (light) / `bg-neutral-950` (dark). Text `text-neutral-500`. Links hover → `text-black`.
2. **Vị trí**: Luôn ở cuối trang (`mt-auto` trong flex column layout). Không sticky.
3. **Email hỗ trợ**: `support@pwbmini.com` — nhất quán với footer email templates trong spec `01`, `02`, `05`, `06`.
4. **Ẩn Footer trên trang đặc biệt**: Các trang cô lập (Account Recovery, Error Pages) **không hiển thị** Footer.

---

## 4. Hệ thống Thông báo Toast (Toast Notification System)

Dự án sử dụng thư viện **Sonner** (tích hợp sẵn qua shadcn/ui) — xem component wrapper tại `src/components/ui/sonner.tsx`. Các đặc tả dưới đây mô tả cách **cấu hình Sonner**, không phải tự build Toast component.

### Cấu hình `<Toaster />` (trong `sonner.tsx`)

| Prop | Giá trị | Ghi chú |
| :--- | :--- | :--- |
| `position` | `"top-right"` | Desktop: góc trên phải. Sonner tự reposition trên mobile. |
| `visibleToasts` | `3` | Tối đa 3 Toast hiển thị cùng lúc, FIFO. |
| `closeButton` | `true` | Hiển thị nút `✕` đóng sớm trên mỗi Toast. |
| `richColors` | `false` | Giữ monochrome — không dùng màu mặc định (xanh/đỏ) của Sonner. |
| `theme` | Đọc từ `next-themes` | Tự chuyển dark/light theo hệ thống. |
| `gap` | `8` | Khoảng cách giữa các Toast xếp chồng. |

### Icons (Lucide — đã cấu hình sẵn)

| Loại | Lucide Icon | Biến thể |
| :--- | :--- | :--- |
| `success` | `CircleCheckIcon` | `size-4` |
| `error` | `OctagonXIcon` | `size-4` |
| `warning` | `TriangleAlertIcon` | `size-4` |
| `info` | `InfoIcon` | `size-4` |
| `loading` | `Loader2Icon` | `size-4 animate-spin` |

### Cách gọi Toast trong code

```typescript
import { toast } from "sonner";

toast.success("Đổi mật khẩu thành công. Các thiết bị khác đã bị đăng xuất.");

toast.error("Mật khẩu không chính xác.", { duration: 6000 });

toast.warning("Phiên đăng nhập sắp hết hạn.", { duration: 6000 });

toast.info("Vui lòng đăng nhập để tiếp tục.");
```

### Quy tắc Duration

| Loại | Duration | Lý do |
| :--- | :--- | :--- |
| **Success / Info** | `4000ms` (mặc định Sonner) | Thông tin xác nhận — người dùng chỉ cần lướt qua. |
| **Error / Warning** | `6000ms` (override per-toast) | Cần thời gian đọc thông điệp lỗi và quyết định hành động. |
| **Loading** | `Infinity` (tự dismiss khi promise resolve) | Dùng với `toast.promise()` cho long-running operations. |

### Styling Monochrome (CSS Override)

Sonner mặc định dùng màu sắc (xanh success, đỏ error). Để giữ monochrome palette theo AGENTS.md, override qua CSS variables trong `sonner.tsx`:

| Phần tử | Light Mode | Dark Mode |
| :--- | :--- | :--- |
| Background | `var(--popover)` — trắng | `var(--popover)` — đen |
| Text | `var(--popover-foreground)` — đen | `var(--popover-foreground)` — trắng |
| Border | `var(--border)` — `neutral-200` | `var(--border)` — `neutral-800` |

### Hành vi mặc định Sonner (không cần cấu hình thêm)
1. **Pause on Hover**: Tự động tạm dừng countdown khi hover (Desktop).
2. **Swipe to Dismiss**: Vuốt ngang để đóng Toast (Mobile).
3. **Stacking Animation**: Sonner tự animate xếp chồng với scale + translate.
4. **A11y**: Sonner tự thêm `role="status"` và `aria-live="polite"` — screen reader tương thích sẵn.
5. **Keyboard**: `Escape` để dismiss Toast đang focus.

---

## 5. Trạng thái Tải dữ liệu / Rỗng / Lỗi (Loading / Empty / Error States)

### 5.1. Loading Skeleton

Khi đang fetch dữ liệu lần đầu, hiển thị placeholder shimmer monochrome thay vì spinner toàn trang.

#### Profile Page Skeleton
```
+--------------------------------------------------+
|        +--------+                                |
|        | ░░░░░░ |   ← Avatar (hình tròn)         |
|        +--------+                                |
|                                                  |
|  ░░░░░░░░░░░░░░░░░░░░░   ← Username             |
|  ░░░░░░░░░░░░░░░░░░░░░░░░░  ← Email             |
|  ░░░░░░░░░░░░░   ← Role                         |
|  ░░░░░░░░░░░░░░░░░░░░░░░░░░░░  ← Full Name      |
|  ░░░░░░░░░░░░░░░   ← Phone                      |
+--------------------------------------------------+
```

#### Sessions Page Skeleton
```
+--------------------------------------------------+
|  ░░░░░░░░░░░░░   ← Tiêu đề                     |
|                                                  |
|  +----------------------------------------------+|
|  | ░░░░░░░░░░░  ░░░░░░░  ░░░░░░  ░░░░ | ← Card 1|
|  +----------------------------------------------+|
|  +----------------------------------------------+|
|  | ░░░░░░░░░░░  ░░░░░░░  ░░░░░░  ░░░░ | ← Card 2|
|  +----------------------------------------------+|
|  +----------------------------------------------+|
|  | ░░░░░░░░░░░  ░░░░░░░  ░░░░░░  ░░░░ | ← Card 3|
|  +----------------------------------------------+|
+--------------------------------------------------+
```

**Đặc tả**:
- Nền placeholder: `bg-neutral-200` với hiệu ứng shimmer (gradient sáng trượt từ trái sang phải).
- Thời gian shimmer: lặp lại mỗi 1.5 giây.
- Kích thước placeholder khớp với kích thước thực của nội dung để tránh layout shift (CLS).

---

### 5.2. Trạng thái Rỗng (Empty State)

Khi dữ liệu trả về rỗng (edge case hiếm gặp):

```
+--------------------------------------------------+
|                                                  |
|            [Illustration trống]                  |
|                                                  |
|         Không có dữ liệu để hiển thị             |
|                                                  |
|  (Mô tả phụ tùy theo ngữ cảnh)                  |
|                                                  |
+--------------------------------------------------+
```

**Đặc tả**:
- Illustration: monochrome, kích thước nhỏ gọn (`120x120px`).
- Text chính: `text-neutral-600 font-medium`.
- Text phụ: `text-neutral-400 text-sm`.

---

### 5.3. Trạng thái Lỗi + Thử lại (Error & Retry)

Khi API trả về lỗi mạng, timeout, hoặc HTTP 500:

```
+--------------------------------------------------+
|                                                  |
|              [Icon Lỗi ⚠]                       |
|                                                  |
|       Đã xảy ra lỗi, vui lòng thử lại           |
|                                                  |
|    Không thể tải dữ liệu. Kiểm tra kết nối      |
|    mạng hoặc thử lại sau ít phút.                |
|                                                  |
|           [ THỬ LẠI ]                            |
|                                                  |
+--------------------------------------------------+
```

**Đặc tả**:
- Nút **"THỬ LẠI"**: gọi lại API fetch dữ liệu. `disabled = true` + Spinner trong 1 giây để tránh spam.
- Icon lỗi: monochrome, kích thước `48x48px`.
- Hiển thị inline (thay thế phần nội dung) chứ không overlay toàn trang.
