# Liveroom — State → UI Mapping

> **Mục đích**: Quy định rõ ràng UI render cho từng state của resource (Room, JoinRequest, Participant Session) — làm cơ sở cho Frontend implement component và QA test UI.
>
> **Nguyên tắc**: **Mỗi state UI phải map 1-1 với state backend**. Không tự chế state "loading", "error", "empty" lung tung — phải được define rõ trong tài liệu này.

**Phiên bản**: v1.4 (2026-07-25)
**Căn cứ**: `liveroom-business-requirements.md` v1.7, `liveroom-user-flow.md` v1.2

---

**Thay đổi v1.4 (đồng bộ BR v1.7)**:
- **Tách 2 reject reason (R-REJECT-04)**: `OWNER_REJECT` (tính LOCKED) vs `ROOM_FULL` (không tính LOCKED)
- **Owner grace period (R-LEAVE-04 → R-LEAVE-09)**: UI banner với countdown 60s
- **Owner reserved slot (R-LEAVE-09)**: capacity display "X/Y (max - 1)" trong grace
- **WS events v1.7**: `OWNER_LEFT`, `OWNER_REJOINED`, `ROOM_AUTO_ENDED`, `CAPACITY_REACHED`, `PARTICIPANT_LEFT` (vs `PARTICIPANT_KICKED`)
- **RECONNECTING overlay** (EC-07): "Đang kết nối lại... Còn X giây trước khi rời phòng"
- **Empty room indicator** (R-LEAVE-08): "Phòng trống, sẽ tự kết thúc sau Xs" (chỉ khi KHÔNG có grace)

**Thay đổi v1.3**:
- Thêm section **Music Player State Mapping** (10. Music Player UI States)
- Thêm section **Annotation State Mapping** (11. Annotation UI States)
- Thêm WS events: `MUSIC_PLAYBACK_STATE_CHANGED`, `MUSIC_SONG_CHANGED`, `ANNOTATION_CREATED`
- Thêm `<MusicPlayer>`, `<AnnotationMarker>`, `<SongPickerModal>`, `<AnnotationModal>` vào Component Library
- Thêm animation cho music + annotation
- Thêm accessibility cho music player + annotation

**Thay đổi v1.3 (bổ sung)**:
- **Bỏ `APPROVED_WAITING` state**: Approve khi phòng đầy → REJECT luôn (R-APPROVE-05)
- **Bỏ `ROOM_CAPACITY_CHANGED → AUTO_PROMOTE`**: Không còn logic auto-promote khi có slot
- Thêm section **Chat Panel State Mapping** (8. Chat UI States)
- Thêm `CHAT_MESSAGE_RECEIVED` vào WS Event catalog
- Thêm `<ChatPanel>`, `<ChatMessage>`, `<ChatInput>`, `<ChatToggle>` vào UI Component Library
- Thêm animation cho chat (message slide in, panel slide right, scroll auto)
- Thêm accessibility cho chat (aria-live, keyboard nav)

---

## 1. Quy ước chung

### 1.1. UI State Categories

Mỗi screen có **4 loại UI state**:

| Category | Mô tả | Trigger |
|---|---|---|
| **Domain state** | State nghiệp vụ (ACTIVE, PENDING, APPROVED, ...) | Từ backend trả về |
| **Loading state** | UI đang fetch data | Khi gọi API lần đầu |
| **Empty state** | Data rỗng | Khi API trả list rỗng |
| **Error state** | Có lỗi xảy ra | Khi API trả error / WS disconnect |

### 1.2. State Combination

Một screen có thể ở **nhiều state cùng lúc**, ví dụ:
- Domain state = ACTIVE, Loading = false, Empty = false, Error = false → render full UI
- Domain state = ACTIVE, Loading = true → render skeleton
- Domain state = PENDING, Error = true → render banner lỗi + vẫn hiển thị UI chờ duyệt

### 1.3. Quy tắc render

1. **Error state** luôn hiển thị banner ở trên cùng (không che UI chính)
2. **Loading state** chỉ hiển thị skeleton cho domain content (không che header, navigation)
3. **Empty state** chỉ hiển thị khi domain state load xong và data rỗng
4. **Domain state** là state chính, quyết định UI layout

---

## 2. Room State → UI

### 2.1. Room State Catalog

| State | Mô tả | Nghiệp vụ |
|---|---|---|
| `ACTIVE` | Phòng đang hoạt động | Cho phép join/leave/end |
| `ENDED` | Phòng đã kết thúc | Read-only, cho phép reopen |

### 2.2. Mapping chi tiết

| Domain State | Loading | Empty | Error | UI Render |
|---|---|---|---|---|
| **ACTIVE** | ✅ | — | — | Skeleton (loading participants list) |
| **ACTIVE** | ❌ | ✅ | — | Hiển thị "Chưa có ai tham gia" + mời (copy roomCode) |
| **ACTIVE** | ❌ | ❌ | ✅ | Banner lỗi + danh sách participants vẫn hiển thị |
| **ACTIVE** | ❌ | ❌ | ❌ | Full UI: video grid, controls, owner panel (nếu là owner) |
| **ENDED** | ✅ | — | — | Skeleton |
| **ENDED** | ❌ | ✅ | — | Hiển thị "Phòng chưa có ai tham gia" + lịch sử |
| **ENDED** | ❌ | ❌ | ✅ | Banner lỗi |
| **ENDED** | ❌ | ❌ | ❌ | UI read-only + nút Reopen (nếu là owner) |

### 2.3. UI Elements theo Room State

| Element | ACTIVE | ENDED |
|---|---|---|
| Badge "Đang hoạt động" (xanh) | ✅ | ❌ |
| Badge "Đã kết thúc" (xám) | ❌ | ✅ |
| Participants list (realtime update) | ✅ | ✅ (snapshot, không realtime) |
| Owner panel (End/Kick/Reopen) | ✅ (chỉ End/Kick) | ✅ (chỉ Reopen) |
| JoinRequest list | ✅ (badge "X chờ duyệt") | ❌ |
| Audio controls | ✅ | ❌ |
| Video controls | ✅ | ❌ |
| Action button "End phòng" | ✅ | ❌ |
| Action button "Reopen phòng" | ❌ | ✅ |
| Lịch sử sessions | ✅ | ✅ |

---

## 3. JoinRequest State → UI (v1.1 MERGED INTO SC-04)

### 3.1. JoinRequest State Catalog (v1.7)

| State | Mô tả | Nghiệp vụ |
|---|---|---|
| `PENDING` | Đang chờ owner duyệt | User chờ, Owner có thể approve/reject |
| `APPROVED` | Đã được duyệt, phòng còn slot | **v1.1: Auto-create participant + auto-redirect SC-06** |
| `REJECTED` (OWNER_REJECT) | Bị từ chối bởi owner | **v1.7**: Tăng `rejectCountByOwner` (tính LOCKED). User retry được nếu count < 3 |
| `REJECTED` (ROOM_FULL - v1.7) | Bị từ chối vì phòng đầy | **v1.7**: Tăng `rejectCountByCapacity` (KHÔNG tính LOCKED). User retry tự do |
| `CANCELLED` | User tự huỷ | User có thể retry |
| `EXPIRED` | Hết hạn (phòng ENDED) | User có thể retry |
| `LOCKED` | Bị từ chối 3+ lần bởi owner (`rejectCountByOwner >= 3`) | **v1.1**: Reset khi reopen (Bug 1). Modal "Bị ban" mỗi lần click "Xin vào" (Bug 9) |

