# Piano design system — nền tảng dùng chung

Ngôn ngữ thị giác của app lấy cảm hứng từ đàn piano. Tài liệu này mô tả **lớp nền** đã
được cài trong [`Frontend/src/app/globals.css`](../Frontend/src/app/globals.css): token
và utility mà mọi trang dùng chung. Việc áp dụng cho từng trang cụ thể nằm ngoài phạm vi
file này.

Nguyên tắc gốc: bảng màu của app vốn đã hoàn toàn vô sắc (`oklch(L 0 0)`) — đó chính là
bàn phím piano. Nên không thêm màu, không rắc icon nốt nhạc; chỉ hệ thống hoá vài motif
có sẵn rồi áp xuống toàn bộ.

---

## 1. Tempo — mọi thời lượng đều là một nốt ở 120bpm

| Token | Giá trị | Nốt | Dùng cho |
| --- | --- | --- | --- |
| `--beat-16th` | 125ms | móc kép | phản hồi nhấn, ripple |
| `--beat-8th` | 250ms | móc đơn | hover, đổi màu |
| `--beat` | 500ms | đen | mở/đóng, fade |
| `--beat-half` | 1000ms | trắng | chuyển trang, loop dài |

Có sẵn 4 utility cùng tên: `beat-16th`, `beat-8th`, `beat`, `beat-half`
(đều set `transition-duration`). **Dùng chúng thay cho `duration-200`** — khi mọi
transition trong app chia hết cho cùng một nhịp, chuyển động sẽ cảm thấy đồng bộ.

Easing: `--key-ease` (nảy như phím nhả, mặc định của `key-press`) và `--hammer-ease`
(tuyến tính hơn). Cả hai có utility Tailwind: `ease-key`, `ease-hammer`.

Đổi `--tempo-bpm` chỉ mang tính tài liệu — muốn đổi nhịp thật thì sửa 4 giá trị ms.

---

## 2. Accent duy nhất: đồng thau

`--brass` / `--brass-foreground`, dùng qua `bg-brass`, `text-brass`, `border-brass`,
`ring-brass`.

Lấy từ pedal và khung dây đàn. **Chỉ dùng cho trạng thái đang hoạt động**: đang phát,
đang thu, phòng đang live. Vì mọi thứ khác vô sắc, một chấm đồng hút mắt tuyệt đối —
dùng rộng ra là mất ngay tác dụng đó.

---

## 3. Bốn motif

### M4 · `key-press` — búa gõ dây

Thứ quan trọng nhất. Áp lên **mọi** thứ nhấn được. Đã nối sẵn vào
[`Button`](../Frontend/src/components/ui/button.tsx) nên phần lớn app có sẵn.

```tsx
<button className="key-press ...">
```

Khi `:active`: `translate: 0 2px` + `scale: 0.985`, timing `--beat-16th`/`--key-ease`.

Ba chi tiết đã tính sẵn:

- Dùng property `translate`/`scale` riêng lẻ, **không** dùng `transform`, nên composes
  được với `hover:scale-105` mà không xung đột.
- Cố ý **không** đụng vào `box-shadow`, để focus ring (`ring-*`) không bị nuốt.
- Loại trừ `[aria-haspopup]`: trigger của popup không được dịch chuyển, nếu không
  popup neo vào nó sẽ nhảy theo.

Dưới `prefers-reduced-motion` phần dịch chuyển bị tắt; các surface bên dưới vẫn giữ
inset shadow nên vẫn có phản hồi nhấn.

### M1 · `key-white` / `key-black` — mặt phím

Bề mặt phím thật: gradient mặt phím, vân gỗ, viền phải tối, bo `0 0 3px 3px`, inset
shadow lúc nghỉ và lúc nhấn.

```tsx
<button className="key-white key-press h-24 w-10">
```

`key-black` tự override `--key-shadow-rest` / `--key-shadow-pressed` cục bộ nên chỉ cần
đổi class là ra phím đen.

Hai lưu ý:

- **Tối đa một bề mặt phím thật mỗi màn hình.** Đây là điểm nhấn, không phải nền. Chỗ
  khác chỉ nên mượn *hình dạng* (tỉ lệ cao/rộng, viền phải, góc bo dưới).
- Hai utility này có sở hữu `box-shadow`, nên focus dùng `outline-2 outline-offset-2`
  chứ đừng dùng `ring-*`.

Phím trắng vẫn sáng ở dark mode — đúng như đàn thật trong phòng tối.

### M2 · `staff-lines` — khuông nhạc

Năm dòng kẻ, vẽ đúng một lần, canh giữa theo chiều dọc của phần tử.

```tsx
<div className="staff-lines h-24" />
```

Chỉnh `--staff-gap` (mặc định `0.6rem`) để đổi khoảng dòng, `--staff-line` để đổi độ mờ.
Dùng cho divider, nền empty state, nền khu vực waveform.

### M3 · `.waveform` — sóng âm

Năm cột dao động lệch pha nhau đúng một `--beat-16th`, chu kỳ `--beat-half`.

```tsx
<span className="waveform text-brass" data-state={isPlaying ? "playing" : "paused"}>
  <span /><span /><span /><span /><span />
</span>
```

Cao theo `1em` và ăn theo `currentColor`, nên chỉ cần đặt `text-*` và cỡ chữ của
container. `data-state="paused"` dừng animation và hạ các cột xuống.

---

## 4. Ranh giới

- **Âm thanh phải opt-in.** Landing hero phát nốt khi hover là hợp lý; dashboard thì
  không. Khu vực đăng nhập mặc định tắt.
- **Không lạm dụng skeuomorphism.** Vân gỗ + gradient trải trên 40 hàng bảng sẽ thành ồn.
- **Luôn kiểm tra `prefers-reduced-motion`** khi thêm animation mới; block xử lý nằm ở
  cuối `globals.css`.
