# Piano & Musical Aesthetics Design System

Hệ thống thiết kế Cảm hứng Piano & Âm nhạc (Trắng / Đen / Xám - Achromatic Monochrome) áp dụng cho giao diện PWB_MiNi.

## 1. Palette màu Đơn sắc (Piano Keys Palette)

- **Ivory White (Trắng phím ngà)**: `#FFFFFF` / `bg-white` (Nền phím chính, card sáng)
- **Ebony Black (Đen phím gỗ)**: `#000000` / `#0A0A0A` / `bg-black` / `bg-neutral-950` (Nền phím tối, button bấm chính)
- **Piano Wire / Metallic Gray (Xám kim loại/dây đàn)**: `neutral-200`, `neutral-300`, `neutral-700`, `neutral-800` (Đường viền ngăn cách phím, bóng mờ)
- **Subtle Key Accent**: Đường line chỉ báo phím bấm `w-[3px]` hoặc `border-b-2` với sự tương phản cao Đen/Trắng.

## 2. Các Đặc tố Thiết kế (Design Motifs)

1. **Piano Key Rhythm Tabs & Cards**:
   - Các dòng và ô tab có tỷ lệ tương phản sắc nét giữa Trắng và Đen.
   - Hiệu ứng nảy phím khi nhấn (`active:translate-y-[1px]` hoặc `active:scale-[0.995]`).
2. **Equalizer Soundwave Indicator**:
   - Các dải thanh 3-4 vạch sóng âm đứng đơn sắc nhảy theo nhịp điệu khi audio đang phát hoặc đang xử lý.
3. **Monochrome Status Badges**:
   - `READY`: Phím ngà Ivory nhãn chữ đen mờ, viền xám nhẹ.
   - `PROCESSING`: Phím xám đá với biểu tượng Equalizer nhấp nháy nhịp điệu.
   - `FAILED`: Phím viền đứt nét tối giản đơn sắc.