### 3.2. Mapping chi tiết theo State (trong SC-04 Phase 3 LOBBY)

#### PENDING

```
┌──────────────────────────────────────┐
│  Tên phòng: "Daily Sync"            │
│  Owner: An (an@company.com)         │
│  Hiện tại: 5/7 người                │  ← v1.1: Capacity real-time
├──────────────────────────────────────┤
│       🕐 Đang chờ duyệt...          │
│    Yêu cầu của bạn đang được        │
│       xử lý bởi chủ phòng           │
├──────────────────────────────────────┤
│         [Huỷ yêu cầu]              │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Capacity real-time "Hiện tại: X/Y" | ✅ (v1.1) |
| Spinner animation | ✅ |
| Text "Đang chờ duyệt..." | ✅ |
| Thông tin phòng (tên, owner) | ✅ |
| Preview mic/cam (giúp user setup trong lúc chờ) | ✅ |
| Nút "Huỷ yêu cầu" | ✅ |
| Nút "Gửi lại" | ❌ |
| Banner lỗi | ❌ (chỉ khi Error) |

#### APPROVED (Hiếm khi hiển thị - auto-redirect ngay)

```
┌──────────────────────────────────────┐
│       ✅ Yêu cầu được duyệt!       │
│    Đang chuyển đến phòng họp...     │
│    (v1.1: Auto-redirect SC-06)      │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Icon success | ✅ |
| Text "Yêu cầu được duyệt!" | ✅ |
| Auto-redirect SC-06 | ✅ (sau 1-2s) |

**v1.1**: State APPROVED thường không hiển thị lâu — user được auto-redirect vào SC-06 (Bug 4).

#### REJECTED (v1.6 update - bao gồm "Room full")

```
┌──────────────────────────────────────┐
│  Tên phòng: "Daily Sync"            │
│  Owner: An (an@company.com)         │
│  Hiện tại: 7/7 người - Còn 0 slot  │  ← v1.1 real-time
│  🔴 Phòng đang đầy                  │
├──────────────────────────────────────┤
│   ❌ Yêu cầu bị từ chối             │
│   Lý do: Phòng đã đầy               │  ← v1.6 NEW (room full reason)
│   hoặc: Owner đã từ chối             │
├──────────────────────────────────────┤
│  Yêu cầu bị từ chối: 1/3 lần       │
│      [Gửi lại yêu cầu]              │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Capacity real-time "Hiện tại: X/Y" | ✅ (v1.1) |
| Icon "Yêu cầu bị từ chối" | ✅ |
| Lý do: "Phòng đã đầy" (v1.6 NEW) hoặc "Owner đã từ chối" | ✅ |
| Số lần bị reject "X/3" | ✅ |
| Nút "Gửi lại yêu cầu" (nếu chưa LOCKED) | ✅ |
| Realtime update capacity | ✅ (WS ROOM_CAPACITY_CHANGED) |
| **KHÔNG auto-redirect** (v1.6 bỏ auto-promote) | ❌ |

```
┌──────────────────────────────────────┐
│  Tên phòng: "Daily Sync"            │
│  Owner: An (an@company.com)         │
│  Hiện tại: 5/7 người                │
├──────────────────────────────────────┤
│       ❌ Yêu cầu bị từ chối        │
│    Lý do: Không rõ (do owner        │
│    không cung cấp lý do)            │
│  Bạn đã bị từ chối 1/3 lần          │
├──────────────────────────────────────┤
│       [Gửi lại yêu cầu]            │
│          [Về trang chủ]             │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Capacity real-time | ✅ (v1.1) |
| Icon error | ✅ |
| Text "Yêu cầu bị từ chối" | ✅ |
| Lý do (nếu owner cung cấp) | Optional |
| Counter "X/3 lần" | ✅ |
| Nút "Gửi lại yêu cầu" | ✅ (nếu count < 3) |
| Nút "Về trang chủ" | ✅ |

**Khi count = 3 → chuyển sang state LOCKED**

#### CANCELLED

```
┌──────────────────────────────────────┐
│       ℹ️  Đã huỷ yêu cầu            │
│    Bạn đã huỷ yêu cầu tham gia     │
├──────────────────────────────────────┤
│       [Gửi lại yêu cầu]            │
│          [Về trang chủ]             │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Icon info | ✅ |
| Text "Đã huỷ yêu cầu" | ✅ |
| Nút "Gửi lại yêu cầu" | ✅ |
| Nút "Về trang chủ" | ✅ |

#### EXPIRED (v1.1 - khi phòng ENDED)

```
┌──────────────────────────────────────┐
│       ⏰ Hết hạn                    │
│    Phòng đã kết thúc, yêu cầu của  │
│    bạn đã hết hạn                   │
│    (Trigger: WS ROOM_ENDED - v1.1)  │
├──────────────────────────────────────┤
│          [Về trang chủ]             │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Icon warning | ✅ |
| Text "Phòng đã kết thúc. Yêu cầu đã hết hạn." | ✅ |
| Nút "Gửi lại yêu cầu" | ❌ (chờ reopen) |
| Nút "Về trang chủ" | ✅ |

**v1.1**: User ở lobby nhận WS ROOM_ENDED → chuyển state sang EXPIRED (Bug 6).

#### LOCKED (v1.1 - có Modal riêng tại Phase 2)

```
Trên SC-04 Phase 2 PREJOIN (khi user click "Xin vào"):
┌──────────────────────────────────────┐
│       🔒 Bạn đã bị ban khỏi phòng   │
│                                      │
│    Lý do: Bạn đã bị từ chối 3 lần  │
│    trong phiên này.                  │
│                                      │
│    ⚠️ Áp dụng cho phiên này.        │
│    Sau khi phòng ENDED → reopen,    │
│    bạn có thể thử lại.               │
│                                      │
├──────────────────────────────────────┤
│          [Đóng]                      │
└──────────────────────────────────────┘
```

| Element | Hiển thị |
|---|---|
| Icon lock | ✅ |
| Text "Bạn đã bị ban khỏi phòng này" | ✅ |
| Lý do cụ thể (3 lần reject) | ✅ |
| Lưu ý "Áp dụng trong phiên này" | ✅ (v1.1 - giúp user hiểu là không vĩnh viễn) |
| Lưu ý "Sau reopen có thể thử lại" | ✅ (v1.1 Bug 1) |
| Nút "Gửi lại yêu cầu" | ❌ |
| Nút "Đóng" | ✅ (vẫn ở Phase 2) |
| Khi user click "Xin vào" lần nữa | Modal hiển thị lại (mỗi lần đều có feedback - Bug 9) |

### 3.3. State Transition → UI Action (v1.7)

