# Liveroom — Permission Matrix (mở rộng)

> **Mục đích**: Quy định chi tiết quyền của từng actor trên từng action, từng resource, từng state — làm cơ sở cho Frontend check quyền khi render UI, và Backend check quyền khi xử lý API.
>
> **Nguyên tắc**: **Permission theo vai trò trong phòng** (owner/participant), KHÔNG theo role hệ thống (PRO/USER). PRO ở phòng người khác = Participant thường.

**Phiên bản**: v1.4 (2026-07-25)
**Căn cứ**: `liveroom-business-requirements.md` v1.7 (R-ROLE-01 → R-ROLE-11, R-CHAT-01 → R-CHAT-08, R-MUSIC-01 → R-MUSIC-08, R-ANNOT-01 → R-ANNOT-08, R-LEAVE-04 → R-LEAVE-09, R-END-08 → R-END-09, R-CAPACITY-02, R-REJECT-04)

---

**Thay đổi v1.4 (đồng bộ BR v1.7)**:
- **Tách 2 loại reject counter (R-REJECT-04 v1.7)**:
  - `rejectCountByOwner`: REJECTED do owner chủ động → tính vào LOCKED limit (3 lần)
  - `rejectCountByCapacity`: REJECTED do phòng đầy → KHÔNG tính vào LOCKED
- **Owner grace period (R-LEAVE-04 → R-LEAVE-09)**:
  - Owner leave → 60s grace → ENDED kể cả có participants khác (R-END-08)
  - Owner có reserved slot trong grace → max effective = max - 1 (R-LEAVE-09)
- **Race condition protection (R-CAPACITY-02)**: Pessimistic lock khi approve/rejoin
- **WS events mới (v1.7)**: `OWNER_LEFT`, `OWNER_REJOINED`, `CAPACITY_REACHED`, `ROOM_AUTO_ENDED`, `PARTICIPANT_LEFT`
- **Đổi tên `Session` → `RoomSessionCycle`** (tránh nhầm với `ParticipantSession`)

**Thay đổi v1.3 (bổ sung)**:
- **Bỏ `AUTO_PROMOTE_APPROVED_WAITING`**: Không còn queue APPROVED_WAITING (v1.6 bỏ state này)
- **Bỏ `REJECT_WHEN_FULL`**: Approve khi phòng đầy → REJECT luôn với reason "Room is full" (R-APPROVE-05 v1.6)
- **Annotation visibility (v1.6)**: JoinRequest.state = APPROVED hoặc đã là ACTIVE participant (bỏ APPROVED_WAITING)
- **Music source**: User tự upload file MP3/M4A từ trước (không lấy từ platform)

---

## 1. Actor Model

Hệ thống có **4 actor** dựa trên 2 chiều (Permission Matrix dùng 4 actor; Screen Inventory gộp Participant chung):

| Chiều 1: Role hệ thống | Chiều 2: Vai trò trong phòng |
|---|---|
| **PRO** (có quyền tạo phòng) | **Owner** (của phòng cụ thể) |
| **USER** (không có quyền tạo phòng) | **Participant** (tham gia phòng của owner khác) |
| **Guest** (chưa đăng nhập) | **Anonymous** |

Kết hợp:

| Actor | Role hệ thống | Vai trò trong phòng | Mô tả |
|---|---|---|---|
| **PRO-Owner** | PRO | Owner | Tạo và quản lý phòng của mình |
| **PRO-Participant** | PRO | Participant | Join phòng của owner khác (R-ROLE-09) |
| **USER-Participant** | USER | Participant | Join phòng của owner nào đó |
| **Guest** | — | — | Chưa đăng nhập, chỉ xem landing |

**Lưu ý v1.1**: Screen Inventory chỉ cần 3 actor (Owner, Participant, Guest) vì PRO/USER chỉ khác biệt ở khả năng tạo phòng. Permission Matrix chi tiết hơn để xử lý ở mức service.

---

## 2. Resource Catalog

Các resource trong hệ thống:

| Resource | Mô tả | Owner duy nhất? |
|---|---|---|
| `Room` | Phòng họp | ✅ (1 owner) |
| `JoinRequest` | Yêu cầu tham gia phòng | ❌ (nhiều user gửi cho 1 phòng) |
| `Participant` | User đang trong phòng (session active) | ❌ (nhiều participant / phòng) |
| `MediaState` | Trạng thái camera/mic của participant | ❌ (1 per participant) |
| `Session` | Phiên làm việc của participant trong room | ❌ (1 per participant, lifecycle) |
| `RejectionTracker` | (userId, roomId) → rejectCount (v1.1) | ❌ (theo cặp) |
| `ChatMessage` (v1.2) | Text message trong phòng (group chat) | ❌ (nhiều message / room) |

**v1.1 NEW**: `RejectionTracker` lưu `rejectCount` của user cho từng phòng. Reset = 0 khi reopen (Bug 1).

**v1.2 NEW**: `ChatMessage` lưu text chat trong group channel. Lifecycle (v1.8 CLARIFIED): persist in-memory + DB trong ACTIVE, **KHÔNG cleanup khi ENDED** (giữ theo R-CHAT-06 v1.8 - audit trail). UI mặc định chỉ load 200 messages mới nhất của cycle hiện tại, có pagination API load thêm.

---

## 3. Action Catalog

Các action có thể thực hiện:

