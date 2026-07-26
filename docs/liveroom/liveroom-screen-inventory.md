# Liveroom — Screen Inventory

> **Mục đích**: Liệt kê TẤT CẢ màn hình cần thiết kế, phân theo role, để làm cơ sở cho UI Designer vẽ Figma và Frontend code.
>
> **Nguyên tắc**: Mỗi screen phải có **Permission Matrix** rõ ràng — UI chỉ render những gì user có quyền, **KHÔNG có button thừa**.

**Phiên bản**: v1.4 (2026-07-25)
**Căn cứ**: `liveroom-business-requirements.md` v1.7

---

**Thay đổi v1.4 (đồng bộ BR v1.7)**:
- **Tách 2 reject counter (R-REJECT-04)**: `rejectCountByOwner` (tính LOCKED) vs `rejectCountByCapacity` (không tính LOCKED)
- **Owner grace period (R-LEAVE-04 → R-LEAVE-09)**: owner leave → 60s grace → ENDED kể cả có participants (R-END-08)
- **Owner reserved slot (R-LEAVE-09)**: trong grace, max effective = max - 1
- **WS events v1.7**: `OWNER_LEFT`, `OWNER_REJOINED`, `CAPACITY_REACHED`, `ROOM_AUTO_ENDED`, `PARTICIPANT_LEFT`
- **RECONNECTING UI overlay** (R-LEAVE-08, EC-07): "Đang kết nối lại... Còn X giây"

**Thay đổi so với v1.2 (v1.3)**:
- **Thêm Music Player** vào SC-06 (bottom bar, full-width)
- **Thêm Song Picker Modal**: chọn bài từ library cá nhân (Voice module)
- **Thêm Annotation Markers** trên progress bar + popup content
- **Thêm Annotation History View** trong SC-03a/SC-07
- Music Player mặc định **collapsed** (chỉ hiển thị thanh progress + controls), expand để xem metadata
- Volume control là **GLOBAL** (1 người chỉnh → cả phòng theo)
- Thêm keyboard shortcut `A` để tạo annotation nhanh
- Thêm i18n: `LIVEROOM_MUSIC_NOT_OWN_SONG`, `LIVEROOM_ANNOTATION_EMPTY`, `LIVEROOM_ANNOTATION_TOO_LONG`

**Thay đổi v1.3 (bổ sung)**:
- **Bỏ APPROVED_WAITING state**: Approve khi phòng đầy → REJECT luôn với reason "Room is full". Lobby không còn state này.
- **Music source**: User tự upload (không lấy từ platform nào) — user chịu trách nhiệm với content

---

## 1. Tổng quan

Tổng số screen MVP: **7 screen** (giảm 1 so với v1.0 — gộp SC-04+SC-05a thành 1), phân theo 3 actor:

| Actor | Số screen | Đặc điểm |
|---|---|---|
| **Owner** (PRO, chủ phòng) | 4 | Quản lý phòng của mình |
| **Participant** (PRO/USER tham gia) | 2 | Vào (prejoin & lobby) → họp |
| **Guest** (chưa đăng nhập) | 1 | Landing page |

**Thay đổi so với v1.0**:
- **Bỏ SC-05b (Participant view riêng)** — gộp vào SC-06 với state NOT_JOINED. User đã was_approved vào phòng xem trước khi join session
- **Gộp SC-04 + SC-05a** thành **SC-04 (Prejoin & Lobby)** — flow Google Meet style: Nhập roomCode → preview mic/cam → click "Xin vào" → chờ duyệt ngay trong cùng screen

**Thay đổi so với v1.1 (v1.2)**:
- **Thêm Chat Panel** vào SC-06 (sidebar bên phải, giống Google Meet)
- Chat icon toggle trên thanh control bar (mobile + desktop)
- Chat panel mặc định **đóng** trên mobile, **mở** trên desktop (kích thước > 1024px)
- **Out of MVP**: emoji reaction, file share, edit/delete message, @mention, reply, private DM, typing indicator
- Thêm i18n messages: `LIVEROOM_CHAT_EMPTY`, `LIVEROOM_CHAT_TOO_LONG`, `LIVEROOM_NOT_IN_SESSION`

**Mục tiêu MVP**:

- Chỉ tập trung vào core flow: tạo → duyệt → vào họp → kết thúc → mở lại
- Không có tính năng thừa (chat, gửi file, reaction, ...) — để post-MVP

---

## 2. Screen theo Role

### 2.1. Owner (PRO — Chủ phòng)

#### **SC-01. `/rooms` — Danh sách phòng của Owner**

**Mục đích**: Hiển thị tất cả phòng do owner này tạo, kèm trạng thái và số participants.

**Quyền truy cập**:

- Chỉ user có role **PRO** mới vào được
- USER → redirect về `/403`

**UI elements**:

| Element | Điều kiện hiển thị |
|---|---|
| Header "Phòng của tôi" | Luôn |
| Nút "Tạo phòng mới" (`+`) | Luôn |
| Danh sách phòng (table/list) | Luôn |
| Mỗi row: tên phòng, status, số participants, nút "Vào" | Luôn |
| Filter: ACTIVE / ENDED / ALL | Luôn |
| Empty state: "Bạn chưa có phòng nào" | Khi list rỗng |

**State**:

| State | Hiển thị |
|---|---|
| Loading | Skeleton 3 row |
| Empty | Illustration + nút "Tạo phòng đầu tiên" |
| Error | Banner đỏ + nút "Thử lại" |
| Success | Danh sách phòng |

**API liên quan**:

- `GET /api/v1/liverooms?ownerId=me&status={ACTIVE|ENDED|ALL}` — danh sách phòng của owner

---

#### **SC-02. `/rooms/new` — Form tạo phòng**

**Mục đích**: Owner tạo phòng mới với capacity và cấu hình.

**Quyền truy cập**:

- Chỉ **PRO** mới vào được
- USER → 403 `LIVEROOM_PRO_REQUIRED`

**UI elements**:

| Element | Điều kiện |
|---|---|
| Form fields: tên phòng, capacity (slider 1-7) | Luôn |
| Nút "Tạo phòng" | Validate pass |
| Nút "Huỷ" | Luôn |
| Validation message inline | Khi invalid |

**Validation**:

- Tên phòng: 1-100 ký tự, unique per-owner, required
- Capacity: 1 ≤ capacity ≤ 7, default = 7

**State**:

| State | Hiển thị |
|---|---|
| Initial | Form trống, capacity default = 7 |
| Submitting | Loading spinner trên nút "Tạo phòng" |
| Error 403 | Banner: "Chỉ tài khoản PRO mới có quyền tạo phòng" |
| Error duplicate name | Inline: "Bạn đã có phòng tên này" |
| Success | Redirect sang `SC-03a` (phòng vừa tạo) |

**API liên quan**:

- `POST /api/v1/liverooms` — tạo phòng mới (R-CREATE-01 → R-CREATE-08)

---

#### **SC-03a. `/rooms/:id` — Chi tiết phòng (Owner view)**

**Mục đích**: Owner xem chi tiết phòng của mình, có toàn quyền quản lý.

**Quyền truy cập**:

- Chỉ **owner** của phòng này
- Khác owner → 403 `LIVEROOM_NOT_OWNER`

**UI elements (theo trạng thái phòng)**:

| Element | ACTIVE | ENDED |
|---|---|---|
| Tên phòng, roomCode (có nút copy) | ✅ | ✅ |
| Trạng thái "Đang hoạt động" (xanh) | ✅ | — |
| Trạng thái "Đã kết thúc" (xám) | — | ✅ |
| Danh sách participants realtime | ✅ | ✅ (read-only) |
| Danh sách JoinRequest pending | ✅ (badge "X chờ duyệt") | ❌ |
| Nút "End phòng" | ✅ | ❌ |
| Nút "Reopen phòng" | ❌ | ✅ |
| Nút "Vào phòng" (để test/host) | ✅ | — |
| Nút "Mời" (chia sẻ roomCode) | ✅ | ❌ |
| Lịch sử phòng (sessions, audit log) | — | ✅ |

**State**:

| State | Hiển thị |
|---|---|
| Loading | Skeleton |
| Room ACTIVE | Full UI với realtime updates |
| Room ENDED | UI read-only + nút Reopen |
| Error 403 | Banner |
| Error 404 | Banner "Không tìm thấy phòng" |

**API liên quan**:

- `GET /api/v1/liverooms/:id` — chi tiết phòng
- `GET /api/v1/liverooms/:id/participants` — danh sách participants
- `GET /api/v1/liverooms/:id/join-requests?status=PENDING` — danh sách chờ duyệt
- `POST /api/v1/liverooms/:id/end` — kết thúc phòng (R-END-01 → R-END-05)
- `POST /api/v1/liverooms/:id/reopen` — mở lại phòng (R-REOPEN-01 → R-REOPEN-03)
- `POST /api/v1/liverooms/:id/join-requests/:requestId/approve` — duyệt (R-APPROVE-01 → R-APPROVE-05)
- `POST /api/v1/liverooms/:id/join-requests/:requestId/reject` — từ chối (R-REJECT-01 → R-REJECT-05)

---

#### **SC-03b. `/rooms/:id/manage` — Quản lý JoinRequest (Owner)**

**Mục đích**: Owner duyệt/từ chối nhiều JoinRequest cùng lúc, kick participant.

**Quyền truy cập**:

- Chỉ **owner** + phòng **ACTIVE**

**UI elements**:

| Element | Điều kiện |
|---|---|
| Tab "Chờ duyệt" (badge count) | Có PENDING request |
| Tab "Đã duyệt" | Khi có APPROVED request |
| Tab "Đã từ chối" | Khi có REJECTED request |
| List mỗi tab: user info, thời gian gửi | Theo tab |
| Nút "Duyệt" trên mỗi row (tab Chờ duyệt) | PENDING |
| Nút "Từ chối" trên mỗi row (tab Chờ duyệt) | PENDING |
| Nút "Kick" trên mỗi participant (tab Đã duyệt) | participant đang ACTIVE |
| Realtime update khi có request mới | Luôn |
| WS event `REQUEST_REJECTED` cho owner (biết user đã bị reject) | Luôn |
| WS event `REQUEST_CANCELLED` cho owner (biết user tự huỷ) | Luôn |

**State**:

| State | Hiển thị |
|---|---|
| Tab Chờ duyệt empty | "Không có yêu cầu nào đang chờ" |
| Tab Đã duyệt empty | "Chưa có ai được duyệt" |
| Tab Đã từ chối empty | "Chưa có ai bị từ chối" |
| Error | Banner |

**API liên quan**:

- Giống SC-03a

---

### 2.2. Participant (PRO hoặc USER tham gia phòng của owner khác)

#### **SC-04. `/rooms/join` — Prejoin & Lobby (MERGED)**

> **v1.1 CHANGE**: SC-04 (nhập roomCode) + SC-05a (lobby chờ duyệt) gộp thành **1 screen duy nhất** theo flow Google Meet.

**Mục đích**: User nhập roomCode → preview mic/cam → gửi yêu cầu → chờ duyệt → tự động vào phòng. Tất cả trong cùng 1 screen (single-page flow).

**Quyền truy cập**:

- Tất cả user đã đăng nhập
- Guest (chưa đăng nhập) → redirect `/login`

**State machine của screen** (theo JoinRequest state — xem `liveroom-state-ui-mapping.md`):

| Phase | Trigger | Stage hiển thị |
|---|---|---|
| **Phase 1: CODE_INPUT** | Vào SC-04 | Input roomCode + nút "Tiếp tục" |
| **Phase 2: PREJOIN** | RoomCode hợp lệ + room tồn tại ACTIVE | Preview mic/cam + nút "Xin vào phòng" |
| **Phase 3: LOBBY** | Click "Xin vào phòng" → JoinRequest tạo (state PENDING/CANCELLED/REJECTED/EXPIRED/LOCKED) | Hiển thị theo state |
| **Phase 4: AUTO_JOIN** | WS `REQUEST_APPROVED` (còn slot) | Auto-redirect SC-06 |

**UI elements theo phase**:

**Phase 1 — CODE_INPUT**:

| Element | Điều kiện |
|---|---|
| Input roomCode (text, validate format) | Luôn |
| Nút "Tiếp tục" | Khi input valid (8 ký tự) |
| Hỗ trợ paste link `/rooms/join/{roomCode}` | Auto-fill input |
| Empty state (chưa nhập gì) | Input rỗng |

**Phase 2 — PREJOIN** (sau khi lookup room OK):

| Element | Điều kiện |
|---|---|
| Card thông tin phòng: tên, owner, capacity | Luôn |
| Preview camera (local stream) | Nếu browser hỗ trợ + user cấp quyền |
| Placeholder camera (avatar) | Nếu user deny camera |
| Test mic (level indicator) | Nếu user cấp quyền mic |
| Nút "Xin vào phòng" | Luôn (luôn available) |
| Nút "Huỷ" | Luôn |
| **Modal "Bạn đã bị ban"** | Nếu user nhấn "Xin vào" mà LOCKED (Bug 9) |
| Empty state | Không áp dụng |

**Phase 3 — LOBBY** (theo JoinRequest state - v1.7):

| Element | PENDING | REJECTED (OWNER_REJECT) | REJECTED (ROOM_FULL - v1.7) | CANCELLED | EXPIRED | LOCKED |
|---|---|---|---|---|---|---|
| Card thông tin phòng | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Capacity real-time** "Hiện tại: X/Y" | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Preview mic/cam vẫn hiển thị (giúp user setup trong lúc chờ) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Spinner "Đang chờ owner duyệt..." | ✅ | — | — | — | — | — |
| Banner "Bị từ chối bởi owner (X/3)" | — | ✅ | — | — | — | — |
| Banner "Phòng đã đầy" (v1.7) | — | — | ✅ | — | — | — |
| Icon "Đã huỷ yêu cầu" | — | — | — | ✅ | — | — |
| Icon "Phòng đã kết thúc" | — | — | — | — | ✅ | — |
| **🔒 Bị khoá** - Text "Đã bị từ chối 3 lần bởi owner. Không thể gửi yêu cầu" (CHỈ do OWNER_REJECT - v1.7) | — | — | — | — | — | ✅ |
| Nút "Huỷ yêu cầu" | ✅ | — | — | — | — | — |
| Nút "Gửi lại yêu cầu" | — | ✅ (nếu rejectCountByOwner < 3) | ✅ (luôn được) | ✅ | ✅ | ❌ |
| Nút "Về trang chủ" | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Auto-redirect | (chờ approve) | — | — | — | — | — |