| Transition | Trigger | UI Action |
|---|---|---|
| (none) → PENDING | POST /join-requests 201 | Show PENDING UI |
| **PENDING → APPROVED** | WS event `REQUEST_APPROVED` (còn slot) | **Auto-redirect SC-06 (v1.1 Bug 4)** |
| **PENDING → REJECTED (ROOM_FULL - v1.7)** | WS event `JOIN_REQUEST_REJECTED { reason: "ROOM_FULL" }` | Show REJECTED UI với banner "Phòng đã đầy" + nút "Gửi lại" (luôn enabled) |
| PENDING → REJECTED (OWNER_REJECT) | WS event `JOIN_REQUEST_REJECTED { reason: "OWNER_REJECT" }` | Show REJECTED UI với banner "Bị từ chối bởi owner (X/3)" |
| PENDING → CANCELLED | User click "Huỷ yêu cầu" | Show CANCELLED UI |
| PENDING → EXPIRED | WS event `ROOM_MANUAL_ENDED` hoặc `ROOM_AUTO_ENDED` | Show EXPIRED UI (kể cả khi đang chờ - Bug 6) |
| REJECTED (OWNER_REJECT) → PENDING | User click "Gửi lại" (rejectCountByOwner < 3) | Show PENDING UI |
| **REJECTED (ROOM_FULL - v1.7) → PENDING** | User click "Gửi lại" (không bị giới hạn counter) | Show PENDING UI |
| **REJECTED → LOCKED (v1.7)** | rejectCountByOwner >= 3 | **Show Modal "Bạn đã bị ban" khi user click "Xin vào"** |
| CANCELLED → PENDING | User click "Gửi lại" | Show PENDING UI |
| EXPIRED → PENDING | User click "Gửi lại" (sau khi phòng reopen) | Show PENDING UI |

---

## 4. Participant Session State → UI (v1.1 SC-06 gồm NOT_JOINED)

### 4.1. Session State Catalog (v1.7)

| State | Mô tả | UI | Trigger |
|---|---|---|---|
| `NOT_JOINED` (v1.1 NEW) | User đã was_approved, chưa join session | SC-06 placeholder + preview + nút "Vào phòng" | User có was_approved=TRUE + ACTIVE |
| `JOINING` | Đang connecting WebRTC | Full-screen spinner | User click "Vào phòng" |
| `ACTIVE` | Đang trong session | Video grid + controls | ws_ready |
| `RECONNECTING` | WS disconnect, đang reconnect | Overlay "Đang kết nối lại... Còn X giây" | WS disconnect |
| `KICKED` | Bị owner đuổi | Modal + redirect | Owner kick |
| `ENDED` | Phòng ENDED (manual hoặc auto) | Modal + redirect | Room ENDED |
| `LEFT` | User tự rời | Redirect SC-04 hoặc SC-01 | User leave |
| `OWNER_GRACE` (v1.7 NEW) | Owner đã leave, grace 60s | Banner "Owner đã rời — phòng sắp kết thúc sau Xs" | WS `OWNER_LEFT` |
| `OFFLINE` | Grace hết mà không reconnect | Slot giải phóng | Reconnect timeout 60s |

### 4.2. Mapping chi tiết

#### NOT_JOINED (v1.1 NEW - thay thế SC-05b)

```
Hiển thị ở: SC-06 (Participant view khi đã was_approved + chưa join session)
Layout: Video grid placeholder + controls disabled + Preview local + nút "Vào phòng"
```

| Element | Hiển thị |
|---|---|
| Video grid placeholder (slot mờ + avatar) | ✅ |
| **Preview camera local (Bug 11)** | ✅ (giúp user test mic/cam trước khi vào) |
| Placeholder cho camera (avatar) | ✅ nếu user deny |
| Test mic level indicator | ✅ |
| Audio/Mic controls | ❌ (disabled) |
| Video/Camera controls | ❌ (disabled) |
| Nút "Rời phòng" | ❌ |
| **Nút "Vào phòng"** | ✅ (enabled) |
| Keyboard shortcuts | ❌ (chỉ ACTIVE mới có) |

#### JOINING

```
Hiển thị ở: SC-06 (sau khi click "Vào phòng")
Layout: Full-screen spinner + text "Đang kết nối..."
```

| Element | Hiển thị |
|---|---|
| Full-screen overlay | ✅ |
| Spinner | ✅ |
| Text "Đang kết nối đến phòng họp..." | ✅ |
| Nút "Huỷ" | ✅ |
| Keyboard shortcuts | ❌ |

#### ACTIVE (v1.1 - có Keyboard shortcuts)

```
Hiển thị ở: SC-06 (đang trong meeting)
Layout: Video grid (7 slots) + control bar + optional owner panel
```

| Element | Hiển thị | Phím tắt (v1.1) |
|---|---|---|
| Video grid | ✅ | — |
| Mic toggle (mute/unmute) | ✅ | **M** |
| Camera toggle (on/off) | ✅ | **V** |
| Nút "Rời phòng" | ✅ | **Ctrl+E** (Win) / **Cmd+E** (Mac) |
| Nút "End phòng" | ✅ (chỉ owner) | — |
| Context menu "Kick" | ✅ (chỉ owner, click vào tile) | — |
| Toast "Phòng sắp kết thúc" | ✅ (khi owner leave, grace period) | — |

**Shortcut tooltip**: Mỗi button có tooltip hiển thị phím tắt (VD: "Tắt mic (M)", "Bật camera (V)", "Rời phòng (Ctrl+E)").

#### RECONNECTING (v1.7 với countdown)

```
Hiển thị ở: SC-06 (khi WS disconnect)
Layout: Overlay mờ + spinner + text "Đang kết nối lại... Còn X giây"
```

| Element | Hiển thị |
|---|---|
| Overlay mờ (đè lên video grid) | ✅ |
| Spinner | ✅ |
| Text "Đang kết nối lại..." | ✅ |
| Text "Còn X giây trước khi rời phòng" (v1.7) | ✅ (grace period 60s, đếm ngược) |
| Video/audio local | ✅ (vẫn chạy local) |
| Video/audio remote | ❌ (đã disconnect) |
| Mic toggle | ✅ (vẫn có thể toggle local) |
| Camera toggle | ✅ (vẫn có thể toggle local) |
| Keyboard shortcuts | ❌ |
| Chat input | Disabled (offline indicator) |

#### KICKED

```
Hiển thị ở: SC-06 (khi bị owner kick)
Layout: Modal toàn màn hình
```

| Element | Hiển thị |
|---|---|
| Modal full-screen | ✅ |
| Icon warning | ✅ |
| Text "Bạn đã bị đuổi khỏi phòng" | ✅ |
| Lý do (nếu có) | Optional |
| Nút "Về trang chủ" | ✅ |
| Auto-redirect | ✅ (sau 3s) |

#### ENDED (v1.7 - phân biệt manual vs auto)

```
Hiển thị ở: SC-06 (khi phòng ENDED)
Modal toàn màn hình - NỘI DUNG khác nhau theo endedReason
```

| Element | ENDED (manual) | ENDED (owner_grace_expired - v1.7) | ENDED (empty_timeout) |
|---|---|---|---|
| Modal full-screen | ✅ | ✅ | ✅ |
| Icon | Info | Warning | Info |
| Text | "Phòng đã kết thúc" | "Owner không quay lại — phòng đã kết thúc" | "Phòng đã kết thúc (trống quá lâu)" |
| Lý do | Optional | "Owner đã rời phòng và không quay lại trong 60s" | "Phòng trống 5 phút" |
| Nút "Về trang chủ" | ✅ | ✅ | ✅ |
| Auto-redirect | ✅ (sau 3s) | ✅ (sau 3s) | ✅ (sau 3s) |

**v1.7**: Lobby user cũng thấy modal này qua WS `ROOM_MANUAL_ENDED` hoặc `ROOM_AUTO_ENDED` (Bug 6).