| Action | Resource | Mô tả |
|---|---|---|
| `CREATE_ROOM` | Room | Tạo phòng mới |
| `VIEW_ROOM_OWN` | Room | Xem phòng của mình (owner view) |
| `VIEW_ROOM_PARTICIPANT` | Room | Xem phòng mình tham gia (participant view) |
| `VIEW_ROOM_PUBLIC` | Room | Xem thông tin công khai (tên, capacity) |
| `END_ROOM` | Room | Kết thúc phòng |
| `REOPEN_ROOM` | Room | Mở lại phòng ENDED |
| `CREATE_JOIN_REQUEST` | JoinRequest | Gửi yêu cầu tham gia |
| `CANCEL_OWN_JOIN_REQUEST` | JoinRequest | Huỷ yêu cầu của chính mình |
| `RETRY_JOIN_REQUEST` | JoinRequest | Gửi lại yêu cầu (REJECTED/CANCELLED/EXPIRED) |
| `APPROVE_JOIN_REQUEST` | JoinRequest | Duyệt yêu cầu (owner) — **v1.1: auto-create participant** |
| `REJECT_JOIN_REQUEST` | JoinRequest | Từ chối yêu cầu (owner) |
| `REJECT_WHEN_FULL` (v1.6 NEW) | JoinRequest | Approve mà phòng đầy → REJECT luôn với reason "Room is full" (R-APPROVE-05) |
| `VIEW_JOIN_REQUESTS_OWN` | JoinRequest | Xem yêu cầu gửi đi (chính mình) |
| `VIEW_JOIN_REQUESTS_OWNED` | JoinRequest | Xem yêu cầu gửi đến (owner phòng) |
| `JOIN_SESSION` | Participant | Tạo participant record + session |
| `LEAVE_SESSION` | Participant | Rời session |
| `KICK_PARTICIPANT` | Participant | Đuổi participant (owner) |
| `TOGGLE_MEDIA` | MediaState | Bật/tắt camera/mic |
| `VIEW_PARTICIPANTS` | Participant | Xem danh sách participants |
| `VIEW_HISTORY` | Room | Xem lịch sử phòng |
| `SHOW_BAN_MODAL` (v1.1) | UI Action | Hiển thị modal "Bị ban" khi user click "Xin vào" mà LOCKED (Bug 9) |
| `USE_KEYBOARD_SHORTCUTS` (v1.1) | UI Action | Dùng M/V/Ctrl+E trong SC-06 |
| `SEND_CHAT_MESSAGE` (v1.2) | ChatMessage | Gửi text chat vào room channel |
| `LOAD_CHAT_HISTORY` (v1.2) | ChatMessage | Load 200 messages gần nhất của room |
| `OPEN_CHAT_PANEL` (v1.2) | UI Action | Mở/đóng chat panel trong SC-06 |
| `SELECT_SONG` (v1.3) | Music | Chọn bài hát của mình vào phòng (R-MUSIC-01 → R-MUSIC-02) |
| `CONTROL_PLAYBACK` (v1.3) | Music | Play/pause/seek/volume (R-MUSIC-03, last-write-wins) |
| `VIEW_PLAYBACK_STATE` (v1.3) | Music | Xem current playback state (sync khi join) |
| `CREATE_ANNOTATION` (v1.3) | Annotation | Tạo annotation tại timestamp (R-ANNOT-01 → R-ANNOT-08) |
| `VIEW_ANNOTATIONS_LIVE` (v1.3) | Annotation | Xem annotation realtime (user đã được duyệt) |
| `VIEW_ANNOTATIONS_HISTORY` (v1.3) | Annotation | Xem annotation sau ENDED (owner + participant đã ACTIVE) |

---

## 4. Permission Matrix (đầy đủ)

### 4.1. Quyền theo Actor

| Action | PRO-Owner | PRO-Participant | USER-Participant | Guest |
|---|---|---|---|---|
| **CREATE_ROOM** | ✅ | ❌ | ❌ | ❌ |
| **VIEW_ROOM_OWN** | ✅ | ❌ | ❌ | ❌ |
| **VIEW_ROOM_PARTICIPANT** | — | ✅ | ✅ | ❌ |
| **VIEW_ROOM_PUBLIC** | ✅ | ✅ | ✅ | ❌ |
| **END_ROOM** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **REOPEN_ROOM** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **CREATE_JOIN_REQUEST** | ❌ (R-ROLE-11) | ✅ | ✅ | ❌ |
| **CANCEL_OWN_JOIN_REQUEST** | ❌ | ✅ | ✅ | ❌ |
| **RETRY_JOIN_REQUEST** | ❌ | ✅ (nếu < 3 reject) | ✅ (nếu < 3 reject) | ❌ |
| **APPROVE_JOIN_REQUEST** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **REJECT_JOIN_REQUEST** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **VIEW_JOIN_REQUESTS_OWN** | ❌ | ✅ | ✅ | ❌ |
| **VIEW_JOIN_REQUESTS_OWNED** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **JOIN_SESSION** | ✅ (auto, owner phòng mình) | ✅ (auto khi APPROVED v1.1) | ✅ (auto khi APPROVED v1.1) | ❌ |
| **LEAVE_SESSION** | ✅ | ✅ | ✅ | ❌ |
| **KICK_PARTICIPANT** | ✅ (chỉ phòng mình) | ❌ | ❌ | ❌ |
| **TOGGLE_MEDIA** | ✅ | ✅ | ✅ | ❌ |
| **VIEW_PARTICIPANTS** | ✅ | ✅ | ✅ | ❌ |
| **VIEW_HISTORY** | ✅ | ✅ | ✅ | ❌ |
| **SEND_CHAT_MESSAGE** (v1.2) | ✅ (khi participant.state = ACTIVE) | ✅ (khi participant.state = ACTIVE) | ✅ (khi participant.state = ACTIVE) | ❌ |
| **LOAD_CHAT_HISTORY** (v1.2) | ✅ (khi participant.state = ACTIVE) | ✅ (khi participant.state = ACTIVE) | ✅ (khi participant.state = ACTIVE) | ❌ |
| **OPEN_CHAT_PANEL** (v1.2 UI) | ✅ | ✅ | ✅ | ❌ |
| **SELECT_SONG** (v1.3) | ✅ (khi ACTIVE, chỉ chọn bài của mình) | ✅ (khi ACTIVE, chỉ chọn bài của mình) | ✅ (khi ACTIVE, chỉ chọn bài của mình) | ❌ |
| **CONTROL_PLAYBACK** (v1.3) | ✅ (khi ACTIVE) | ✅ (khi ACTIVE) | ✅ (khi ACTIVE) | ❌ |
| **VIEW_PLAYBACK_STATE** (v1.3) | ✅ (khi ACTIVE) | ✅ (khi ACTIVE) | ✅ (khi ACTIVE) | ❌ |
| **CREATE_ANNOTATION** (v1.3) | ✅ (khi ACTIVE + đã duyệt phiên hiện tại) | ✅ (khi ACTIVE + đã duyệt phiên hiện tại) | ✅ (khi ACTIVE + đã duyệt phiên hiện tại) | ❌ |
| **VIEW_ANNOTATIONS_LIVE** (v1.3) | ✅ (khi ACTIVE + đã duyệt) | ✅ (khi ACTIVE + đã duyệt) | ✅ (khi ACTIVE + đã duyệt) | ❌ |
| **VIEW_ANNOTATIONS_HISTORY** (v1.3) | ✅ (owner phòng) | ✅ (participant đã ACTIVE trong phiên) | ✅ (participant đã ACTIVE trong phiên) | ❌ |
| **SHOW_BAN_MODAL** (UI) | — | ✅ (UI tự trigger khi 403 LOCKED) | ✅ | — |
| **USE_KEYBOARD_SHORTCUTS** | ✅ (trong phòng mình) | ✅ (trong phòng đang join) | ✅ | ❌ |

**Legend**: ✅ = Có quyền | ❌ = Không có quyền | — = Không áp dụng