**v1.7 NEW**:
- REJECTED có 2 reason: `OWNER_REJECT` (tính LOCKED) và `ROOM_FULL` (không tính LOCKED → retry tự do)
- LOCKED chỉ xảy ra khi `rejectCountByOwner >= 3` (do owner chủ động reject)
- REJECTED bởi `ROOM_FULL` → nút "Gửi lại" LUÔN enable (không bị giới hạn counter)

**State (overall screen)**:

| State | Hiển thị |
|---|---|
| Phase 1 + roomCode invalid | Inline "Mã phòng không hợp lệ" |
| Phase 1 + roomCode 404 | Banner "Không tìm thấy phòng" |
| Phase 1 + roomCode ENDED | Banner "Phòng đã kết thúc" |
| Phase 2 - Initial | Card phòng + preview camera placeholder |
| Phase 2 - Camera denied | Toast warning + placeholder avatar |
| Phase 3 - PENDING | Spinner + "Đang chờ duyệt" |
| Phase 3 - REJECTED | Banner "Bị từ chối X/3" + nút "Gửi lại" |
| Phase 3 - EXPIRED (do room ENDED) | Banner "Phòng đã kết thúc, yêu cầu hết hạn" + nút "Về trang chủ" |
| Phase 3 - LOCKED | Modal "Bạn đã bị ban khỏi phòng này" + nút "Về trang chủ" |
| Phase 4 | Auto-redirect SC-06 |
| Error | Banner lỗi |

**API liên quan**:

- `GET /api/v1/liverooms/by-code/{roomCode}` — lookup room
- `POST /api/v1/liverooms/:id/join-requests` — gửi yêu cầu (R-JOIN-01 → R-JOIN-08)
- `DELETE /api/v1/liverooms/:id/join-requests/me` — huỷ yêu cầu
- `POST /api/v1/liverooms/:id/join-requests/me/retry` — gửi lại (nếu LOCKED check `rejectCountByOwner < 3`)
- WebSocket: subscribe `REQUEST_APPROVED`, `JOIN_REQUEST_REJECTED` (v1.7: có reason), `REQUEST_CANCELLED`, `ROOM_CAPACITY_CHANGED`, `ROOM_MANUAL_ENDED`, `ROOM_AUTO_ENDED` (v1.7), `CAPACITY_REACHED` (v1.7)

---

#### **SC-06. `/rooms/:id/room` — Phòng họp chính (WebRTC)**

**Mục đích**: Giao diện phòng họp với video/audio realtime. Bao gồm cả **state NOT_JOINED** (participant view khi đã was_approved nhưng chưa join session).

**Quyền truy cập**:

- User đã được **APPROVED** + phòng **ACTIVE**
- Hoặc **owner**

**UI elements theo session state**:

| Element | NOT_JOINED | JOINING | ACTIVE | RECONNECTING | KICKED | ENDED | LEFT |
|---|---|---|---|---|---|---|---|
| Video grid (7 slots) | Placeholder | — | ✅ | ✅ | — | — | — |
| Preview camera local | ✅ | — | — | — | — | — | — |
| Spinner "Đang kết nối" | — | ✅ | — | — | — | — | — |
| Mic toggle | ❌ (disabled) | ❌ | ✅ (shortcut M) | ✅ | — | — | — |
| Camera toggle | ❌ (disabled) | ❌ | ✅ (shortcut V) | ✅ | — | — | — |
| Nút "Vào phòng" | ✅ | — | — | — | — | — | — |
| Nút "Rời phòng" | — | ✅ | ✅ (shortcut Ctrl/Cmd+E) | — | — | — | — |
| Nút "End phòng" | — | — | Owner only | Owner only | — | — | — |
| Nút "Kick" (context menu tile) | — | — | Owner only | — | — | — | — |
| **Chat toggle button** (control bar) | ❌ | — | ✅ | ✅ | — | — | — |
| **Chat panel** (sidebar phải, toggle) | ❌ | — | ✅ (optional open) | ✅ (giữ nguyên state nếu đang mở) | — | — | — |
| **RECONNECTING overlay** (v1.7) | — | — | — | ✅ ("Đang kết nối lại... Còn X giây") | — | — | — |
| Toast "Phòng sắp kết thúc" (owner left, grace) | — | — | ✅ (Participants) | ✅ | — | — | — |
| Toast "Owner đã rời — nhạc tạm dừng" (v1.7) | — | — | ✅ (Participants) | ✅ | — | — | — |
| Modal "Owner đã rời" countdown 60s (v1.7) | — | — | ✅ (Participants) | ✅ | — | — | — |
| Modal "Bạn đã bị đuổi" + auto-redirect 3s | — | — | — | — | ✅ | — | — |
| Modal "Phòng đã kết thúc" + auto-redirect 3s | — | — | — | — | — | ✅ (manual) / ✅ (auto) | — |
| Modal "Owner không quay lại — phòng đã kết thúc" (v1.7) | — | — | — | — | — | ✅ (auto, endedReason = owner_grace_expired) | — |
| Auto-redirect (silent) | — | — | — | — | — | — | ✅ |