#### LEFT

```
Hiển thị ở: SC-06 (khi user tự leave)
Layout: Redirect về SC-01 (owner) hoặc SC-04 Phase 2 (participant với was_approved)
```

| Element | Hiển thị |
|---|---|
| Không có modal | ✅ |
| Auto-redirect silent | ✅ |

---

## 5. WS Event → UI Action (v1.1 - bổ sung)

### 5.1. WS Event Catalog (v1.1)

| Event | Trigger | UI Action |
|---|---|---|
| `PARTICIPANT_JOINED` | Có user mới join session | Thêm tile video + toast (optional) |
| `PARTICIPANT_LEFT` | User leave session | Xoá tile video + cập nhật count |
| `PARTICIPANT_KICKED` | Owner kick user | Modal KICKED cho user bị kick |
| `MEDIA_STATE_CHANGED` | User toggle camera/mic | Update tile video (show/hide video, mute icon) |
| `REQUEST_PENDING_NEW` | Có JoinRequest mới | Owner: badge count +1, toast notification |
| `REQUEST_APPROVED` | Owner approve request | **v1.1: User auto-redirect SC-06 (Bug 4)** |
| `REQUEST_REJECTED` | Owner reject request | User bị reject: show REJECTED UI |
| `REQUEST_LOCKED` | rejectCount = 3 | **v1.1: User show Modal "Bị ban" mỗi lần click "Xin vào" (Bug 9)** |
| `REQUEST_CANCELLED` | User huỷ yêu cầu | **v1.1: Owner update badge count** |
| `ROOM_ENDED` / `ROOM_MANUAL_ENDED` (v1.7 rename) | Phòng kết thúc | **v1.1 (Bug 6)**: Tất cả (participants ACTIVE + user có active JoinRequest) → modal ENDED |
| `ROOM_AUTO_ENDED` (v1.7 NEW) | Auto-end (owner grace hoặc empty timeout) | Modal ENDED với text theo `reason` |
| `ROOM_REOPENED` | Phòng mở lại | **v1.1 (Bug 1)**: Owner refresh SC-03a, user LOCKED có thể thử lại |
| `ROOM_CAPACITY_CHANGED` (v1.7 payload mở rộng) | Participant join/leave/timeout | **v1.1 (Bug 7 R-CAPACITY-01)**: Lobby user update count real-time (hiển thị X/Y người). **v1.7**: payload thêm `reservedOwnerSlot`. KHÔNG auto-promote (v1.6 bỏ APPROVED_WAITING). |
| `CAPACITY_REACHED` (v1.7 NEW) | currentCount >= maxParticipants | Lobby user tắt nút "Xin vào" real-time |
| `OWNER_LEFT` | Owner leave, grace period | **v1.7**: Participants: banner "Owner đã rời — phòng sắp kết thúc sau Xs" + countdown 60s + music auto-pause |
| `OWNER_REJOINED` | Owner quay lại trong grace | **v1.7**: Participants: đóng banner (music KHÔNG auto-resume theo R-MUSIC-06) |
| `ROLE_CHANGED` | User upgrade/downgrade | Update UI permissions |
| **`CHAT_MESSAGE_RECEIVED`** (v1.2 NEW) | User gửi chat message | Append message vào Chat Panel + auto-scroll |
| **`CHAT_HISTORY_LOADED`** (v1.2 NEW) | Load chat history khi vào ACTIVE | Render messages + auto-scroll xuống cuối |
| **`MUSIC_PLAYBACK_STATE_CHANGED`** (v1.3 NEW) | Play/pause/seek/volume thay đổi | Apply state + sync UI (all clients) |
| **`MUSIC_SONG_CHANGED`** (v1.3 NEW) | User chọn bài mới | Load audio mới + autoplay |
| **`ANNOTATION_CREATED`** (v1.3 NEW) | User tạo annotation | Append marker + popup |

### 5.2. Event Handling Pattern

```typescript
ws.on('REQUEST_APPROVED', (event) => {
  if (event.userId === currentUser.id) {
    // v1.1: Auto-redirect, không cần click "Vào phòng"
    navigate('/rooms/:id/room');
  }
});

ws.on('ROOM_CAPACITY_CHANGED', (event) => {
  if (isUserInLobby) {
    setCapacity({ current: event.currentCount, max: event.maxParticipants });
    // v1.6: Bỏ auto-promote - user tự retry khi có slot trống
  }
});

ws.on('REQUEST_REJECTED', (event) => {
  if (event.userId === currentUser.id) {
    // v1.1: Hiển thị REJECTED UI hoặc MODAL nếu đã LOCKED
    if (event.rejectCount >= 3) {
      setState('LOCKED');
      // Không show modal tự động, chỉ khi user click "Xin vào" lần sau
    } else {
      setJoinRequestState('REJECTED');
    }
  }
});

ws.on('REQUEST_LOCKED', (event) => {
  if (event.userId === currentUser.id) {
    setState('LOCKED');
    // v1.1: Modal sẽ hiển thị khi user click "Xin vào"
  }
});

ws.on('ROOM_ENDED', (event) => {
  if (isUserInLobby) {
    // v1.1 Bug 6: Lobby user cũng nhận
    setJoinRequestState('EXPIRED');
    showEndedModal();
  }
});
```

---

## 6. Common UI States (cho mọi screen)

### 6.1. Loading State

```
Skeleton component:
- Pulse animation 1.5s
- Background color: gray-200 (light) / gray-800 (dark)
- Match layout của content thật
- KHÔNG che header/navigation
```

### 6.2. Empty State

```
Layout:
- Icon lớn (illustration)
- Tiêu đề ngắn (1 dòng)
- Mô tả (1-2 dòng)
- CTA button (nếu có action)
- Background: neutral
```

### 6.3. Error State

```
Layout:
- Banner đỏ ở top (fixed position)
- Icon warning + message + nút "Thử lại" + nút "Đóng"
- KHÔNG che UI chính (chỉ banner, content vẫn hiển thị)
- Auto-dismiss: 5s (warning), không auto-dismiss (error nghiêm trọng)
```

### 6.4. Toast Notification

```
Variants:
- Success: green background, check icon, 3s auto-dismiss
- Warning: yellow background, warning icon, 5s auto-dismiss
- Error: red background, error icon, KHÔNG auto-dismiss (cần user đóng)
- Info: blue background, info icon, 3s auto-dismiss

Position: top-right (desktop), top-center (mobile)
```

---

## 7. Modal & Confirmation (v1.1 - thêm Modal "Bị ban")

### 7.1. Modal Catalog

| Modal | Trigger | Actions |
|---|---|---|
| EndRoom | Owner click "End phòng" | [Huỷ] [Xác nhận End] |
| ReopenRoom | Owner click "Reopen" | [Huỷ] [Xác nhận Reopen] |
| CancelJoinRequest | User click "Huỷ yêu cầu" | [Đóng] [Xác nhận huỷ] |
| RetryJoinRequest | User click "Gửi lại" | [Đóng] [Xác nhận gửi lại] |
| LeaveSession | User click "Rời phòng" hoặc nhấn Ctrl/Cmd+E | [Ở lại] [Rời phòng] |
| KickParticipant | Owner click "Kick" | [Huỷ] [Kick] |
| MediaDenied | Browser block camera/mic | [Tiếp tục chỉ nghe] |
| **BanModal (v1.1 NEW)** | **User click "Xin vào" khi LOCKED** | **[Đóng]** |