**v1.1 NEW**:
- `JOIN_SESSION` cho PRO/USER-Participant giờ là "auto" khi APPROVED — user không cần click riêng
- `SHOW_BAN_MODAL` là UI behavior khi nhận 403 LOCKED (không phải business action)
- `USE_KEYBOARD_SHORTCUTS` là UI capability (M/V/Ctrl+E) trong SC-06 ACTIVE

**v1.2 NEW**:
- `SEND_CHAT_MESSAGE`: chỉ ACTIVE participant mới gửi được (cả owner)
- `LOAD_CHAT_HISTORY`: chỉ ACTIVE participant mới load được (auto-call khi vào SC-06 ACTIVE)
- `OPEN_CHAT_PANEL`: UI capability, tất cả ACTIVE participant đều có (kể cả owner)
- KHÔNG phân biệt owner/participant về chat — mọi người đều bình đẳng trong chat (R-CHAT-02)

**v1.3 NEW**:
- `SELECT_SONG`: chỉ ACTIVE participant, chỉ bài của chính mình (Song.userId = actor.userId)
- `CONTROL_PLAYBACK`: mọi ACTIVE participant đều điều khiển được play/pause/seek/volume GLOBAL
- Volume GLOBAL sync (R-MUSIC-03): khi ai chỉnh → cả phòng theo
- Owner leave → auto PAUSE nhạc (R-MUSIC-06)
- `CREATE_ANNOTATION`: chỉ ACTIVE participant đã được duyệt TRONG PHIÊN HIỆN TẠI (R-ANNOT-02, R-ANNOT-03)
- "Đã được duyệt trong phiên" = JoinRequest.state = APPROVED hoặc đã là ACTIVE participant (bỏ APPROVED_WAITING từ v1.6)
- Annotation chỉ gắn với phiên hiện tại, reset on reopen (R-ANNOT-05)

### 4.2. Giải thích chi tiết từng quyền

#### CREATE_ROOM

```
Điều kiện: actor.role = PRO
Nếu actor.role = USER hoặc Guest → 403 LIVEROOM_PRO_REQUIRED
```

#### END_ROOM

```
Điều kiện: actor.role = PRO AND actor = room.owner AND room.status = ACTIVE
Nếu room.status = ENDED → 409 LIVEROOM_CANNOT_END
Nếu actor không phải owner → 403 LIVEROOM_NOT_OWNER
v1.1: actor.role cũng phải là PRO (không phải downgrade giữa phiên)
```

#### REOPEN_ROOM (v1.1)

```
Điều kiện:
  - actor.role = PRO (current role, không phải role lúc tạo)
  - actor = room.owner
  - room.status = ENDED
Nếu actor.role = USER (hiện tại) → 403 LIVEROOM_PRO_REQUIRED
Nếu room.status = ACTIVE → 409 LIVEROOM_CANNOT_REOPEN
v1.1 side-effect: rejectCount của tất cả user cho phòng này reset = 0 (Bug 1)
```

#### APPROVE_JOIN_REQUEST (v1.7 phân biệt reject reason)

```
Điều kiện: actor.role = PRO AND actor = room.owner AND room.status = ACTIVE
Nếu request.state ≠ PENDING → 409 LIVEROOM_REQUEST_NOT_PENDING
Nếu actor không phải owner → 403 LIVEROOM_NOT_OWNER

Logic khi approve (v1.7):
  1. Race condition protection (R-CAPACITY-02): pessimistic lock trên LiveRoom row
  2. Check slot available (current_count < max - reservedOwnerSlot)
  3. Nếu còn slot:
     - Update JoinRequest.state = APPROVED
     - Auto-create Participant record + RoomSessionCycle (atomic)
     - Mark was_approved = TRUE
     - WS push REQUEST_APPROVED { autoJoin: true, roomState } → user auto-redirect SC-06
  4. Nếu hết slot (v1.7 NEW):
     - Update JoinRequest.state = REJECTED với reason = "ROOM_FULL"
     - increment rejectCountByCapacity (KHÔNG tính LOCKED)
     - WS push JOIN_REQUEST_REJECTED { reason: "ROOM_FULL" } → user hiển thị toast "Phòng đã đầy"
     - KHÔNG có queue, KHÔNG auto-promote
```

#### CREATE_JOIN_REQUEST (v1.1 LOCKED check)

```
Điều kiện:
  - actor ≠ room.owner
  - actor chưa có active JoinRequest (PENDING/APPROVED)
  - v1.1: actor.rejectCount cho room này < 3 (không trong RejectionTracker LOCKED state)
Nếu actor = room.owner → 400 LIVEROOM_CANNOT_JOIN_OWN_ROOM (R-ROLE-11)
Nếu actor đã có active request → 409 LIVEROOM_ALREADY_REQUESTED
v1.1: Nếu actor đang LOCKED → 403 LIVEROOM_REQUEST_LOCKED
  - UI nhận 403 → SHOW_BAN_MODAL → user click Đóng → vẫn ở Phase 2 PREJOIN
```

#### APPROVE_JOIN_REQUEST (v1.6 R-APPROVE-02 + R-APPROVE-05)

```
Actor: Owner của room
Pre-condition: actor = room.owner, room.state = ACTIVE

Logic (v1.6):
  1. Backend check room.currentParticipantCount >= room.maxParticipants
     - Nếu ĐẦY → set JoinRequest.state = REJECTED với reason "Room is full, please try later"
       - Return 409 LIVEROOM_ROOM_FULL
       - KHÔNG tạo participant, KHÔNG có queue
       - User phải gửi JoinRequest mới sau khi có slot trống
  2. Nếu còn slot:
     - Update JoinRequest.state = APPROVED
     - Auto-create Participant record + Session
     - WS push REQUEST_APPROVED cho user → user ở lobby auto-redirect SC-06
     - WS push ROOM_CAPACITY_CHANGED cho tất cả

So với v1.5: bỏ APPROVED_WAITING state, bỏ auto-promote queue.
Đơn giản hơn, tránh race condition, giảm complexity.
```

#### REJECT_WHEN_FULL (v1.6 R-APPROVE-05 - chạy trong APPROVE flow)

```
Trigger: Khi owner approve mà room đầy
Logic:
  1. Không tạo participant
  2. Set JoinRequest.state = REJECTED, reason = "Room is full"
  3. WS push JOIN_REQUEST_REJECTED cho user
  4. Frontend: toast "Phòng đã đầy, vui lòng thử lại sau"
  5. User có thể gửi JoinRequest mới sau khi thấy capacity real-time giảm
```

#### REJECT_JOIN_REQUEST (v1.7 tách counter)