**Chat Panel (v1.2 NEW)** — Google Meet style sidebar:

```
┌──────────────────┬─────────────────────────────┐
│  Video Grid      │  Chat Panel (sidebar phải)  │
│  (main area)     │  ┌─────────────────────────┐│
│                  │  │ [Header: "Tin nhắn" X]  ││
│                  │  ├─────────────────────────┤│
│                  │  │ an@company.com 10:30    ││
│                  │  │ Hello cả nhóm          ││
│                  │  │                         ││
│                  │  │ binh@company.com 10:31  ││
│                  │  │ Chào An                 ││
│                  │  │                         ││
│                  │  │ (auto-scroll xuống dưới)││
│                  │  ├─────────────────────────┤│
│                  │  │ [Nhập tin nhắn...][Send]││
│                  │  └─────────────────────────┘│
├──────────────────┴─────────────────────────────┤
│  Control bar (Mic, Camera, Chat icon, Leave)   │
└────────────────────────────────────────────────┘
```

**Chat Panel UI elements**:

| Element | Điều kiện hiển thị |
|---|---|
| Chat toggle button (icon chat bubble trên control bar) | ACTIVE / RECONNECTING |
| Chat panel (sidebar bên phải, width 320px desktop) | ACTIVE và đã click toggle |
| Chat panel header "Tin nhắn" + nút đóng (X) | Khi panel mở |
| Chat messages list (auto-scroll xuống dưới) | Khi panel mở |
| Empty state "Chưa có tin nhắn nào" | Khi panel mở và history = [] |
| Loading state (skeleton 3 dòng) | Khi đang fetch GET /:id/chat/messages |
| Input field "Nhập tin nhắn..." (max 500 chars) | ACTIVE và panel mở |
| Counter "X/500" (warn đỏ khi >= 450) | Khi input có text |
| Send button (icon paper plane) | Khi input có text (validated) |
| Auto-scroll xuống message mới nhất | Khi có message mới (realtime) |
| Badge unread count trên toggle button | Khi có message mới mà panel đang đóng |
| Sound notification (optional) | Khi có message mới mà panel đang đóng |

**Chat Panel layout**:

| Breakpoint | Layout |
|---|---|
| `< 640px` (mobile) | Panel chiếm 100% width (full-screen overlay, đè lên video grid). Toggle button hiển thị badge "X tin nhắn mới" |
| `640-1024px` (tablet) | Panel 320px bên phải, video grid thu nhỏ lại |
| `> 1024px` (desktop) | Panel 320px bên phải, video grid chiếm phần còn lại. Mặc định mở |

**Chat message format trong UI**:

```
[avatar 24px] an@company.com       10:30
              Hello cả nhóm
              [text wrap max 280px]

              binh@company.com       10:31
              Chào An
              [text wrap max 280px]
```

- Message của chính user: align right, màu nền xanh nhạt
- Message của người khác: align left, màu nền xám nhạt
- Display name: email (theo R-DISPLAY-01)
- Timestamp: HH:mm format, locale vi_VN
- Avatar: optional (initials của email nếu có)

**Chat validation UX** (v1.2 NEW):

| Action | Validation | Error UI |
|---|---|---|
| Type 501 chars | Tự chặn tại 500 | Counter đỏ "500/500" |
| Type chỉ space | Không cho Send | Input border đỏ + helper text "Vui lòng nhập tin nhắn" |
| Send empty | Validation fail | Inline error "LIVEROOM_CHAT_EMPTY" |
| Send > 500 | Validation fail | Inline error "LIVEROOM_CHAT_TOO_LONG" |
| Click Send trong khi offline | API fail | Toast "Lỗi kết nối" + retry button |
| Send khi bị KICKED | 403 | Auto-redirect + modal (R-CHAT-15) |

**State**:

| State | Hiển thị |
|---|---|
| Connecting | Full-screen spinner |
| In meeting | Video grid + controls |
| Media denied | Toast warning, audio-only |
| Network unstable | Toast warning |
| Owner left (grace period) | Modal countdown 60s |
| Room ended | Modal "Phòng đã kết thúc" → redirect |

**Keyboard shortcuts** (chỉ ở ACTIVE state):