### 7.2. Modal Pattern

```tsx
// Standard Modal
<Modal open={isOpen} onClose={handleClose}>
  <Modal.Header>
    <Icon name="warning" />
    <Title>End phòng?</Title>
  </Modal.Header>
  <Modal.Body>
    <Text>Bạn có chắc muốn kết thúc phòng họp? Tất cả participants sẽ bị ngắt kết nối.</Text>
  </Modal.Body>
  <Modal.Footer>
    <Button variant="secondary" onClick={handleClose}>Huỷ</Button>
    <Button variant="danger" onClick={handleEnd}>End phòng</Button>
  </Modal.Footer>
</Modal>

// BanModal (v1.1 NEW)
<Modal open={isOpen} onClose={handleClose}>
  <Modal.Header>
    <Icon name="lock" color="red" />
    <Title>Bạn đã bị ban khỏi phòng này</Title>
  </Modal.Header>
  <Modal.Body>
    <Text>Lý do: Bạn đã bị từ chối 3 lần trong phiên này.</Text>
    <Note>⚠️ Áp dụng cho phiên này. Sau khi phòng ENDED → reopen, bạn có thể thử lại.</Note>
  </Modal.Body>
  <Modal.Footer>
    <Button variant="primary" onClick={handleClose}>Đóng</Button>
  </Modal.Footer>
</Modal>
```

---

## 8. State Machine Summary

### 8.1. Room

```
ACTIVE ──end──> ENDED
ACTIVE ──downgrade──> ENDED (force-end)
ACTIVE ──grace_expired──> ENDED (v1.1: grace ưu tiên empty)
ACTIVE ──empty_5min──> ENDED (bị SUSPEND trong grace)
ENDED ──reopen──> ACTIVE (+ rejectCount reset = 0, v1.1)
```

### 8.2. JoinRequest

```
            ┌──────────────────────────┐
            ▼                          │
(none) ──create──> PENDING ──approve──> APPROVED ──auto-create participant (v1.1)
                     │                              │
                     │                              ▼
                     │                         (user auto-redirect SC-06)
                     │
                     ├──approve + full──> REJECTED (room full - v1.6)
                     │                              │
                     │                              ▼
                     │                         (auto-redirect SC-06)
                     │
                     ├──reject──> REJECTED (count++)
                     │                │
                     │                └── count=3 ──> LOCKED (TRONG PHIÊN, v1.1)
                     │                                    │
                     │                                    ▼
                     │                              (Modal "Bị ban" khi click)
                     │
                     ├──cancel──> CANCELLED
                     │
                     └──room_end──> EXPIRED ───v1.1: WS ROOM_ENDED cho cả lobby user

PENDING/REJECTED/CANCELLED/EXPIRED ──retry──> PENDING (nếu count < 3)
LOCKED ──retry──> 403 → Modal "Bị ban"
```

### 8.3. Participant Session

```
NOT_JOINED ──join──> JOINING ──ws_ready──> ACTIVE ──leave──> LEFT
   (v1.1)                                       │
                                                ├──ws_disconnect──> RECONNECTING ──ws_ready──> ACTIVE
                                                │                       │
                                                │                       └──timeout (>60s)──> OFFLINE
                                                │
                                                ├──kick──> KICKED
                                                ├──room_end──> ENDED
                                                └──shortcut M/V/Ctrl+E (v1.1, ACTIVE only)
```

---

## 8. Chat Panel State Mapping (v1.2 NEW)

### 8.1. Chat Panel States

Chat Panel có **4 state chính** (UI states, không phải domain state):

| State | Mô tả | Trigger |
|---|---|---|
| `HIDDEN` | Panel đóng (default mobile) | User chưa click toggle |
| `OPENING` | Panel đang mở (animation) | User click toggle |
| `OPENED` | Panel đang mở, hiển thị messages | Sau animation opening |
| `CLOSING` | Panel đang đóng (animation) | User click X / toggle |

### 8.2. Mapping theo Session State

| Session State | Chat Panel state | Hiển thị |
|---|---|---|
| `NOT_JOINED` | — | Không hiển thị chat toggle (chưa join session) |
| `JOINING` | — | Không hiển thị chat toggle |
| `ACTIVE` | `HIDDEN` (mobile) / `OPENED` (desktop > 1024px) | Toggle button + panel |
| `ACTIVE` (panel opened) | `OPENED` | Chat messages + input |
| `RECONNECTING` | Giữ nguyên state cũ | Nếu panel đang mở → giữ, disable input |
| `KICKED` | Force close | Modal KICKED hiển thị |
| `ENDED` | Force close | Modal ENDED hiển thị |
| `LEFT` | — | Auto-redirect |

### 8.3. Chat Panel Layout

**Desktop (> 1024px) - default OPENED**:

```
┌─────────────────────────────────┬──────────────────────┐
│  [Video Tile 1] [Video Tile 2]  │  Tin nhắn      [X]  │
│  [Video Tile 3] [Video Tile 4]  ├──────────────────────┤
│  [Video Tile 5] [Video Tile 6]  │ an@company.com 10:30│
│  [Video Tile 7]                 │ Hello cả nhóm        │
│                                 │                      │
│                                 │ binh@company 10:31   │
│                                 │ Chào An              │
│                                 │  (auto-scroll)       │
├─────────────────────────────────┴──────────────────────┤
│  [Mic M] [Camera V] [Chat 💬] [Leave Ctrl+E]          │
└────────────────────────────────────────────────────────┘
```

**Mobile (< 640px) - HIDDEN default**:

```
┌─────────────────────────┐
│  [Video Tile 1]         │
│  [Video Tile 2]         │
│                         │
├─────────────────────────┤
│ [Mic] [Cam] [💬3] [↗]  │  ← Chat toggle có badge "3"
└─────────────────────────┘

Khi click 💬 → panel slide in (full width):

┌─────────────────────────┐
│ Tin nhắn          [X]   │
├─────────────────────────┤
│ an@company.com 10:30    │
│ Hello                   │
│                         │
│ [Nhập tin nhắn...] [➤] │
└─────────────────────────┘
```

### 8.4. Chat Message UI State

Mỗi ChatMessage có 1 trong các state:

| State | Hiển thị | Trigger |
|---|---|---|
| `SENDING` | Opacity 50%, icon spinner | Đang gửi POST request |
| `SENT` | Full opacity, normal | Backend confirm 201 |
| `FAILED` | Border đỏ, icon warning, nút "Retry" | Backend error / network |

**Mockup**:

```
✓ SENT (mình gửi):
┌─────────────────────────────────────┐
│              an@company.com 10:30   │
│              Hello cả nhóm         │  ← right-aligned, blue bg
└─────────────────────────────────────┘

⟳ SENDING (đang gửi):
┌─────────────────────────────────────┐
│              an@company.com 10:30   │
│              Hello cả nhóm  ⟳      │  ← opacity 50%
└─────────────────────────────────────┘

✗ FAILED:
┌─────────────────────────────────────┐
│              an@company.com 10:30   │
│              Hello cả nhóm  ⚠      │  ← red border
│              [Thử lại]              │
└─────────────────────────────────────┘

📨 RECEIVED (người khác gửi):
┌─────────────────────────────────────┐
│ binh@company.com 10:31              │
│ Chào An                             │  ← left-aligned, gray bg
└─────────────────────────────────────┘
```