```
Điều kiện: actor.role = PRO AND actor = room.owner AND room.status = ACTIVE
Logic:
  1. Update JoinRequest.state = REJECTED với rejectionReason = "OWNER_REJECT"
  2. rejectCountByOwner += 1 (CÓ tính LOCKED - v1.7)
  3. Nếu rejectCountByOwner >= 3 → JoinRequest state = LOCKED
  4. WS push JOIN_REQUEST_REJECTED { reason: "OWNER_REJECT", rejectCountByOwner } cho user
  5. WS push REQUEST_REJECTED cho owner (reset UI)
```

#### OWNER_LEAVE_GRACE (v1.7 - R-LEAVE-04 → R-LEAVE-09)

```
Trigger: Khi owner leave phòng ACTIVE
Logic (v1.7):
  1. Set owner_left_at = now, reservedOwnerSlot = true
  2. max effective capacity = maxParticipants - 1 (giữ slot cho owner)
  3. WS push OWNER_LEFT { ownerLeftAt, graceExpiresAt = now + 60s }
  4. Participants: banner "Owner đã rời — phòng sắp kết thúc sau Xs"
  5. Music: pause nhạc (R-MUSIC-06)
  6. Empty room timeout bị SUSPEND trong grace (R-LEAVE-08)

Nếu owner rejoin trong 60s:
  1. owner_left_at = null, reservedOwnerSlot = false
  2. max effective = maxParticipants
  3. WS push OWNER_REJOINED
  4. Music: KHÔNG auto resume (R-MUSIC-06)
  5. Banner đóng

Nếu grace hết (60s) mà owner không rejoin:
  1. Phòng → ENDED với endedReason = "owner_grace_expired" (R-END-08 v1.7)
  2. Áp dụng KỂ CẢ KHI PHÒNG CÒN PARTICIPANTS KHÁC (v1.7 thay đổi)
  3. WS push ROOM_AUTO_ENDED { reason: "owner_grace_expired" }
  4. Participants: modal "Owner không quay lại — phòng đã kết thúc"
```

#### RACE_CONDITION_PROTECTION (R-CAPACITY-02 - v1.7 NEW)

```
Áp dụng cho: Approve JoinRequest, Rejoin, Owner rejoin
Logic:
  1. Sử dụng pessimistic lock: SELECT ... FOR UPDATE trên LiveRoom row
  2. Hoặc optimistic lock với @Version + retry logic
  3. Re-check capacity SAU khi lock
  4. Nếu hết slot → REJECT_WHEN_FULL hoặc trả 409 LIVEROOM_ROOM_FULL

EC-24: Khi 2 user approve/rejoin đồng thời → chỉ 1 thắng, user còn lại
nhận REJECT_WHEN_FULL hoặc "phòng đầy"
```

#### KICK_PARTICIPANT

```
Điều kiện: actor.role = PRO AND actor = room.owner AND room.status = ACTIVE
  AND target.participant.status = ACTIVE
Nếu target.participant.userId = room.ownerId → 400 LIVEROOM_CANNOT_KICK_OWNER
```

#### USE_KEYBOARD_SHORTCUTS (v1.1)

```
Áp dụng: Chỉ trong SC-06 ACTIVE state
- 'M': toggle microphone (gọi TOGGLE_MEDIA với mic)
- 'V': toggle camera (gọi TOGGLE_MEDIA với camera)
- 'Ctrl+E' (Windows) hoặc 'Cmd+E' (Mac): mở LeaveSession confirm modal
- 'Esc': đóng modal đang mở
Điều kiện: actor đang trong session ACTIVE (không áp dụng khi NOT_JOINED/JOINING)
```

#### MULTI-SESSION (R-ROLE-10)

```
Actor có thể đồng thời:
  - Owner phòng A (WS session key: userId+roomA)
  - Participant phòng B (WS session key: userId+roomB)
  - Participant phòng C (WS session key: userId+roomC)
Giới hạn nghiệp vụ: Không giới hạn
Giới hạn kỹ thuật (v1.1): Browser thường hỗ trợ ~5-10 peer connection/tab
  → Backend KHÔNG enforce giới hạn, user tự quản lý
```

#### SEND_CHAT_MESSAGE (v1.2 NEW)

```
Điều kiện:
  - actor có participant.state = ACTIVE trong phòng
  - room.status = ACTIVE
Nếu actor không trong session (LEFT, KICKED, ENDED, OFFLINE) → 403 LIVEROOM_NOT_IN_SESSION
Nếu actor ở lobby (PENDING/REJECTED/...) → 403 (chưa join session)
Nếu room ENDED → 409 LIVEROOM_ROOM_ENDED
Validation:
  - content.length > 0 sau trim
  - content.length <= 500
Nếu empty → 400 LIVEROOM_CHAT_EMPTY
Nếu > 500 → 400 LIVEROOM_CHAT_TOO_LONG

Sau khi validate pass:
  - Backend persist ChatMessage vào DB
  - Trim rolling window (nếu > 200 messages → xoá cũ nhất)
  - WS broadcast CHAT_MESSAGE_RECEIVED cho tất cả ACTIVE participant khác
```

#### LOAD_CHAT_HISTORY (v1.2 NEW)

```
Điều kiện:
  - actor có participant.state = ACTIVE trong phòng
  - room.status = ACTIVE
Nếu room ENDED → 409 LIVEROOM_ROOM_ENDED (user sắp redirect)

Response:
  - 200 OK với array ChatMessage (limit 200, newest first)
  - Empty list nếu chưa có chat nào
```

#### OPEN_CHAT_PANEL (v1.2 NEW - UI)

```
UI capability: Mở/đóng chat panel trong SC-06
Điều kiện: actor.participant.state = ACTIVE
UI:
  - Click toggle button (icon chat bubble) → mở panel
  - Click X (close) trên panel header → đóng panel
  - Click toggle button khi panel đang mở → đóng
Không liên quan đến role (owner/participant đều mở được)
Không liên quan đến backend (chỉ UI state)
```

---

## 5. State-based Permission

### 5.1. JoinRequest State → Available Actions (v1.7)

| State | CREATE_JOIN_REQUEST | CANCEL_OWN | RETRY | APPROVE | REJECT | AUTO_PROMOTE |
|---|---|---|---|---|---|---|
| (chưa có request) | ✅ (nếu rejectCountByOwner < 3) | — | — | — | — | — |
| PENDING | ❌ | ✅ | — | ✅ (auto-create participant nếu còn slot, pessimistic lock) | ✅ | — |
| APPROVED | ❌ | — | — | ❌ | ❌ | ❌ |
| REJECTED (ROOM_FULL - v1.7) | ✅ (không bị giới hạn bởi counter) | — | ✅ | ❌ | ❌ | ❌ |
| REJECTED (OWNER_REJECT, count < 3) | ❌ | — | ✅ | — | — | — |
| REJECTED (OWNER_REJECT, count = 3) | ❌ (→ LOCKED check) | — | ❌ | — | — | — |
| CANCELLED | ❌ | — | ✅ | — | — | — |
| EXPIRED | ❌ | — | ✅ | — | — | — |
| LOCKED | ❌ | — | ❌ | — | — | — |