| Phím | Action |
|---|---|
| `M` | Toggle mute/unmute |
| `V` | Toggle camera on/off |
| `Ctrl+E` (Windows) / `Cmd+E` (Mac) | Leave room (có confirm modal) |
| `Enter` | Send chat message (khi đang focus vào chat input) |
| `Shift+Enter` | Xuống dòng trong chat input (KHÔNG gửi) |
| `Space` | Play/Pause music (khi không focus vào input) |
| `←` / `→` | Seek -5s / +5s music (khi không focus vào input) |
| `↑` / `↓` | Volume +5% / -5% (GLOBAL, khi không focus vào input) |
| `A` | Tạo annotation tại timestamp hiện tại (R-ANNOT-01) |
| `Esc` | Đóng modal |

**Music Player UI (v1.3 NEW)** — Google Meet style bottom bar:

```
┌─────────────────────────────────────────────────────────┐
│ 🎵 [Album] Tên bài - Artist                  [×]       │
│ 0:30 ━━━●━━━━━━━━━━━━━━━━━━━ 4:12  🔊━━━━ 80% [📍]   │
│                                                         │
│              [Video Grid chính ở trên]                  │
│                                                         │
│  [Mic M] [Camera V] [Chat 💬] [Music 🎵] [Leave]       │
└─────────────────────────────────────────────────────────┘
```

**Music Player Elements (v1.3 NEW)**:

| Element | Điều kiện hiển thị | Action |
|---|---|---|
| Music icon trên control bar | ACTIVE state | Toggle Music Player mở/đóng |
| Album thumbnail (40x40px) | Khi player mở + có bài | Display |
| Tên bài + artist | Khi player mở + có bài | Display |
| Close player button (×) | Khi player mở | Đóng player (KHÔNG stop nhạc) |
| **Progress bar** với markers | Khi player mở + có bài | Click để seek, drag để scrub |
| **Annotation markers** (📍 icon) | Khi player mở + có annotation | Click → popup nội dung |
| **Current time / Duration** | Khi player mở + có bài | Update realtime |
| **Volume slider** | Khi player mở + có bài | Drag → GLOBAL volume |
| **Volume percent** (X%) | Khi player mở + có bài | Display |
| **Play/Pause button** | Khi player mở + có bài | Click → toggle |
| **📍 Đánh dấu button** | Khi player mở + có bài | Click → mở annotation modal |
| Empty state "Chưa có bài hát nào" | Khi player mở + không có bài | Hiển thị button "Chọn bài" |
| **Banner "Owner đã rời — nhạc tạm dừng"** | Khi player mở + owner đã leave | Toast persistent |

**Music Player States (v1.3 NEW)**:

| State | Hiển thị |
|---|---|
| `HIDDEN` | Player không hiển thị (default), music vẫn chạy nếu đã chọn |
| `EMPTY` | Player mở, không có bài nào đang phát |
| `PLAYING` | Player mở, bài đang phát (progress bar chạy) |
| `PAUSED_BY_USER` | User click pause |
| `PAUSED_BY_OWNER_LEAVE` | Owner leave → auto pause + banner |
| `LOADING` | Đang load audio stream từ Voice module |

**Annotation Markers (v1.3 NEW)**:

```
Progress bar với markers:
│───────●─────────────────●─────●────────────│
│       1:30              3:00  4:15         │
│       📍                📍    📍           │
│       ↓ click           ↓     ↓            │
│       Popup:            Popup: Popup:
│       "đoạn này nhạc     "giọng  "..."
│       to quá"            hay"    
```

| Element | Hiển thị |
|---|---|
| 📍 Marker tại positionSeconds | Mỗi annotation là 1 marker |
| Marker tooltip "A lúc MM:SS" | Hover marker |
| Marker popup với content + userName | Click marker |
| Active marker (highlighted khi đang ở gần timestamp đó ±2s) | Realtime |

**Song Picker Modal (v1.3 NEW)**:

```
┌────────────────────────────────────────────┐
│  Chọn bài hát từ thư viện của bạn      [X]│
├────────────────────────────────────────────┤
│ 🔍 [Tìm bài hát...]                       │
├────────────────────────────────────────────┤
│ ┌────┐                                     │
│ │♪│  Song Title 1                          │
│ │  │  Artist A • 3:45                      │
│ └────┘                                     │
│ ┌────┐                                     │
│ │♪│  Song Title 2                          │
│ │  │  Artist B • 4:12                      │
│ └────┘                                     │
│ ...                                        │
├────────────────────────────────────────────┤
│ Hiển thị: 25 / 50 bài                     │
│         [Tải lên thêm trong Voice] [Hủy]  │
└────────────────────────────────────────────┘
```

| Element | Hiển kiện |
|---|---|
| Search input | Luôn (filter theo title/artist) |
| List bài hát (của user hiện tại) | User có upload bài |
| Empty state "Bạn chưa upload bài hát nào" | User có 0 bài PROCESSED |
| Link "Tải lên thêm trong Voice" | Empty state |
| Nút "Hủy" | Luôn |
| Pagination / Lazy load | User có > 20 bài |