### 8.5. Chat Input States

| State | Hiển thị | Trigger |
|---|---|---|
| `EMPTY` | Placeholder "Nhập tin nhắn...", Send disabled | Input rỗng |
| `TYPING` | Counter "X/500", Send enabled | User đang nhập |
| `TOO_LONG` | Counter đỏ "500/500", input bị chặn | User vượt 500 chars |
| `WHITESPACE_ONLY` | Border đỏ, helper text "Vui lòng nhập tin nhắn" | Chỉ có space |
| `SENDING` | Icon spinner trên Send button | Đang POST |
| `SENT` | Input clear, ready cho message mới | Backend confirm 201 |
| `OFFLINE` | Border vàng, tooltip "Mất kết nối" | WS disconnect |

### 8.6. Chat Panel UI Elements (chi tiết)

**Header**:

| Element | Điều kiện |
|---|---|
| Text "Tin nhắn" | Luôn |
| Nút đóng (X) | Luôn |
| Badge "200" (số message trong history) | Có message |

**Messages list**:

| Element | Điều kiện |
|---|---|
| Auto-scroll xuống cuối khi có message mới | Luôn |
| Empty state "Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện!" | Khi messages = [] |
| Loading state (skeleton 3 dòng) | Khi đang fetch GET /:id/chat/messages |
| Error state "Không tải được lịch sử chat" + retry button | API fail |
| Divider "Hôm nay" / "Hôm qua" | Theo timestamp |

**Input area**:

| Element | Điều kiện |
|---|---|
| Input text field (max 500 chars) | ACTIVE state |
| Counter "X/500" (màu xám khi < 450, đỏ khi >= 450) | Khi có text |
| Send button (icon paper plane ➤) | Input có text valid |
| Disabled state (opacity 50%, không click được) | RECONNECTING / KICKED / ENDED |

### 8.7. State Transition cho Chat (v1.2 NEW)

| Transition | Trigger | UI Action |
|---|---|---|
| `HIDDEN` → `OPENING` | User click toggle button | Slide panel in (300ms) |
| `OPENING` → `OPENED` | Animation complete | Fetch chat history + subscribe WS |
| `OPENED` → `CLOSING` | User click X / toggle | Slide panel out (200ms) |
| `CLOSING` → `HIDDEN` | Animation complete | Clear messages (optional) |
| WS `CHAT_MESSAGE_RECEIVED` (panel OPENED) | User khác gửi message | Append + auto-scroll |
| WS `CHAT_MESSAGE_RECEIVED` (panel HIDDEN) | User khác gửi message | Badge count +1, sound notification (optional) |
| User gửi message | Click Send / Enter | State = SENDING → SENT |
| Send fail | 4xx/5xx | State = FAILED, hiển thị retry button |
| WS disconnect | RECONNECTING | Disable input + show offline indicator |
| WS reconnect | RECONNECTING → ACTIVE | Enable input, refresh messages |
| Room ENDED | WS ROOM_ENDED | Force close panel + modal redirect |

### 8.8. Chat Validation UX (v1.2 NEW)

| Action | Validation | Error UI |
|---|---|---|
| Type 501 chars | Frontend chặn tại 500 (input.maxLength) | Counter đỏ "500/500" |
| Type chỉ space | Disable Send button | Input border đỏ + helper text "Vui lòng nhập tin nhắn" |
| Click Send empty | Frontend validate | Inline error "LIVEROOM_CHAT_EMPTY" |
| Send > 500 (bypass frontend) | Backend validate | 400 + Toast "LIVEROOM_CHAT_TOO_LONG" |
| Click Send trong lúc RECONNECTING | Input disabled | Tooltip "Đang kết nối lại..." |
| Send khi bị KICKED (race) | 403 LIVEROOM_NOT_IN_SESSION | Toast + auto-redirect (modal KICKED) |
| Send khi WS offline | API fail | Toast "Lỗi kết nối" + retry button |

### 8.9. Edge Cases UI

| Edge case | UI behavior |
|---|---|
| Message có emoji | Render emoji Unicode bình thường |
| Message có HTML tag | Escape thành text (hiển thị `<script>` thay vì chạy) |
| Message có newline | Hiển thị multiple lines (CSS white-space: pre-wrap) |
| Message dài > 280px | Word wrap, max-width 280px |
| 200+ messages | Auto-scroll vẫn work; messages cũ vẫn accessible (DOM virtualization optional) |
| User scroll up để đọc message cũ | KHÔNG auto-scroll xuống khi có message mới (giữ vị trí scroll); hiển thị "↓ N tin nhắn mới" button |

---

## 9. UI Component Library Mapping (v1.2)

| UI Component | State | Used in Screen |
|---|---|---|
| `<RoomStatusBadge>` | ACTIVE / ENDED | SC-01, SC-03a, SC-07 |
| `<JoinRequestStateUI>` | PENDING / REJECTED / ... | SC-04 Phase 3 |
| `<SessionStateUI>` | NOT_JOINED / ACTIVE / RECONNECTING / ... | SC-06 |
| `<OwnerPanel>` | (theo Room state + actor.role) | SC-03a |
| `<ParticipantList>` | (danh sách + online status) | SC-03a, SC-06 |
| `<JoinRequestList>` | (các tab: PENDING/APPROVED/REJECTED) | SC-03b |
| `<MediaControls>` | (mic, camera, leave) | SC-06 |
| `<ConnectionStatus>` | (CONNECTED/RECONNECTING/DISCONNECTED) | SC-06 (top bar) |
| `<Toast>` | (success/warning/error/info) | Tất cả |
| `<BanModal>` (v1.1 NEW) | LOCKED | SC-04 Phase 2 |
| `<CapacityIndicator>` (v1.1 NEW) | currentCount/maxParticipants (+ `reservedOwnerSlot` - v1.7) | SC-04 Phase 2+3, SC-06 |
| `<MediaPreview>` (v1.1 NEW) | Local camera/mic preview | SC-04 Phase 2, SC-06 NOT_JOINED |
| `<ReconnectingOverlay>` (v1.7 enhance) | secondsRemaining countdown khi reconnect | SC-06 |
| `<OwnerGraceBanner>` (v1.7 NEW) | "Owner đã rời — phòng sắp kết thúc sau Xs" | SC-06 (Participants view) |
| `<EndedReasonModal>` (v1.7 NEW) | reason (manual/owner_grace_expired/empty_timeout) | SC-06 |
| `<CapacityReachedHint>` (v1.7 NEW) | currentCount >= maxParticipants | SC-04 Phase 2 |
| `<ChatPanel>` (v1.2 NEW) | Chat sidebar với messages + input | SC-06 |
| `<ChatMessage>` (v1.2 NEW) | Single chat message (own/others) | SC-06 Chat Panel |
| `<ChatInput>` (v1.2 NEW) | Input field + Send button + counter | SC-06 Chat Panel |
| `<ChatToggle>` (v1.2 NEW) | Chat icon trên control bar với badge | SC-06 |
| `<ChatHeader>` (v1.2 NEW) | "Tin nhắn" header + close button | SC-06 Chat Panel |
| `<MusicPlayer>` (v1.3 NEW) | Bottom bar với progress + controls + volume | SC-06 |
| `<SongPickerModal>` (v1.3 NEW) | Modal chọn bài từ Voice library | SC-06 |
| `<AnnotationMarker>` (v1.3 NEW) | Marker 📍 trên progress bar | SC-06 Music Player |
| `<AnnotationModal>` (v1.3 NEW) | Modal tạo annotation mới | SC-06 |
| `<AnnotationPopup>` (v1.3 NEW) | Popup hiển thị nội dung annotation | SC-06 Music Player |
| `<MusicToggle>` (v1.3 NEW) | Music icon trên control bar | SC-06 |