**v1.7 NEW**:
- `rejectCountByOwner` (do owner chủ động reject) → tính vào LOCKED limit (3 lần)
- `rejectCountByCapacity` (do phòng đầy) → KHÔNG tính vào LOCKED, user retry tự do
- Không phân biệt trên UI: REJECTED state thống nhất, chỉ khác `rejectionReason` field
- Reject reason trong payload WS để frontend hiển thị message phù hợp

### 5.2. Room Status → Available Actions (v1.7)

| Status | VIEW_ROOM_OWN | END | REOPEN | JOIN_SESSION | REJOIN | OWNER_LEAVE |
|---|---|---|---|---|---|---|
| ACTIVE | ✅ | ✅ | ❌ | ✅ (khi APPROVED + còn slot) | ✅ (was_approved + còn slot, pessimistic lock) | ✅ (bắt đầu grace 60s) |
| ACTIVE (grace) | ✅ | ✅ | ❌ | ✅ (slot reserved cho owner, max effective = max - 1) | ✅ | ✅ (cancel grace) |
| ENDED | ✅ (read-only) | ❌ | ✅ | ❌ | ❌ | ❌ |

**v1.7 NEW**:
- ACTIVE (grace) là state trung gian khi owner leave. WS `OWNER_LEFT` broadcast.
- Empty room timeout bị SUSPEND trong grace (R-LEAVE-08).
- `reopenedCount` (R-REOPEN-04.1) tăng mỗi lần reopen, `started_at` reset = now() để empty timeout chính xác.

### 5.3. Participant Session Status → Available Actions (v1.7)

| Status | LEAVE | KICK | TOGGLE_MEDIA | KEYBOARD_SHORTCUTS |
|---|---|---|---|---|
| NOT_JOINED (SC-06 v1.1) | ❌ | ❌ | ❌ (camera preview vẫn chạy local) | ❌ |
| JOINING | ✅ | ❌ | ❌ | ❌ |
| ACTIVE | ✅ | ✅ (owner) | ✅ | ✅ (M, V, Ctrl/Cmd+E) |
| RECONNECTING | ✅ | ✅ | ❌ (UI overlay "Đang kết nối lại..." + countdown 60s) | ❌ |
| KICKED | ❌ | ❌ | ❌ | ❌ |
| ENDED | ❌ | ❌ | ❌ | ❌ |
| OFFLINE (grace) | ✅ | ✅ | ❌ | ❌ |

**v1.1 NEW**: NOT_JOINED state là screen SC-06 cho user đã was_approved nhưng chưa click "Vào phòng". Keyboard shortcuts chỉ ACTIVE trong ACTIVE state.

**v1.7 NEW**:
- RECONNECTING có UI overlay "Đang kết nối lại... Còn X giây trước khi rời phòng" (grace 60s)
- Khi timeout 60s → state = OFFLINE, slot được giải phóng
- Reconnect fail cho owner → set owner_left_at → grace 60s (R-LEAVE)
- Reconnect fail cho participant thường → slot trống → trigger đếm capacity lại

---

## 6. UI Rendering Rules (v1.1)

### 6.1. Frontend Check Pattern

```typescript
function canActorDoAction(actor: Actor, action: Action, resource: Resource): boolean {
  const matrix = PERMISSION_MATRIX[action];
  return matrix[actor.type] === true;
}

function shouldRenderButton(buttonId: string, context: { actor: Actor; room?: Room }): boolean {
  const action = BUTTON_TO_ACTION[buttonId];
  return canActorDoAction(context.actor, action, context.room);
}

function shouldShowBanModal(errorCode: string): boolean {
  return errorCode === 'LIVEROOM_REQUEST_LOCKED';
}

function isKeyboardShortcutAvailable(currentState: SessionState): boolean {
  return currentState === 'ACTIVE';
}
```

### 6.2. UI Examples (v1.1)

```tsx
// SC-04 Phase 2 PREJOIN
function PrejoinScreen({ room, userState }: { room: Room; userState: JoinRequestState }) {
  const [showBanModal, setShowBanModal] = useState(false);

  async function handleXinVao() {
    try {
      await joinRequestApi.create(room.id);
    } catch (err) {
      if (err.code === 'LIVEROOM_REQUEST_LOCKED') {
        setShowBanModal(true);  // v1.1: hiển thị modal Bị ban
      }
    }
  }

  return (
    <div>
      <RoomCard room={room} />
      <CapacityIndicator room={room} />  {/* Realtime count */}
      <MediaPreview />

      {userState !== 'LOCKED' && <button onClick={handleXinVao}>Xin vào phòng</button>}

      {showBanModal && <BanModal onClose={() => setShowBanModal(false)} />}
    </div>
  );
}

// SC-06 ACTIVE
function MeetingControls({ actor, room, sessionState }: ...) {
  const shortcutsEnabled = isKeyboardShortcutAvailable(sessionState);

  useKeyboardShortcut('m', () => toggleMic(), { enabled: shortcutsEnabled });
  useKeyboardShortcut('v', () => toggleCamera(), { enabled: shortcutsEnabled });
  useKeyboardShortcut('mod+e', () => openLeaveModal(), { enabled: shortcutsEnabled });

  return (
    <div className="meeting-controls">
      {canActorDoAction(actor, 'TOGGLE_MEDIA', room) && <MicToggleButton tooltip="M" />}
      {canActorDoAction(actor, 'TOGGLE_MEDIA', room) && <CameraToggleButton tooltip="V" />}
      {canActorDoAction(actor, 'LEAVE_SESSION', room) && <LeaveButton tooltip="Ctrl+E" />}

      {canActorDoAction(actor, 'END_ROOM', room) && <EndRoomButton />}
      {canActorDoAction(actor, 'KICK_PARTICIPANT', room) && (
        <ParticipantContextMenu>
          <KickMenuItem userId={participant.userId} />
        </ParticipantContextMenu>
      )}
    </div>
  );
}
```

### 6.3. Quy tắc render