**Annotation Modal (v1.3 NEW)**:

```
┌────────────────────────────────────────────┐
│  Đánh dấu tại 2:30                    [X] │
├────────────────────────────────────────────┤
│  Vị trí: 2:30 / 5:00                       │
│  ┌────────────────────────────────────────┐│
│  │ Nhập ghi chú... (tối đa 200 ký tự)   ││
│  │                                        ││
│  └────────────────────────────────────────┘│
│  12/200                                    │
├────────────────────────────────────────────┤
│              [Hủy]  [Lưu]                  │
└────────────────────────────────────────────┘
```

**Annotation History View (v1.3 NEW)** — trong SC-03a (khi phòng ENDED):

```
┌────────────────────────────────────────────┐
│  Phòng: "Daily Sync" (ENDED)               │
│  Phát lúc: 2026-07-25 14:00 - 14:30        │
├────────────────────────────────────────────┤
│  Bài hát: "Tên bài" - Artist               │
│  ┌────────────────────────────────────────┐│
│  │ 0:00  ▶ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ 5:00││
│  │                                        ││
│  │       📍 1:30 - "đoạn này nhạc to quá"  ││
│  │              📍 3:00 - "giọng hát hay"   ││
│  │                                        ││
│  └────────────────────────────────────────┘│
│  Tổng: 5 ghi chú từ 3 người                │
└────────────────────────────────────────────┘
```

**API liên quan**:

- `POST /api/v1/liverooms/:id/sessions` — join session (R-SESSION-01 → R-SESSION-04)
- `POST /api/v1/liverooms/:id/sessions/me/leave` — leave session
- `PATCH /api/v1/liverooms/:id/sessions/me/media` — toggle camera/mic
- `POST /api/v1/liverooms/:id/chat/messages` (v1.2 NEW) — gửi chat message (R-CHAT-01 → R-CHAT-08)
- `GET /api/v1/liverooms/:id/chat/messages` (v1.2 NEW) — load lịch sử chat (200 messages gần nhất)
- **`GET /api/v1/liverooms/:id/music/state`** — get current playback state (read-only, R-MUSIC-05)
- **STOMP** `/app/liveroom/{roomId}/music/play` (v1.8 NEW) — chọn bài + phát (R-MUSIC-01, R-MUSIC-09 v1.8 - thay thế REST `/music/select`)
- **STOMP** `/app/liveroom/{roomId}/music/pause` (v1.8 NEW) — pause (R-MUSIC-09 v1.8)
- **STOMP** `/app/liveroom/{roomId}/music/seek` (v1.8 NEW) — seek tới positionSeconds (R-MUSIC-09 v1.8)
- **STOMP** `/app/liveroom/{roomId}/music/volume` (v1.8 NEW) — set volumePercent (R-MUSIC-09 v1.8)
- **`GET /api/v1/liverooms/:id/music/state`** (v1.3 NEW) — get current PlaybackState (sync khi join)
- **`POST /api/v1/liverooms/:id/annotations`** (v1.3 NEW) — tạo annotation (R-ANNOT-01 → R-ANNOT-08)
- **`GET /api/v1/liverooms/:id/annotations`** (v1.3 NEW) — load annotation history
- **Voice module API**: `GET /api/v1/voice/songs?userId=me&status=PROCESSED` — list bài hát cá nhân
- WebSocket: subscribe room events (JOIN/LEAVE/MEDIA_STATE/END/PARTICIPANT_CONNECTION_CHANGED/**CHAT_MESSAGE_RECEIVED**/**MUSIC_PLAYBACK_STATE_CHANGED**, **MUSIC_SONG_CHANGED**, **ANNOTATION_CREATED**, **OWNER_LEFT**, **OWNER_REJOINED** (v1.7))

---

### 2.3. Shared (Tất cả user)

#### **SC-07. `/history` — Lịch sử phòng**

**Mục đích**: User xem tất cả phòng đã từng tham gia (cả as owner và as participant).

**Quyền truy cập**:

- Tất cả user đã đăng nhập

**UI elements**:

| Element | Điều kiện |
|---|---|
| Tab "Phòng của tôi" (as owner) | Có phòng owner |
| Tab "Phòng đã tham gia" (as participant) | Có phòng participant |
| Mỗi row: tên, role (Owner/Participant), trạng thái, ngày | Theo tab |
| Pagination | Khi > 20 phòng |
| Filter theo status, theo ngày | Luôn |

**State**:

| State | Hiển thị |
|---|---|
| Loading | Skeleton |
| Empty | "Bạn chưa tham gia phòng nào" |
| Error | Banner |

**API liên quan**:

- `GET /api/v1/liverooms/history?role={owner|participant}&status={ACTIVE|ENDED}` — lịch sử