---

## 10. Music Player State Mapping (v1.3 NEW)

### 10.1. Music Player States

| State | Mô tả | Trigger |
|---|---|---|
| `HIDDEN` | Player không hiển thị | User chưa click toggle |
| `OPENING` | Đang slide up | User click toggle |
| `EMPTY` | Player mở, chưa chọn bài | Sau khi join hoặc sau ENDED |
| `LOADING` | Đang load audio stream từ Voice module | User chọn bài mới |
| `PLAYING` | Đang phát (progress bar chạy) | status = PLAYING |
| `PAUSED_BY_USER` | User click pause | User action |
| `PAUSED_BY_OWNER_LEAVE` | Owner leave → auto pause | WS OWNER_LEFT |
| `ENDED` | Bài đã hết (currentPosition >= duration) | Auto stop |
| `BUFFERING` | Đang buffer audio | Network issue |

### 10.2. UI Elements theo Music Player State

| Element | HIDDEN | EMPTY | LOADING | PLAYING | PAUSED_USER | PAUSED_OWNER | ENDED |
|---|---|---|---|---|---|---|---|
| Music icon (control bar) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Player panel (slide up) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Album thumbnail | — | ❌ | ✅ (skeleton) | ✅ | ✅ | ✅ | ✅ |
| Title + artist | — | ❌ | ✅ (placeholder) | ✅ | ✅ | ✅ | ✅ |
| Progress bar | — | ❌ | ✅ (skeleton) | ✅ | ✅ (dừng) | ✅ (dừng) | ✅ (full) |
| Annotation markers | — | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Time display | — | ❌ | ✅ ("--:--") | ✅ | ✅ | ✅ | ✅ |
| Play/Pause button | — | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Volume slider | — | ❌ | ✅ (disabled) | ✅ | ✅ | ✅ | ✅ |
| Volume % | — | ❌ | ✅ ("--") | ✅ | ✅ | ✅ | ✅ |
| 📍 Annotation button | — | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ |
| Close player (×) | — | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Banner "Owner đã rời" | — | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Empty state "Chọn bài" | — | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### 10.3. Annotation UI States

| State | Mô tả | Trigger |
|---|---|---|
| `NO_ANNOTATION` | Chưa có annotation nào | Sau khi chọn bài mới |
| `VIEWING` | Hiển thị markers trên progress bar | Sau khi load annotations |
| `EDITING` | Đang tạo annotation mới | User click 📍 |
| `POPUP_OPEN` | Đang xem content của marker | User click marker |

### 10.4. Music Player State Transition (v1.3 NEW)

| Transition | Trigger | UI Action |
|---|---|---|
| `HIDDEN` → `EMPTY` (or PLAYING) | User click music icon | Slide up panel 300ms |
| `EMPTY` → `LOADING` | User chọn bài từ Song Picker | Spinner + skeleton |
| `LOADING` → `PLAYING` | Audio loaded, autoplay | Progress bar starts |
| `PLAYING` → `PAUSED_BY_USER` | User click Pause | Icon toggle, time stop |
| `PLAYING` → `PAUSED_BY_OWNER_LEAVE` | WS OWNER_LEFT | Icon toggle + banner appear |
| `PAUSED_*` → `PLAYING` | User click Play | Icon toggle, time resume |
| `PLAYING` → `LOADING` | User chọn bài mới | Spinner + reset |
| `PLAYING` → `ENDED` | positionSeconds >= duration | Show "Đã hết" |
| `PLAYING` → `HIDDEN` | User click × | Slide down panel (nhạc vẫn chạy) |
| WS `MUSIC_PLAYBACK_STATE_CHANGED` | Backend broadcast | Apply state (UI all clients sync) |
| WS `ROOM_ENDED` | Phòng kết thúc | Force close player + cleanup |

---

## 11. Animation & Transition (v1.3)

### 10.1. State Transition Animation

| Transition | Animation | Duration |
|---|---|---|
| **PENDING → AUTO_REDIRECT SC-06 (v1.1)** | Fade out lobby + slide up meeting | 300ms |
| **Banner REJECTED → retry (v1.6)** | Toast "Phòng đã đầy, vui lòng thử lại sau" | 300ms |
| REJECTED → PENDING (retry) | Fade in + slide down | 200ms |
| Modal mở | Fade in + scale up (0.95 → 1) | 200ms |
| Modal đóng | Fade out + scale down | 150ms |
| Toast vào | Slide in from right | 200ms |
| Toast ra | Fade out | 200ms |
| Loading skeleton | Pulse animation | 1500ms infinite |
| **BanModal (v1.1)** | Fade in + shake (warning) | 250ms |
| **Chat panel mở (v1.2)** | Slide in from right (desktop) / Slide up from bottom (mobile) | 300ms |
| **Chat panel đóng (v1.2)** | Slide out to right (desktop) / Slide down (mobile) | 200ms |
| **Chat message mới (v1.2)** | Fade in + slide up from bottom 8px | 200ms |
| **Chat message FAILED → retry (v1.2)** | Shake warning + border pulse đỏ | 400ms |
| **Chat input typing (v1.2)** | Counter color transition (gray → orange → red) | 150ms |
| **Music Player mở (v1.3)** | Slide up from bottom (full-width bar) | 300ms |
| **Music Player đóng (v1.3)** | Slide down to bottom | 200ms |
| **Song Picker Modal mở (v1.3)** | Fade in + scale up | 200ms |
| **Song bắt đầu phát (v1.3)** | Album thumbnail pulse + progress bar slide in | 400ms |
| **Annotation marker xuất hiện (v1.3)** | Scale up + bounce | 250ms |
| **Annotation marker ACTIVE (v1.3)** | Glow effect 1.5s | — |
| **Annotation popup mở (v1.3)** | Fade in + scale up | 200ms |

### 10.2. Real-time Update Animation

| Event | Animation |
|---|---|
| Participant mới vào | Slide in tile + glow effect 1s |
| Participant rời | Fade out tile 300ms |
| **Capacity thay đổi (v1.1)** | Number counter animation + color flash (red→green khi có slot) |
| Media state change | Camera on: fade in video; Camera off: fade in avatar |
| Reconnect | Pulse overlay 1.5s |

---

## 11. Accessibility (v1.2)