1. **Không disable button** khi user không có quyền — **ẩn hoàn toàn**
2. **Không hiển thị tooltip "Bạn không có quyền"** cho button bị ẩn
3. **Dựa vào actor + room.role** để quyết định render, không hardcode
4. **Nếu backend trả 403** → hiển thị banner lỗi (hoặc modal nếu là LOCKED - v1.1), không redirect
5. **Keyboard shortcuts**: Hiển thị trong tooltip của button tương ứng (M/V/Ctrl+E)

---

## 7. Backend Enforcement Rules

### 7.1. Layer Check

```
Layer 1: Authentication Filter
  → Kiểm tra JWT token, lấy userId + role

Layer 2: Authorization Interceptor (Liveroom)
  → Kiểm tra user.role = PRO cho CREATE_ROOM
  → Kiểm tra user = room.owner cho END/REOPEN/APPROVE/REJECT/KICK
  → v1.1: Kiểm tra rejectCount < 3 cho CREATE_JOIN_REQUEST

Layer 3: Service-level check
  → Kiểm tra state transitions (R-X-X-XX)
  → Kiểm tra business rules (capacity real-time check tại approve, grace period, empty room, ...)
  → v1.6: REJECT_WHEN_FULL chạy trong approve flow (R-APPROVE-05)

Layer 4: Database constraint
  → Unique constraint, foreign key, check constraint
```

### 7.2. Response Codes (v1.2)

| Error | HTTP Status | Error Code |
|---|---|---|
| Chưa đăng nhập | 401 | `UNAUTHORIZED` |
| Không có role PRO | 403 | `LIVEROOM_PRO_REQUIRED` |
| Không phải owner | 403 | `LIVEROOM_NOT_OWNER` |
| **Bị LOCKED (rejectCount = 3 trong phiên)** | **403** | **`LIVEROOM_REQUEST_LOCKED`** |
| **Không trong session (chat)** | **403** | **`LIVEROOM_NOT_IN_SESSION`** (v1.2) |
| Phòng ENDED | 409 | `LIVEROOM_ROOM_ENDED` |
| Phòng không ACTIVE (khi reopen) | 409 | `LIVEROOM_CANNOT_REOPEN` |
| Phòng đầy | 409 | `LIVEROOM_ROOM_FULL` |
| Đã có request active | 409 | `LIVEROOM_ALREADY_REQUESTED` |
| Tên phòng trùng | 409 | `LIVEROOM_ROOM_NAME_DUPLICATE` |
| Request đã xử lý | 409 | `LIVEROOM_REQUEST_NOT_PENDING` |
| Tự join phòng mình | 400 | `LIVEROOM_CANNOT_JOIN_OWN_ROOM` |
| Tự kick chính mình | 400 | `LIVEROOM_CANNOT_KICK_OWNER` |
| **Chat message empty** | **400** | **`LIVEROOM_CHAT_EMPTY`** (v1.2) |
| **Chat message too long (> 500)** | **400** | **`LIVEROOM_CHAT_TOO_LONG`** (v1.2) |
| **Music not playing** (v1.3) | **400** | **`LIVEROOM_MUSIC_NOT_PLAYING`** |
| **Music invalid position** (v1.3) | **400** | **`LIVEROOM_MUSIC_INVALID_POSITION`** |
| **Music invalid volume** (v1.3) | **400** | **`LIVEROOM_MUSIC_INVALID_VOLUME`** |
| **Song not ready** (v1.3) | **409** | **`LIVEROOM_MUSIC_NOT_READY`** |
| **Song not owned by user** (v1.3) | **403** | **`LIVEROOM_MUSIC_NOT_OWN_SONG`** |
| **Annotation empty** (v1.3) | **400** | **`LIVEROOM_ANNOTATION_EMPTY`** |
| **Annotation too long** (v1.3) | **400** | **`LIVEROOM_ANNOTATION_TOO_LONG`** |
| **Annotation invalid position** (v1.3) | **400** | **`LIVEROOM_ANNOTATION_INVALID_POSITION`** |
| **Annotation not approved** (v1.3) | **403** | **`LIVEROOM_ANNOTATION_NOT_APPROVED`** |
| Resource không tồn tại | 404 | `LIVEROOM_ROOM_NOT_FOUND` / `LIVEROOM_REQUEST_NOT_FOUND` |

---

## 8. Permission Change Events (v1.1)

| Event | Trigger | Action |
|---|---|---|
| User upgrade USER → PRO | Admin thay đổi role | WS push `ROLE_CHANGED` tới user → unlock nút "Tạo phòng" |
| User downgrade PRO → USER | Admin thay đổi role | **v1.7 (R-ROLE-06)**: Force-end phòng ACTIVE do user TẠO (owner) → WS push `ROOM_AUTO_ENDED`. Phòng user là participant ở phòng khác → KHÔNG bị ảnh hưởng. v1.1: ngay cả sau upgrade lại → có thể reopen |
| User bị reject 3 lần OWNER trong phiên | Owner reject lần 3 (`rejectCountByOwner = 3`) | WS push `REQUEST_LOCKED` tới user → modal Bị ban mỗi lần click "Xin vào" (v1.1 Bug 9) |
| User bị reject capacity | Phòng đầy khi owner approve | **v1.7 (R-REJECT-04)**: `rejectCountByCapacity` tăng nhưng KHÔNG LOCKED. WS push `JOIN_REQUEST_REJECTED { reason: "ROOM_FULL" }` |
| Phòng ENDED thủ công | Owner end | WS push `ROOM_MANUAL_ENDED` cho CẢ user có active JoinRequest (lobby) → user thấy "Phòng đã kết thúc" |
| Phòng AUTO ENDED (v1.7) | Owner grace hết / empty timeout | WS push `ROOM_AUTO_ENDED { reason: "owner_grace_expired" \| "empty_timeout" }` |
| Phòng REOPENED | Owner reopen | WS push `ROOM_REOPENED` tới owner → refresh SC-03a. **v1.1 (Bug 1)**: rejectCountByOwner reset, user từng LOCKED có thể thử lại |
| User được approve + còn slot | Owner approve | **v1.1 (Bug 4)**: Auto-create participant + WS push `REQUEST_APPROVED` → user auto-redirect SC-06 (không cần click "Vào phòng") |
| User REJECTED (room full) + có slot | User tự retry | **v1.6/v1.7**: User click "Gửi lại yêu cầu" → POST /join-requests → owner duyệt lại → vào phòng. KHÔNG có auto-promote. Counter `rejectCountByCapacity` KHÔNG giới hạn. |
| Capacity thay đổi | Participant join/leave/timeout | **v1.1 (R-CAPACITY-01)**: WS push `ROOM_CAPACITY_CHANGED` cho lobby user → hiển thị count real-time. **v1.7**: payload thêm `reservedOwnerSlot` |
| Capacity đầy | currentCount >= maxParticipants | **v1.7 NEW**: WS push `CAPACITY_REACHED` cho lobby user → frontend tắt nút "Xin vào" |
| User bị kick | Owner kick | WS push `PARTICIPANT_KICKED` tới user → modal + redirect |
| Owner leave (grace) | Owner leave | **v1.7 NEW**: WS push `OWNER_LEFT { graceExpiresAt }` → UI banner "Phòng sắp kết thúc sau Xs" |
| Owner rejoin (grace) | Owner rejoin < 60s | **v1.7 NEW**: WS push `OWNER_REJOINED` → UI đóng banner |
| Participant leave | User leave chủ động | **v1.7 NEW**: WS push `PARTICIPANT_LEFT` (vs `PARTICIPANT_KICKED`) để UI phân biệt |