---

#### **SC-08. `/403` — Trang lỗi quyền**

**Mục đích**: Hiển thị khi user không có quyền truy cập.

**Quyền truy cập**:

- Tất cả user

**UI elements**:

| Element | Điều kiện |
|---|---|
| Icon 403 + message | Luôn |
| Nút "Về trang chủ" | Luôn |

---

## 3. Screen Relationship Map (v1.1)

```
                    ┌─────────────────┐
                    │  /login         │ (existing)
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ SC-01    │   │ SC-04    │   │ SC-07    │
        │ /rooms   │   │ /rooms/  │   │ /history │
        │ (Owner)  │   │  join    │   │          │
        └────┬─────┘   │ (Merged  │   └──────────┘
             │         │  Prejoin │
             ▼         │ + Lobby) │
        ┌──────────┐   └────┬─────┘
        │ SC-02    │        │
        │ /rooms/  │        │ Click "Xin vào"
        │   new    │        ▼
        └────┬─────┘   ┌──────────┐
             │         │ LOBBY    │
             ▼         │ state    │
        ┌──────────┐   │ PENDING  │ ──WS approve──┐
        │ SC-03a   │   │ / WAIT / │               │
        │ /rooms/  │   │ REJECTED │               ▼
        │   :id    │   │ / EXPIRE │          ┌──────────┐
        │ (Owner)  │   │ / LOCKED │          │ SC-06    │
        └────┬─────┘   └────┬─────┘          │ /rooms/  │
             │              │                │   :id/   │
             │              └──auto──>       │  room    │
             │                  redirect     │ (WebRTC) │
             │              ┌──<──<──<─┐      └──────────┘
             │              │           │
             │              ▼           ▼
             └──────────> SC-06     SC-06
                           (NOT     (ACTIVE)
                            JOINED)
```

---

## 4. Screen Ownership Summary (v1.1)

| Screen | Path | Owner | Participant | Guest |
|---|---|---|---|---|
| SC-01 | `/rooms` | ✅ (PRO only) | ❌ | ❌ |
| SC-02 | `/rooms/new` | ✅ (PRO only) | ❌ | ❌ |
| SC-03a | `/rooms/:id` (Owner view) | ✅ (chỉ owner phòng) | ❌ | ❌ |
| SC-03b | `/rooms/:id/manage` | ✅ (chỉ owner) | ❌ | ❌ |
| SC-04 | `/rooms/join` (Prejoin + Lobby merged) | ✅ (đi được, hiển thị UI khác) | ✅ | ❌ (cần login) |
| SC-06 | `/rooms/:id/room` (gồm NOT_JOINED) | ✅ | ✅ | ❌ |
| SC-07 | `/history` | ✅ | ✅ | ❌ (cần login) |
| SC-08 | `/403` | ✅ | ✅ | ✅ |

**Lưu ý v1.1**: SC-05b (Participant view riêng) đã được **bỏ**. User đã was_approved + chưa join session → vào SC-06 với state NOT_JOINED (preview + nút "Vào phòng").

---

## 5. Checklist cho Designer

Khi thiết kế Figma cho mỗi screen, đảm bảo:

- [ ] Mỗi button đều map với action trong Permission Matrix (không button thừa)
- [ ] Mỗi state UI đều map với state trong `liveroom-state-ui-mapping.md`
- [ ] Error state được thiết kế (không chỉ happy path)
- [ ] Empty state được thiết kế
- [ ] Loading state được thiết kế (skeleton)
- [ ] Realtime update được thiết kế (toast, modal khi có event)
- [ ] Mobile responsive (tối thiểu 360px width)
- [ ] Dark/Light mode (nếu có)
- [ ] Keyboard shortcuts hiển thị trong tooltip/help (SC-06)
- [ ] Prejoin UI thiết kế rõ ràng (preview camera + test mic) — SC-04 Phase 2

---

## 6. Out of Scope (MVP)

Các tính năng **KHÔNG làm** trong MVP:

- ❌ ~~Chat trong phòng~~ (v1.2: ĐÃ CÓ — R-CHAT-01 → R-CHAT-08)
- ❌ Reaction (emoji, raise hand)
- ❌ Gửi file
- ❌ Background customization
- ❌ Recording
- ❌ Whiteboard
- ❌ Screen sharing
- ❌ Breakout rooms
- ❌ Whitelist cho REJECTED (post-MVP - dùng reopen reset thay thế)
- ❌ Public/private room (MVP: private by default, share by code)
- ❌ Queue position (không cần — chỉ hiện count real-time)
- ❌ Multi-session giới hạn (giới hạn thực tế do browser WebRTC)

---

**Phiên bản tiếp theo**:

- Sau khi Designer review → cập nhật v1.3
- Sau khi có OpenAPI spec → cross-check API endpoints
- Sau khi Frontend code → cập nhật state transitions