| Element | ARIA | Keyboard |
|---|---|---|
| Nút "End phòng" | `aria-label="End phòng (chỉ owner)"` | Enter / Space |
| Nút "Mic toggle" | `aria-label="Tắt mic (M)"` | `M`, Enter / Space |
| Nút "Camera toggle" | `aria-label="Bật camera (V)"` | `V`, Enter / Space |
| Nút "Leave" | `aria-label="Rời phòng (Ctrl+E)"` | `Ctrl+E` / `Cmd+E`, Enter / Space |
| Modal | `role="dialog"`, `aria-modal="true"` | `Esc` đóng |
| BanModal (v1.1) | `role="alertdialog"`, `aria-modal="true"`, `aria-describedby="ban-reason"` | `Esc` đóng |
| Toast | `role="alert"` hoặc `role="status"` | — |
| Loading | `aria-busy="true"`, `aria-live="polite"` | — |
| Video tile | `aria-label="Video của {userName}"` | — |
| CapacityIndicator | `aria-live="polite"`, `aria-atomic="true"` | — |
| **Chat toggle button** (v1.2) | `aria-label="Mở chat"`, `aria-expanded={panelOpen}`, `aria-controls="chat-panel"` | `Enter` / `Space` |
| **Chat panel** (v1.2) | `role="complementary"`, `aria-label="Tin nhắn trong phòng"` | — |
| **Chat messages list** (v1.2) | `role="log"`, `aria-live="polite"`, `aria-atomic="false"` | — |
| **Chat message** (v1.2) | `aria-label="{userName} lúc {time}: {content}"` | — |
| **Chat input** (v1.2) | `aria-label="Nhập tin nhắn"`, `aria-describedby="char-counter"` | `Enter` gửi, `Shift+Enter` xuống dòng |
| **Char counter** (v1.2) | `aria-live="polite"` | — |
| **Send button** (v1.2) | `aria-label="Gửi tin nhắn"` | `Enter` / `Space` (khi focus) |
| **Unread badge** (v1.2) | `aria-label="{N} tin nhắn chưa đọc"` | — |

**v1.1**: Tooltip trên button có phím tắt được ghi rõ (M, V, Ctrl+E) cho cả người dùng sighted và screen reader.

**v1.2**: Chat panel dùng `role="log"` cho messages list để screen reader tự động thông báo message mới. `aria-live="polite"` không interrupt user.

---

## 12. Responsive Breakpoints

| Breakpoint | Layout |
|---|---|
| `< 640px` (mobile) | 1 cột, video tile full-width, controls ở bottom bar |
| `640-1024px` (tablet) | 2 cột, controls ở bottom bar |
| `> 1024px` (desktop) | 3-4 cột video grid, controls ở bottom bar, sidebar có thể mở |

**v1.1**: Prejoin screen SC-04 Phase 2 hiển thị full-width trên mobile, 2 cột (info + preview) trên desktop.

---

## 13. Testing Checklist (cho QA UI - v1.1)

### 13.1. State Transition Tests

```gherkin
Scenario: User join phòng - auto-redirect (v1.1)
  Given User U chưa join phòng P
  When U vào SC-04 Phase 2 và click "Xin vào"
  Then U chuyển sang Phase 3 PENDING
  When Owner approve U
  Then U tự động redirect SC-06 ACTIVE (v1.1 - không cần click "Vào phòng")

Scenario: Phòng đầy → REJECTED luôn (v1.6 NEW)
  Given Phòng P đã đầy (7 participants)
  And User U có JoinRequest state = PENDING
  When Owner approve U
  Then U state = REJECTED, reason = "Room is full"
  And UI U: toast "Phòng đã đầy, vui lòng thử lại sau"
  And Nút "Gửi lại yêu cầu" hiển thị
  And U KHÔNG tự động vào phòng (v1.6 bỏ auto-promote)

Scenario: REJECTED 3 lần → LOCKED → Modal "Bị ban" (v1.1 Bug 9)
  Given User U đã bị reject 2 lần
  When Owner reject U lần 3
  Then state = LOCKED
  When U click "Xin vào" lần nữa
  Then UI: Modal "Bạn đã bị ban khỏi phòng này"
  And Modal có nút "Đóng"

Scenario: KICKED → Modal KICKED
  Given User U đang ở SC-06 (ACTIVE)
  When Owner kick U
  Then UI U hiển thị modal "Bạn đã bị đuổi khỏi phòng"
  And UI U auto-redirect SC-04 sau 3s

Scenario: WS disconnect → RECONNECTING UI
  Given User U đang ở SC-06 (ACTIVE)
  When WS connection drop
  Then UI U hiển thị overlay "Đang kết nối lại..."
  And UI U hiển thị "Còn X giây trước khi rời phòng"

Scenario: Lobby user nhận ROOM_ENDED (v1.1 Bug 6)
  Given User U ở SC-04 Phase 3 state = PENDING
  When Owner end phòng P
  Then U nhận WS ROOM_ENDED
  Then U state = EXPIRED
  And UI U: "Phòng đã kết thúc. Yêu cầu của bạn đã hết hạn."
```

### 13.2. Permission UI Tests

```gherkin
Scenario: PRO-Owner thấy Owner Panel
  Given User A là PRO, owner phòng X
  When A vào SC-03a
  Then UI hiển thị Owner Panel (End/Kick/Reopen)

Scenario: PRO-Participant KHÔNG thấy Owner Panel
  Given User A là PRO, tham gia phòng Y (của owner B)
  When A vào SC-04 → SC-06
  Then UI A KHÔNG hiển thị Owner Panel
  And UI A KHÔNG hiển thị nút "End phòng"
  And UI A KHÔNG hiển thị nút "Kick"
```

### 13.3. Keyboard Shortcut Tests (v1.1)

```gherkin
Scenario: Shortcut M toggle mic
  Given User U đang ở SC-06 ACTIVE
  When U nhấn 'M'
  Then mic_on toggle
  And tooltip hiển thị "(M)" trên button

Scenario: Shortcut Ctrl+E mở Leave modal
  Given User U đang ở SC-06 ACTIVE
  When U nhấn 'Ctrl+E' (Windows) hoặc 'Cmd+E' (Mac)
  Then UI mở LeaveSession modal

Scenario: Shortcut KHÔNG hoạt động trong NOT_JOINED
  Given User U đang ở SC-06 NOT_JOINED
  When U nhấn 'M'
  Then KHÔNG toggle mic
```

### 13.4. Capacity Real-time Tests (v1.1 Bug 7)

```gherkin
Scenario: Capacity update real-time cho lobby (v1.6 - bỏ auto-promote)
  Given U ở SC-04 Phase 3 state = PENDING
  And capacity hiện tại 7/7 (U đã được reject vì phòng đầy)
  When 1 participant leave
  Then WS ROOM_CAPACITY_CHANGED broadcast {currentCount: 6}
  And UI U: "Hiện tại: 6/7 người - Còn 1 slot" update ngay
  And U vẫn ở state REJECTED (KHÔNG auto-promote)
  And U có thể click "Gửi lại yêu cầu" để retry
```

---

## 14. Out of Scope (MVP)

Các UI state **KHÔNG hỗ trợ** trong MVP:

- ❌ Animation phức tạp (chỉ fade/slide cơ bản)
- ❌ Dark mode (chỉ light mode MVP)
- ❌ Custom theme
- ❌ Accessibility nâng cao (chỉ ARIA cơ bản)
- ❌ i18n UI text (chỉ tiếng Việt MVP)
- ❌ Drag & drop sắp xếp video tile
- ❌ Picture-in-picture
- ❌ Virtual background
- ❌ Queue position (chỉ hiện count real-time)

---

**Phiên bản tiếp theo**:

- Sau khi Designer review → bổ sung chi tiết Figma mapping
- Sau khi Frontend code → cập nhật animation duration thực tế
- Sau khi QA test → bổ sung state transition phát hiện được