---

## 9. Audit Trail

Mọi action quan trọng phải ghi audit log:

| Action | Log fields | v1.1 update |
|---|---|---|
| CREATE_ROOM | actorId, roomId, roomName, capacity, timestamp | |
| END_ROOM | actorId, roomId, reason (manual/auto/force), timestamp | reason có thể là: `manual` / `grace_expired` / `empty_5min` / `force_role_change` |
| REOPEN_ROOM | actorId, roomId, timestamp | v1.1: thêm `resetRejectCount: true` |
| APPROVE_JOIN_REQUEST | actorId, roomId, requestId, targetUserId, autoJoin (true/false), timestamp | v1.1: `autoJoin=true` cho còn slot, `autoJoin=false` cho phòng đầy |
| REJECT_JOIN_REQUEST | actorId, roomId, requestId, targetUserId, reason, rejectCountAfter, timestamp | v1.1: log rejectCount để debug |
| AUTO_PROMOTE (R-APPROVE-05) | systemActor, roomId, requestId, targetUserId, reason='slot_available' | v1.1 NEW |
| KICK_PARTICIPANT | actorId, roomId, targetUserId, reason, timestamp | |
| ROLE_CHANGE | adminId, targetUserId, oldRole, newRole, timestamp | |

---

## 10. Edge Cases & Special Rules

### 10.1. PRO join phòng khác (R-ROLE-09)

```
PRO A muốn join phòng của PRO B:
- A có quyền CREATE_JOIN_REQUEST cho phòng B
- B có quyền APPROVE/REJECT
- Khi A được approve và còn slot → AUTO-JOIN (v1.1)
- A chỉ có quyền TOGGLE_MEDIA + LEAVE_SESSION trong phòng B
```

### 10.2. Multi-session (R-ROLE-10 v1.1)

```
PRO A vừa là owner phòng X, vừa là participant phòng Y:
- Ở phòng X: A = PRO-Owner → full quyền (trừ KICK chính mình)
- Ở phòng Y: A = PRO-Participant → TOGGLE_MEDIA + LEAVE + KEYBOARD_SHORTCUTS
- Quyền độc lập hoàn toàn theo (actor, room)
- v1.1 limitation: browser giới hạn ~5-10 peer/tab, KHÔNG enforce ở backend
```

### 10.3. Owner rời phòng (grace period - v1.7 NEW rules)

```
Owner leave phòng đang ACTIVE:
- Owner mất quyền END_ROOM tạm thời (đã leave)
- Set owner_left_at = now, reservedOwnerSlot = true
- max effective capacity = maxParticipants - 1 (giữ slot cho owner - R-LEAVE-09)
- Backend bắt đầu grace 60s (R-LEAVE-04)
- Empty room 5min timeout bị SUSPEND trong grace (R-LEAVE-08)
- Music pause + banner "Owner đã rời" (R-MUSIC-06)
- WS OWNER_LEFT broadcast (graceExpiresAt)

Nếu owner rejoin trong 60s:
- owner_left_at = null, reservedOwnerSlot = false
- max effective = maxParticipants
- WS OWNER_REJOINED → UI đóng banner
- Music KHÔNG auto resume (R-MUSIC-06)

Nếu hết 60s mà owner không rejoin:
- v1.7 (R-END-08): phòng ENDED ngay lập tức KỂ CẢ KHI PHÒNG CÒN PARTICIPANTS KHÁC
- endedReason = "owner_grace_expired"
- WS ROOM_AUTO_ENDED { reason: "owner_grace_expired" }
- Participants: modal "Owner không quay lại — phòng đã kết thúc"
```

### 10.4. Downgrade giữa phiên (R-ROLE-06 v1.7 update)

```
PRO A bị downgrade → USER trong khi phòng X đang ACTIVE:
- v1.7 (R-ROLE-06): Chỉ force-end phòng do A TẠO (owner)
- Phòng A là participant (của owner khác) → KHÔNG bị ảnh hưởng
- A vẫn là participant bình thường ở phòng khác
- Nếu force-end: WS ROOM_AUTO_ENDED { reason: "force_role_change" }
- v1.1 (Bug 5): Nếu A upgrade lại PRO sau đó → CÓ THỂ reopen phòng X
  - rejectCountByOwner đã reset (Bug 1 reopen rule)
  - was_approved của participants cũ vẫn giữ
```

### 10.5. WS Reconnect grace (v1.7)

```
WS disconnect trong meeting:
- Backend giữ participant.state = ACTIVE trong 60s grace
- UI hiển thị RECONNECTING overlay "Đang kết nối lại... Còn X giây"
- Sau grace: participant.state = OFFLINE, slot được giải phóng
- WS PARTICIPANT_CONNECTION_CHANGED broadcast
- Reconnect fail cho owner → set owner_left_at → grace 60s (R-LEAVE-04)
- Reconnect fail cho participant thường → slot trống → giải phóng chỗ
```

---

## 11. Test Scenarios (cho QA - v1.1)

### 11.1. Test Permission Matrix

```gherkin
Scenario: PRO tạo phòng thành công
  Given user U có role PRO
  When U POST /api/v1/liverooms
  Then status 201 Created

Scenario: USER tạo phòng bị từ chối
  Given user U có role USER
  When U POST /api/v1/liverooms
  Then status 403 Forbidden
  And error code = LIVEROOM_PRO_REQUIRED

Scenario: PRO join phòng của owner khác
  Given user A là PRO, owner phòng X
  And user B là PRO, owner phòng Y
  When A POST /api/v1/liverooms/Y/join-requests
  Then status 201 Created
  When B approve A
  Then A auto-redirect SC-06 (v1.1: auto-create participant)
  And A KHÔNG có quyền END phòng Y

Scenario: PRO tự join phòng của chính mình
  Given user A là PRO, owner phòng X
  When A POST /api/v1/liverooms/X/join-requests
  Then status 400 Bad Request
  And error code = LIVEROOM_CANNOT_JOIN_OWN_ROOM
```

### 11.2. Test State Transitions (v1.1)

```gherkin
Scenario: REJECTED 3 lần OWNER trong phiên → LOCKED → Modal Bị ban
  Given user A trong phòng P, rejectCountByOwner = 2
  When owner reject A lần 3 (reason = OWNER_REJECT)
  Then A rejectCountByOwner = 3, state = LOCKED
  When A click "Xin vào" lần nữa
  Then status 403 LIVEROOM_REQUEST_LOCKED
  And UI: Modal "Bạn đã bị ban khỏi phòng này"

Scenario: REJECTED do phòng đầy → KHÔNG LOCKED (v1.7 NEW - R-REJECT-04)
  Given phòng P đã đầy 7/7
  And user A có request state = PENDING
  When owner approve A (capacity check fail)
  Then A state = REJECTED, rejectionReason = "ROOM_FULL"
  And rejectCountByCapacity += 1 (rejectCountByOwner KHÔNG tăng)
  And A KHÔNG bị LOCKED
  When A click "Xin vào" sau khi có slot trống
  Then status 201 Created (vì rejectCountByOwner vẫn < 3)

Scenario: APPROVED + còn slot → auto-create participant (v1.1)
  Given phòng P ACTIVE, còn 3 slot trống
  And user A có request state = PENDING
  When owner approve A
  Then A state = APPROVED
  And A tự động có participant record + session (auto-create)
  And WS push REQUEST_APPROVED → A auto-redirect SC-06

Scenario: APPROVED mà phòng đầy → REJECT luôn (v1.6 NEW)
  Given phòng P đã đầy 7/7
  And user A có request state = PENDING
  When owner approve A
  Then A state = REJECTED, reason = "Room is full, please try later"
  And UI A hiển thị toast "Phòng đã đầy, vui lòng thử lại sau"
  And A có thể click "Gửi lại yêu cầu" sau khi có slot trống
  And KHÔNG có auto-promote như v1.5 (bỏ APPROVED_WAITING)

Scenario: Reopen reset rejectCount (v1.1 Bug 1)
  Given user A đã bị LOCKED trong phòng P (rejectCount = 3)
  And phòng P đang ENDED
  When owner reopen phòng P
  Then rejectCount của A reset = 0
  When A POST /join-requests
  Then status 201 Created

Scenario: Owner leave grace hết + có participants → ENDED (v1.7 NEW - R-END-08)
  Given phòng P ACTIVE, owner + 3 participants
  When owner leave lúc 10:00
  Then grace 60s bắt đầu, owner_left_at = now, reservedOwnerSlot = true
  And max effective capacity = max - 1
  And empty timeout 5min bị SUSPEND
  And WS OWNER_LEFT broadcast { graceExpiresAt = 10:01 }
  When 10:01 (grace hết) mà owner chưa rejoin
  Then phòng P auto-END với endedReason = "owner_grace_expired"
  And KỂ CẢ KHI PHÒNG CÒN 3 PARTICIPANTS
  And WS ROOM_AUTO_ENDED { reason: "owner_grace_expired" }
  And participants: modal "Owner không quay lại — phòng đã kết thúc"
```

### 11.3. Test Keyboard Shortcuts (v1.1)

```gherkin
Scenario: Shortcut M toggle mic trong ACTIVE state
  Given user U đang ở SC-06 ACTIVE
  When U nhấn phím 'M'
  Then PATCH /sessions/me/media được gọi
  And UI mic toggle thay đổi

Scenario: Shortcut Ctrl+E mở Leave modal
  Given user U đang ở SC-06 ACTIVE
  When U nhấn 'Ctrl+E' (hoặc Cmd+E trên Mac)
  Then UI mở LeaveSession confirm modal

Scenario: Shortcut KHÔNG hoạt động trong NOT_JOINED state
  Given user U đang ở SC-06 NOT_JOINED
  When U nhấn 'M' hoặc 'V'
  Then KHÔNG có gì xảy ra (shortcut bị disable)
```

---

## 12. Mapping với Frontend Component (v1.1)

| Permission Check | Frontend Component | Hook / Util |
|---|---|---|
| `canActorDoAction(actor, 'CREATE_ROOM')` | `<CreateRoomButton>` | `usePermission()` |
| `canActorDoAction(actor, 'END_ROOM', room)` | `<EndRoomButton>` | `usePermission()` |
| `canActorDoAction(actor, 'APPROVE_JOIN_REQUEST', room)` | `<ApproveRequestButton>` | `usePermission()` |
| `canActorDoAction(actor, 'KICK_PARTICIPANT', room)` | `<KickMenuItem>` | `usePermission()` |
| `canActorDoAction(actor, 'TOGGLE_MEDIA', room)` | `<MicToggle>` + `<CameraToggle>` | `usePermission()` |
| `shouldShowBanModal(errorCode)` | `<BanModal>` | `useApiErrorHandler()` |
| `isKeyboardShortcutAvailable(sessionState)` | `<MediaControls>` | `useKeyboardShortcut()` |

```typescript
// usePermission.ts
import { useContext } from 'react';
import { AuthContext } from '@/contexts/AuthContext';

export function usePermission() {
  const { actor } = useContext(AuthContext);

  return {
    can: (action: Action, resource?: Resource): boolean => {
      return PERMISSION_MATRIX[action][actor.type] === true;
    },
    actor,
  };
}

// useKeyboardShortcut.ts (v1.1)
export function useKeyboardShortcut(
  key: string,
  callback: () => void,
  options: { enabled: boolean; modifier?: 'ctrl' | 'cmd' }
) {
  useEffect(() => {
    if (!options.enabled) return;

    function handler(e: KeyboardEvent) {
      const matchesKey = e.key.toLowerCase() === key.toLowerCase();
      const matchesModifier =
        !options.modifier ||
        (options.modifier === 'ctrl' && e.ctrlKey) ||
        (options.modifier === 'cmd' && e.metaKey);
      const noOtherModifiers = !e.altKey && !e.shiftKey;

      if (matchesKey && matchesModifier && noOtherModifiers) {
        e.preventDefault();
        callback();
      }
    }

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [key, callback, options.enabled, options.modifier]);
}
```

---

**Phiên bản tiếp theo**:
- v1.5: Bổ sung Resolved/Rejected cho LiveRoomRoomModerationAction, thêm i18n cho OWNER_LEFT banners
- Sau khi Backend implement authorization interceptor → cross-check
- Sau khi Frontend implement usePermission → cross-check
- Sau khi có bug report từ QA → bổ sung edge case
