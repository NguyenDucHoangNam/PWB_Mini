# Live Room - Tài liệu Nghiệp vụ

**Phiên bản**: 1.8
**Ngày cập nhật**: 2026-07-26
**Phạm vi**: Module Live Room (Backend + Realtime Gateway + Media Server)
**Đối tượng đọc**: Product Owner, BA, QA, Dev, Designer

---

## 1. Bối cảnh sản phẩm

Live Room là tính năng **phòng họp trực tuyến** (voice + video meeting) trong hệ thống PWB_MiNi. Người dùng có thể tạo phòng họp nhanh (instant meeting), mời người khác tham gia, và cùng trao đổi bằng **giọng nói + hình ảnh** theo thời gian thực (thấy mặt nhau).

### 1.1. Đặc điểm cốt lõi

- **Instant meeting**: Không cần lên lịch trước, tạo phòng là vào họp luôn
- **Voice + Video**: Người tham gia có thể **vừa nói vừa thấy mặt nhau** (giống Google Meet / Zoom)
- **Persistent**: Phòng có thể tái sử dụng nhiều lần, lưu lịch sử các buổi họp
- **Small group**: Phòng có giới hạn số người, tối ưu cho nhóm nhỏ
- **Owner-controlled**: Chủ phòng kiểm soát việc ai được vào
- **Media flexibility**: Mỗi người tự quyết định bật/tắt camera và mic của mình

### 1.2. Use case chính

Em tóm tắt các kịch bản sử dụng điển hình:

```
Use Case 1: Họp nhóm nội bộ (voice + video)
  - Owner tạo phòng → 3-5 thành viên tham gia
  - Mọi người bật camera + mic, họp trực tiếp thấy mặt nhau
  - Họp xong → owner kết thúc

Use Case 2: Họp nhiều team (persistent room)
  - Owner tạo phòng "Daily Sync Team A"
  - Họp team A xong → kết thúc
  - Ngày mai cần họp lại → mở lại phòng cũ → mời team
  - Lịch sử cuộc họp được giữ lại

Use Case 3: Guest tham gia có kiểm duyệt
  - Owner tạo phòng riêng tư
  - Guest gửi yêu cầu tham gia → owner phê duyệt → được vào

Use Case 4: Slot FIFO khi phòng đầy
  - Phòng đầy 7 người
  - Người thứ 8 muốn vào → phải đợi
  - Khi có người rời → người đợi vào (không qua duyệt nếu đã từng được duyệt)

Use Case 5: Tắt camera khi bandwidth yếu
  - Người dùng mạng chậm → tắt camera, chỉ dùng mic
  - Vẫn tham gia được bình thường

Use Case 6: Nghe thầm / Xem thầm
  - Người dùng join với camera off + mic off (chỉ xem, không nói)
  - Dùng cho observer, người học, hoặc xem demo
```

---

## 2. Đối tượng sử dụng (Personas)

### 2.1. Owner (Chủ phòng)

- Người tạo phòng, có quyền cao nhất trong phòng
- Phải có role **PRO** trong hệ thống
- Quyết định ai được vào (phê duyệt hoặc từ chối yêu cầu)
- Quyết định kết thúc phòng
- Quyết định mở lại phòng sau khi kết thúc
- Có thể **kick** người tham gia ra khỏi phòng
- **Không có chức năng "mời" trong hệ thống** — owner chỉ cần chia sẻ `roomCode` hoặc `roomLink` qua kênh bên ngoài (Zalo, SMS, email, chat...), người nhận tự vào qua link/mã

### 2.2. Participant (Người tham gia)

- Người đã được duyệt và đang trong phòng
- Có thể rời phòng bất cứ lúc nào
- Khi rời, có thể tham gia lại (nếu còn slot) mà không cần duyệt lại

### 2.3. Requester (Người chờ duyệt)

- Người đã gửi yêu cầu tham gia nhưng chưa được owner phản hồi
- Có thể hủy yêu cầu của mình
- Trạng thái chờ có thời hạn (tự hết hạn khi phòng kết thúc)

### 2.4. Guest (Người ngoài)

- Người chưa gửi yêu cầu hoặc đã bị từ chối
- Có thể gửi JoinRequest để xin vào phòng
- **Nếu đã bị REJECTED**: được phép gửi yêu cầu lại, nhưng **tối đa 3 lần** (tính trong cùng 1 phiên)
  - Sau 3 lần REJECTED → bị **LOCKED** cho phiên này của phòng, không thể gửi yêu cầu nữa
  - Khi phòng REOPEN → rejectCount reset về 0, user có thể thử lại
- Thấy danh sách phòng nhưng không có quyền truy cập nội dung

---

## 3. Thuật ngữ nghiệp vụ (Glossary)

| Thuật ngữ           | Định nghĩa                                                            |
| ------------------- | --------------------------------------------------------------------- |
| **Live Room**       | Phòng họp thoại trực tuyến, có mã phòng riêng                         |
| **Room Code**       | Mã định danh phòng, dùng để mời người khác tham gia                   |
| **Owner**           | Chủ phòng, người tạo phòng                                            |
| **Participant**     | Người đang trong phòng (bao gồm owner)                                |
| **Capacity**        | Sức chứa tối đa của phòng                                             |
| **Slot**            | Một vị trí trong phòng, tương ứng với 1 người                         |
| **Join Request**    | Yêu cầu tham gia phòng, cần owner phê duyệt                           |
| **Approve**         | Phê duyệt cho người vào phòng                                         |
| **Reject**          | Từ chối yêu cầu tham gia                                              |
| **Rejoin**          | Tham gia lại phòng sau khi đã từng vào (không cần duyệt lại)          |
| **FIFO**            | First In First Out - vào theo thứ tự, ai vào trước thì giữ slot trước |
| **Grace Period**    | Khoảng thời gian "ân hạn" trước khi hệ thống tự động xử lý            |
| **Auto-End**        | Hệ thống tự động kết thúc phòng khi điều kiện đủ                      |
| **Reopen**          | Mở lại phòng sau khi đã kết thúc, dùng cho persistent room            |
| **Persistent Room** | Phòng có thể tái sử dụng nhiều lần qua nhiều phiên họp                |
| **Session**         | Một lần họp cụ thể trong persistent room                              |

---

## 4. Quy tắc nghiệp vụ (Business Rules)

### 4.1. Tạo phòng (Create Room)

**Mô tả**: Owner tạo phòng mới và bắt đầu phiên họp ngay lập tức.

**Quy tắc**:

- **R-CREATE-01**: Khi tạo phòng, owner được tự động thêm vào phòng với vai trò owner (không cần qua bước "tham gia")
- **R-CREATE-02**: Phòng mới có trạng thái `ACTIVE` ngay từ đầu
- **R-CREATE-03**: Sức chứa mặc định của phòng là **7 người** (tối đa)
- **R-CREATE-04**: Sức chứa tối thiểu là **1 người** (chỉ owner — phòng mới tạo có thể chỉ có 1 mình owner)
- **R-CREATE-05**: Mỗi phòng có 1 mã `roomCode` duy nhất, dùng để chia sẻ cho người khác vào phòng. **Quy tắc mã phòng (v1.8)**:
  - **Độ dài**: 6 ký tự
  - **Bộ ký tự**: `A-Z` (uppercase) + `0-9` → 36 ký tự
  - **Không trùng**: kiểm tra unique trong DB trước khi lưu, retry tối đa 5 lần nếu trùng
  - **Không phân biệt chữ hoa/thường khi user nhập**: client tự uppercase trước khi gọi API
  - **Bảo mật (brute-force protection)** ở **Phase 2 — Join by RoomCode**:
    - Rate limit: **5 attempts / IP / 60 phút** (áp dụng cho cả JoinRequest POST)
    - Nếu vượt → HTTP 429 + cooldown 1 giờ
    - Logging: log IP + attempted code hash (không log raw code)
  - **Không gánh lỗ hổng Critical**: 36^6 = 2.176.782.336 combinations, với rate limit 5/h thì brute-force không khả thi
  - **Tránh ký tự gây nhầm lẫn**: bỏ `0/O`, `1/I/L` → chỉ dùng 32 ký tự safe (A-Z bỏ I, O, L + 0-9 bỏ 0, 1) nếu muốn tăng UX, nhưng đề xuất giữ 36 để giữ tổ hợp cao
  - **Định dạng hiển thị**: `ABC123` (group 3-3, ví dụ `ABC-123` chỉ khi hiển thị UI, lưu trữ là `ABC123`)
- **R-CREATE-06**: Tên phòng **không được trùng trong cùng owner** (per-owner unique). Hai owner khác nhau CÓ THỂ cùng đặt tên "Daily Sync". **Quy tắc so sánh unique (v1.8)**:
  - **Trim leading/trailing whitespace** trước khi so sánh
  - **Lowercase comparison** (case-insensitive): `"Daily Sync"` = `"daily sync"` = `"DAILY SYNC "` (sau trim)
  - **Độ dài tối đa**: 100 chars (sau trim)
  - **Độ dài tối thiểu**: 1 char (sau trim)
  - **Ký tự cho phép**: chữ cái, số, khoảng trắng, các dấu câu thường gặp (`. , - _ ' " ! ?`)
  - **Không cho phép**: chỉ toàn khoảng trắng, ký tự control, emoji
  - **Lưu trữ**: lưu raw value (giữ nguyên case user nhập) ở `roomName`, **unique constraint trên `(ownerId, LOWER(TRIM(roomName)))`**
  - **Ví dụ hợp lệ**: `"Daily Sync"`, `"  meeting  "` (=`"meeting"`), `"Dự án Q3"`
  - **Ví dụ không hợp lệ**: `""` (rỗng), `"   "` (toàn space), `null`
- **R-CREATE-07**: Một PRO có thể tạo nhiều phòng (persistent rooms hoặc phòng mới)
- **R-CREATE-08**: Chỉ user có role **PRO** mới có quyền tạo phòng (xem chi tiết tại R-ROLE-01)

**R-CAPACITY-01 (Real-time capacity cho lobby)**: Phòng cập nhật `current_count` real-time và broadcast qua WS `ROOM_CAPACITY_CHANGED`

- Trigger: Có participant join/leave/timeout
- WS payload: `{ roomId, currentCount, maxParticipants, timestamp }`
- User ở lobby (PENDING) subscribe để hiển thị count
- Frontend render: "Hiện tại: 5/7 người" + "Còn 2 slot trống"
- **Lưu ý v1.6**: Khi `currentCount >= maxParticipants` → owner KHÔNG thể approve user mới (R-APPROVE-05 sẽ reject)

**R-CAPACITY-02 (Race condition protection - v1.7)**: Approve/rejoin sử dụng **lock** để tránh race condition khi nhiều request đồng thời:

- **Pessimistic lock (CHÍNH - chọn v1.8)**: `SELECT ... FOR UPDATE` trên `LiveRoom` row khi thực hiện approve/rejoin
  - Lock acquire → check capacity → update participant → release lock
  - Đảm bảo chỉ 1 request thành công tại 1 thời điểm
  - **Quyết định v1.8**: KHÔNG dùng `@Version` cho `LiveRoom` — chỉ dùng `@Version` cho `PlaybackState` (R-MUSIC-10 v1.8). Lý do: Pessimistic đơn giản hơn, đảm bảo serialize, không cần retry logic (xem `liveroom-concurrency.md` §1.1)
- **Race scenario EC-24**: 2 user approve/rejoin đồng thời khi phòng gần đầy (VD: còn 1 slot)
  - Cả 2 request cùng đọc `current_count = 6`, `max = 7` → đều pass
  - Không có lock → cả 2 đều update → `current_count = 8` (vượt max)
  - Có lock → request A acquire → update thành 7 → release → request B acquire → check 7 >= 7 → REJECT_WHEN_FULL
- **Áp dụng cho**: Approve JoinRequest, Rejoin, Owner rejoin (R-LEAVE-05)
- **Không áp dụng cho**: Read-only API (xem capacity), WS broadcast

**Ví dụ nghiệp vụ**:

```
An tạo phòng "Daily Sync" → Phòng ACTIVE, An là owner, capacity = 7
Hiện tại có 1 người (An) trong phòng
```

---

### 4.2. Tham gia phòng (Join Room)

**Mô tả**: Người dùng muốn vào phòng đang hoạt động.

**Quy tắc chung**:

- **R-JOIN-01**: Chỉ user có role **PRO** mới có quyền tạo phòng (và trở thành owner). User thường (non-PRO) chỉ có thể tham gia với vai trò Participant, không có quyền tạo phòng
- **R-JOIN-02**: Người tham gia phải có tài khoản hợp lệ trong hệ thống
- **R-JOIN-03**: Một user chỉ có thể có tối đa 1 phiên tham gia cho mỗi phòng (tại một thời điểm)

**Cơ chế Join - Decision Tree**:

```
User muốn vào phòng
   │
   ├─ Đã là participant (chưa rời / đã rời)?
   │      │
   │      ├─ Đang trong phòng → 409 Conflict (đã ở trong rồi)
   │      │
   │      └─ Đã rời → R-JOIN-04 (Rejoin logic)
   │
   ├─ Đã từng được duyệt (was_approved = TRUE)?
   │      │
   │      ├─ Có → R-JOIN-04 (Rejoin - không cần duyệt)
   │      │
   │      └─ Không → Check giới hạn REJECTED
   │
   └─ Chưa từng tham gia / chưa từng được duyệt
          │
          ├─ Đã bị REJECTED 3 lần cho phòng này? (R-REJECT-04)
          │      │
          │      ├─ Có → 403 FORBIDDEN + message LIVEROOM_REQUEST_LOCKED
          │      │         (Không tạo JoinRequest, user bị chặn vĩnh viễn)
          │      │
          │      └─ Không → Tạo JoinRequest mới → chờ owner duyệt
          │
          └─ < 3 lần REJECTED → Tạo JoinRequest mới
```

**R-JOIN-04 - Rejoin Logic** (áp dụng cho cả participant cũ và người đã từng được duyệt):

```
User thực hiện Rejoin
   │
   ├─ Phòng còn slot (current_count < max)?
   │      │
   │      ├─ Có → Vào phòng ngay (FIFO)
   │      │
   │      └─ Không → Từ chối, đợi khi có slot
   │              │
   │              └─ User tự retry khi có slot
   │                 (KHÔNG qua queue, KHÔNG qua approval)
   │
   └─ Trạng thái phòng?
          │
          ├─ ACTIVE → Tiếp tục check slot
          │
          ├─ ENDED → Không cho rejoin (403)
          │
          └─ Không tồn tại → 404
```

**R-JOIN-05 - FIFO (First In First Out)**:

- Khi phòng đầy, không có queue/position lưu trữ
- Cả rejoin và new joiner đều phải đợi slot trống
- Khi có slot trống → user thực hiện thao tác join/rejoin sẽ race để vào
- Hệ thống không can thiệp vào thứ tự ưu tiên, chỉ check `current_count < max`

**R-JOIN-06 - Phòng đầy**:

- Khi phòng đầy, response cho biết "phòng đầy, vui lòng đợi"
- User có thể thử lại thủ công (không có thông báo tự động)
- Nếu user đã từng được duyệt → vẫn phải đợi slot (không được ưu tiên)

**R-JOIN-07 (Idempotency cho duplicate JoinRequest - v1.8 NEW)**:

- Khi user click "Gửi yêu cầu tham gia" 2 lần liên tiếp (double-click, lag, network retry) → **KHÔNG tạo 2 JoinRequest**
- **Idempotency key**: Mỗi request kèm theo `idempotencyKey` (UUID v4 generated frontend, lưu localStorage 24h)
- Nếu backend nhận request với key đã tồn tại (chưa expire) → trả về JoinRequest đã tạo trước đó (state hiện tại)
- Nếu key mới → tạo JoinRequest mới
- **TTL idempotency key**: 24 giờ (sau đó có thể gửi lại)
- **Conflict nếu state khác**: User gửi request, hết hạn (24h), user gửi lại cùng key → không trùng vì key hết hạn. Logic idempotent KHÔNG apply giữa 2 phiên (tránh issue khi user thực sự muốn gửi lại)
- **Lý do**: Tránh UI double-click tạo 2 request, owner nhận 2 notify, user bị reject 2 lần → oan

**R-JOIN-08 (Camera/Mic permission gate - v1.8 NEW)**:

- **Trước khi JoinRequest POST**: Frontend phải check `navigator.mediaDevices` available
- Nếu browser không support hoặc user không cấp permission → vẫn cho phép JoinRequest (vì SC-04 pre-join là optional screen)
- Nhưng khi user vào phòng (state = ACTIVE) → nếu không có mic/camera thì chỉ "nghe thầm" (R-MEDIA-04)
- **Lý do**: Không gate JoinRequest bởi permission, tránh mất cơ hội vào phòng

**R-JOIN-09 (RejectionReason enum - v1.8 NEW)**: Mỗi JoinRequest state kết thúc có `rejectionReason` để hiển thị UI chính xác:

- `OWNER_REJECT` — REJECTED_BY_OWNER (owner chủ động reject)
- `CAPACITY_FULL` — REJECTED_BY_CAPACITY (phòng đầy)
- `ROOM_ENDED` — EXPIRED (phòng ENDED trước khi duyệt)
- `USER_CANCELLED` — CANCELLED (user tự hủy)
- `RATE_LIMIT` — LOCKED (user đã REJECTED_BY_OWNER >= 3 lần)
- UI hiển thị message i18n theo reason (EN/VI)

**R-JOIN-10 (Multi-tab cùng user cùng phòng - v1.8 NEW)**:

- User A đã có participant state = ACTIVE trong phòng P → mở tab 2 cùng account, cùng URL phòng P
- **Detect**: Frontend check `localStorage` key `activeRoomSession:{roomId}` = `{sessionId, tabId}` khi load. Nếu trùng roomId + sessionId khác tabId → block UI
  - **Lưu ý**: Key là **per-room** (`activeRoomSession:{roomId}`) để hỗ trợ multi-room (R-ROLE-10: user có thể ở phòng A tab 1 + phòng B tab 2)
- **Behavior**:
  - Hiển thị warning page "Bạn đang ở phòng này ở tab khác"
  - 2 button: "Chuyển sang tab kia" (focus existing tab qua BroadcastChannel) + "Đóng tab này"
  - **KHÔNG** cho phép kick tab 1 từ tab 2 (tránh lừa đảo — user A mở tab 2, chiếm quyền owner từ tab 1)
- **Cơ chế BroadcastChannel**:
  - Tab mở cùng origin → BroadcastChannel API trao đổi `ping/pong` định kỳ (5s)
  - Tab 1 ping → không có pong → 5s timeout → mới cho phép tab 2 chiếm quyền
  - Nếu có pong → vẫn block
- **Owner**: Nếu user A là owner ở tab 1, mở tab 2 → tab 2 thấy warning. Không tự promote tab 2 thành owner
- **Lý do**: Tránh nhiều phiên media cùng user cùng phòng (gây echo, bandwidth waste, data race)

**R-JOIN-11 (REJECTED state breakdown - v1.8 NEW)**: State diagram chi tiết:

```
PENDING ─owner approve→ APPROVED
   │
   ├─owner reject→ REJECTED_BY_OWNER (reason=OWNER_REJECT)
   │
   ├─capacity full khi approve→ REJECTED_BY_CAPACITY (reason=CAPACITY_FULL)
   │
   ├─user cancel→ CANCELLED (reason=USER_CANCELLED)
   │
   ├─room ENDED→ EXPIRED (reason=ROOM_ENDED)
   │
   └─rejectCountByOwner >= 3→ LOCKED (reason=RATE_LIMIT)
```

**Ví dụ nghiệp vụ**:

```
Tình huống 1: User mới hoàn toàn
  - Bình gửi yêu cầu vào phòng "Daily Sync" → JoinRequest PENDING
  - An (owner) duyệt → Bình được thêm vào phòng

Tình huống 2: Rejoin khi có slot
  - Bình rời phòng (còn 6 người)
  - Bình thực hiện rejoin → vào phòng ngay (vì was_approved = TRUE)

Tình huống 3: Rejoin khi phòng đầy
  - Bình rời phòng (còn 6 người)
  - 6 người mới vào, phòng đầy lại (7 người)
  - Bình thực hiện rejoin → bị từ chối (phòng đầy)
  - Khi có người rời → Bình thử lại → vào được

Tình huống 4: Phòng đã kết thúc
  - Bình rời phòng
  - An kết thúc phòng
  - Bình thực hiện rejoin → 403 Forbidden (phòng đã kết thúc)
```

---

### 4.3. Phê duyệt yêu cầu tham gia (Approve Join Request)

**Quy tắc**:

- **R-APPROVE-01**: Chỉ owner mới có quyền phê duyệt
- **R-APPROVE-02**: Khi phê duyệt, hệ thống check còn slot **real-time** (đếm `currentParticipantCount` lúc approve)
  - Còn slot → tự động tạo participant record + session → user vào phòng **NGAY LẬP TỨC** (auto-join), đánh dấu `was_approved = TRUE`
  - **Hết slot → REJECT luôn** với reason `"Room is full, please try later"` (state = `REJECTED`). **KHÔNG có `APPROVED_WAITING`** (bỏ từ v1.6)
- **R-APPROVE-02.1 (Auto-join behavior)**: User được approve và còn slot → **KHÔNG cần click "Vào phòng"** riêng
  - Backend tạo participant + session atomically ngay khi approve
  - WS push `REQUEST_APPROVED` chứa `roomId` → user ở lobby tự động redirect sang SC-06
  - Nếu user offline khi được approve → state = APPROVED, lần sau vào `/rooms/join` với code đó sẽ auto-join
  - Lợi ích: UX giống Google Meet — "Ask to join" → owner approve → vào phòng ngay
- **R-APPROVE-03**: Sau khi được duyệt, user có `was_approved = TRUE` vĩnh viễn cho phiên đó
- **R-APPROVE-04**: Nếu user rời phòng sau khi được duyệt, vẫn giữ `was_approved = TRUE` (để rejoin)

**R-APPROVE-05 (REJECT khi phòng đầy - v1.7)**: Approve mà `currentParticipantCount >= maxParticipants` → REJECT luôn (KHÔNG đợi slot)

- Lý do: tránh queue phức tạp, tránh race condition khi nhiều slot trống cùng lúc
- User bị reject → toast `"Phòng đã đầy, vui lòng thử lại sau"`
- User muốn vào → phải gửi JoinRequest mới sau khi có slot trống
- **KHÔNG** auto-promote queue (bỏ so với v1.5)
- **v1.7 MỚI**: REJECTED do phòng đầy **KHÔNG tính vào `rejectCountByOwner`** (xem R-REJECT-04 v1.7) → user không bị LOCKED oan
  - Đánh dấu JoinRequest với state = `REJECTED_BY_CAPACITY` (mapping rejectionReason = `CAPACITY_FULL`, xem R-REJECT-06)
  - User vẫn có thể gửi JoinRequest mới (không bị giới hạn bởi counter)

**Các trạng thái JoinRequest (v1.8 - SPLIT REJECTED)**:

```
PENDING              → Chờ owner phản hồi
APPROVED             → Đã duyệt, đã vào phòng
REJECTED_BY_OWNER    → Bị owner chủ động từ chối (rejectionReason = OWNER_REJECT)
REJECTED_BY_CAPACITY → Bị reject vì phòng đầy (R-APPROVE-05, rejectionReason = CAPACITY_FULL)
CANCELLED            → User tự hủy yêu cầu (rejectionReason = USER_CANCELLED)
EXPIRED              → Hết hạn (phòng kết thúc trước khi duyệt, rejectionReason = ROOM_ENDED)
LOCKED               → Tự động chặn (user đã REJECTED_BY_OWNER 3 lần cho phòng này, rejectionReason = RATE_LIMIT)
```

**Lưu ý quan trọng**:

- **REJECTED_BY_OWNER, REJECTED_BY_CAPACITY, CANCELLED, EXPIRED, LOCKED** đều là trạng thái kết thúc (terminal state)
- **LOCKED chỉ áp dụng** cho JoinRequest mới của user đã đạt giới hạn 3 lần `REJECTED_BY_OWNER`
- LOCKED không hiển thị cho owner (vì user không thể gửi yêu cầu nữa — hệ thống chặn từ đầu)
- **v1.8 breaking change**: Trước đây `REJECTED` là 1 state chung với field `rejectionReason`. v1.8 tách thành 2 state riêng biệt `REJECTED_BY_OWNER` + `REJECTED_BY_CAPACITY` để khớp với state diagram ở `liveroom-user-flow.md` và đơn giản hoá enum (không cần field `rejectionReason` cho các trạng thái REJECTED)

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Phê duyệt thành công
  - Bình gửi yêu cầu → PENDING
  - An duyệt → APPROVED, Bình vào phòng, was_approved = TRUE

Tình huống 2: Phê duyệt nhưng phòng đầy (v1.6 NEW)
  - Bình gửi yêu cầu → PENDING
  - Phòng đầy 7/7
  - An duyệt → REJECTED (v1.6 - bỏ APPROVED_WAITING)
  - Bình thấy toast "Phòng đã đầy, vui lòng thử lại sau"
  - Bình chờ 1 slot trống → gửi yêu cầu mới (KHÔNG auto-promote)

Tình huống 3: Sau khi được duyệt, rejoin không cần duyệt lại
  - Bình được duyệt, vào phòng
  - Bình rời phòng
  - Bình thực hiện rejoin → vào ngay (was_approved = TRUE)
```

---

### 4.4. Từ chối yêu cầu tham gia (Reject Join Request)

**Quy tắc**:

- **R-REJECT-01**: Chỉ owner mới có quyền từ chối
- **R-REJECT-02**: Sau khi bị từ chối, user có thể gửi yêu cầu mới (cho cùng phòng)
- **R-REJECT-03**: Việc từ chối không ảnh hưởng đến quyền rejoin nếu user đã từng được duyệt trước đó
- **R-REJECT-04 (Giới hạn 3 lần - v1.8 clarification)**: Hệ thống track **2 loại reject counter** riêng biệt cho mỗi (user, room) trong cùng 1 phiên:
  - **`rejectCountByOwner`**: TĂNG **CHỈ KHI** JoinRequest chuyển sang state `REJECTED_BY_OWNER` (owner chủ động bấm Reject + rejectReason = `OWNER_REJECT`)
  - **`rejectCountByCapacity`**: TĂNG **CHỈ KHI** JoinRequest chuyển sang state `REJECTED_BY_CAPACITY` (phòng đầy theo R-APPROVE-05)
  - **Các state khác KHÔNG tăng counter**:
    - `CANCELLED` (user tự hủy) → counter giữ nguyên
    - `EXPIRED` (phòng ENDED) → counter giữ nguyên
    - `LOCKED` (hệ thống chặn do reach limit) → counter giữ nguyên (chỉ là biểu hiện state, không phải reject mới)
  - Sau khi `rejectCountByOwner >= 3` → tất cả JoinRequest tiếp theo của user cho phòng đó sẽ tự động bị **LOCKED**
  - `rejectCountByCapacity` có thể tăng vô hạn (không giới hạn) — không ảnh hưởng đến quyền gửi JoinRequest
  - **Implement**: Backend dùng trigger trong transaction: `IF newState = REJECTED_BY_OWNER THEN counter++`. KHÔNG dùng event hook để tránh race
- **R-REJECT-04.1 (Reopen reset rejectCount - v1.7)**: Khi owner reopen phòng, **CẢ HAI** counter (`rejectCountByOwner` và `rejectCountByCapacity`) của TẤT CẢ user cho phòng đó được reset về 0
  - Lý do: Reopen = meeting mới hoàn toàn (clean slate), mọi user đều có cơ hội mới
  - User từng bị LOCKED có thể gửi request mới sau reopen
  - Implement: Xoá table tracking cả 2 counter theo (userId, roomId) khi reopen; JoinRequest cũ cũng bị xoá theo R-REOPEN-07
- **R-REJECT-05 (State LOCKED - v1.7)**: Khi `rejectCountByOwner >= 3`, mọi JoinRequest mới sẽ tự động chuyển sang status **LOCKED** (thay vì PENDING), owner sẽ không nhận được yêu cầu
  - LOCKED là trạng thái cuối, không thể chuyển sang trạng thái khác
  - **Không có whitelist** để owner mở khoá — đây là quyết định cuối cùng của MVP. Nếu sau này cần, whitelist sẽ là module riêng (không thuộc Live Room hiện tại)
  - **Lưu ý**: Chỉ `rejectCountByOwner` mới trigger LOCKED, `rejectCountByCapacity` KHÔNG bao giờ trigger LOCKED
- **R-REJECT-06 (Mapping state ↔ counter - v1.8)**: Bảng mapping chính thức:

| State JoinRequest      | `rejectionReason` | `rejectCountByOwner` | `rejectCountByCapacity` |
| ---------------------- | ----------------- | -------------------- | ----------------------- |
| `REJECTED_BY_OWNER`    | `OWNER_REJECT`    | +1                   | 0                       |
| `REJECTED_BY_CAPACITY` | `CAPACITY_FULL`   | 0                    | +1                      |
| `CANCELLED`            | `USER_CANCELLED`  | 0                    | 0                       |
| `EXPIRED`              | `ROOM_ENDED`      | 0                    | 0                       |
| `LOCKED`               | `RATE_LIMIT`      | 0                    | 0                       |

**v1.7 RATIONALE (tại sao tách 2 loại counter)**:

- Tình huống cũ (v1.6): User gửi request → owner vô tình bấm Approve khi phòng đầy → user bị REJECTED → lặp 3 lần → user bị LOCKED oan → bất công
- Tình huống mới (v1.7): User gửi request → owner vô tình bấm Approve khi phòng đầy → user bị REJECTED với reason `ROOM_FULL` → chỉ toast thông báo → không ảnh hưởng counter → user có thể gửi lại bình thường
- Trade-off: Counter phức tạp hơn 1 chút, nhưng công bằng cho user hơn

---

### 4.4.5. Quản trị phòng (Admin Actions — Kick + Remote Mute)

**Mô tả**: Owner có quyền quản trị participants trong phòng: kick (đuổi) participant ra khỏi phòng, mute mic từ xa. Mục đích: duy trì trật tự, chống quấy rối, quản lý chất lượng cuộc họp.

**Quy tắc chung**:

- **R-ADMIN-01**: Chỉ **owner** mới có quyền kick/remote-mute
- **R-ADMIN-02**: Owner **không thể tự kick** chính mình (vì không có ai khác claim owner trong phòng đó — nếu owner muốn rời → dùng R-LEAVE)
  - Cố tình gọi API → HTTP 400 `SELF_KICK_NOT_ALLOWED`
- **R-ADMIN-03**: Mỗi action đều **audit log**: `actor=userId owner`, `target=userId`, `action=KICK|REMOTE_MUTE`, `timestamp`, `roomId`, `reason` (optional)

**R-ADMIN-04 (Kick participant - v1.8 NEW)**:

- Khi owner kick user X khỏi phòng:
  - User X chuyển participant state → `KICKED`
  - WS broadcast `PARTICIPANT_KICKED` cho room (mọi participant đang ACTIVE)
  - User X nhận redirect về SC-13 (KICKED screen) hiển thị "Bạn đã bị chủ phòng đuổi khỏi phòng"
  - **Cooldown 5 phút (5 \* 60 = 300 giây)**: User X **KHÔNG thể**:
    - Tự gửi JoinRequest mới cho cùng phòng
    - Rejoin (kể cả khi was_approved = TRUE)
    - Vào phòng qua link/code
  - Sau 5 phút cooldown, user X có thể gửi JoinRequest mới (state = PENDING, owner phải duyệt lại)
  - **was_approved = FALSE**: Khi user bị KICKED, backend set `was_approved = FALSE` cho user đó trong phòng. User không thể rejoin trực tiếp mà phải qua JoinRequest mới để owner duyệt lại
  - **Implement**: Backend lưu `kickedAt` + `kickedFromRoomId` + `kickedCooldownUntil = kickedAt + 300s`. Mọi API Join/Rejoin check `now() < kickedCooldownUntil` → HTTP 429 `KICKED_COOLDOWN` với message `"Bạn đã bị đuổi khỏi phòng này. Vui lòng thử lại sau X phút."`
  - **Cooldown KHÔNG reset** khi reopen phòng (cố ý: user bị kick 1 lần, phải chờ 5 phút, không có ngoại lệ)
  - **Frontend cache KICKED state** (R-KICK-04): sau khi nhận event KICKED, lưu vào localStorage key `kicked:{roomId}` value `{kickedAt, cooldownUntil}`. Reload/navigate về URL phòng → check cache → vẫn thấy KICKED screen, không bị flash sang "ask to join"
- **R-ADMIN-05 (Remote Mute mic - v1.8 NEW)**:
  - Owner có quyền **mute mic** của participant X (KHÔNG tắt camera — tôn trọng privacy)
  - Khi owner remote-mute user X:
    - `participant.micOn = false` (force, kể cả user X đang bật mic)
    - `participant.micState = "MUTED_BY_OWNER"` (state mới, để phân biệt self-mute)
    - WS broadcast `PARTICIPANT_MIC_MUTED_BY_OWNER` cho room
    - User X thấy icon 🔇 + tooltip "Chủ phòng đã tắt mic của bạn"
    - User X **KHÔNG tự bật mic lại** trong 30 giây (cooldown mute) — tránh loop mute/unmute
    - Sau 30 giây, user X có thể self-unmute (nhưng khi bật → WS broadcast `PARTICIPANT_MIC_UNMUTED` → owner được thông báo trên UI)
  - **Audio stream**: Backend force pause audio track của user X. WebRTC peer connection close audio track → không stream audio lên
  - **Permission matrix**: REMOTE_MUTE là permission riêng, áp dụng cho owner. Update Permission Matrix: `OWNER → {APPROVE_JOIN, REJECT_JOIN, KICK, REMOTE_MUTE, END_ROOM, REOPEN_ROOM}` (xem `liveroom-permission-matrix.md`)
  - **UI**: Owner thấy nút "Tắt mic" (icon mic-off) trên tile của mỗi participant. User bị mute có icon 🔇 thay cho mic icon bình thường
- **R-ADMIN-06 (Audit log)**:
  - Lưu table `room_admin_actions`: `id, roomId, actorUserId, targetUserId, actionType, reason, timestamp`
  - Query được qua admin API (POST-MVP)
  - Giữ tối thiểu 90 ngày (POST-MVP sẽ configurable)

**R-KICK-04 (Frontend cache KICKED state - v1.8 NEW)**:

- Sau khi user nhận WS event `PARTICIPANT_KICKED`, frontend cache state vào `localStorage` (cross-tab):
  - Key: `kicked:{roomId}`
  - Value: `{kickedAt: ISO timestamp, cooldownUntil: ISO timestamp, reason: string}`
- **TTL**: 1 giờ (trùng cooldown 5 phút + buffer)
- **Behavior**:
  - User reload page → frontend check `localStorage.kicked:{roomId}` → nếu còn TTL → render SC-13 ngay, không bị flash sang "ask to join"
  - User navigate tới URL phòng → check cache → render SC-13
  - Sau TTL → cache tự expire, user có thể join lại nếu cooldown backend hết
- **Edge case**: User mở tab 2 sau khi bị kick tab 1 → tab 2 cũng thấy SC-13 (vì dùng localStorage, cross-tab sharing)
- **Lý do**: Tránh UX "ghost" — user bị kick, F5 trang, vẫn thấy "ask to join" → confused

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Owner kick user quấy rối
  - An (owner) thấy Bình spam chat → click "Kick Bình"
  - WS broadcast PARTICIPANT_KICKED → Bình navigate về SC-13
  - Bình cooldown 5 phút, không thể vào lại
  - Audit log: "An kicked Bình, reason: spam chat"

Tình huống 2: Owner remote-mute background noise
  - An (owner) nghe thấy tiếng ồn từ mic của Cường
  - An click "Tắt mic" trên tile của Cường
  - WS broadcast PARTICIPANT_MIC_MUTED_BY_OWNER
  - Cường thấy icon 🔇 + tooltip "Chủ phòng đã tắt mic của bạn"
  - Cường cố bật mic lại → bị chặn 30s cooldown
  - Sau 30s, Cường bật mic → WS broadcast PARTICIPANT_MIC_UNMUTED → An được thông báo

Tình huống 3: Kicked user cố rejoin
  - Bình bị kick lúc 10:00:00 → kickedCooldownUntil = 10:05:00
  - Bình click "Vào phòng" lúc 10:01:00 → HTTP 429 KICKED_COOLDOWN
  - Bình thử lại lúc 10:05:01 → được, nhưng JoinRequest mới (state = PENDING, owner phải duyệt)
```

---

### 4.5. Rời phòng (Leave Room)

**Quy tắc**:

- **R-LEAVE-01**: Bất kỳ participant nào cũng có thể rời phòng (kể cả owner)
- **R-LEAVE-02**: Khi rời, slot được giải phóng ngay lập tức
- **R-LEAVE-03**: Sau khi rời, user có thể rejoin (không cần duyệt lại nếu was_approved = TRUE)
- **R-LEAVE-04**: Khi owner rời phòng, đánh dấu `owner_left_at = now` (bắt đầu grace period)
- **R-LEAVE-04.1 (Owner rejoin debounce 3s - v1.8)**: Tránh flash event cho participants
  - Khi owner leave + rejoin **trong vòng 3 giây** → hệ thống **KHÔNG broadcast** `OWNER_LEFT` / `OWNER_RETURNED` qua WS
  - Lý do: Tránh UI flash (participants thấy "Owner left" → vài ms sau lại thấy "Owner returned") gây khó chịu
  - Cách implement: Backend buffer các event trong 3s window. Nếu owner rejoin trước khi buffer flush → không broadcast gì cả. Nếu vẫn absent sau 3s → broadcast `OWNER_LEFT` event
  - **Quan trọng**: Backend vẫn set `owner_left_at = now` trong DB (audit), chỉ WS broadcast bị suppress
- **R-LEAVE-05**: Nếu owner rejoin trong grace period (sau 3s debounce), đánh dấu `owner_left_at = null` (reset)
- **R-LEAVE-06**: Nếu owner không rejoin trong grace period, phòng tự động kết thúc

**R-LEAVE-07 - Grace Period cho owner (ƯU TIÊN CAO NHẤT)**:

- **Thời lượng mặc định**: 60 giây
- **v1.8 GRACE CONFIGURABLE (R-GRACE-01)**: Owner có thể **cấu hình grace duration** khi tạo phòng:
  - **Min**: 30 giây
  - **Max**: 1800 giây (30 phút)
  - **Default**: 60 giây
  - **Đề xuất hợp lý**:
    - Meeting thường: 300 giây (5 phút) — cho phép owner step out briefly
    - Meeting formal: 60 giây (1 phút) — tight schedule
    - Workshop/dài hạn: 600 giây (10 phút) — cho phép giải lao
  - **Implement**: thêm field `ownerGraceSeconds` vào `CreateRoomRequest` (range 30-1800, default 60). Lưu vào `room.ownerGraceSeconds`. Apply khi owner leave
  - **Storage**: `room.ownerGraceSeconds` (int, not null, default 60)
  - **Validate**: Reject nếu < 30 hoặc > 1800 với HTTP 400
  - **Update UI**: Form tạo phòng có toggle "Quick meeting (60s)" / "Custom..." với slider 30-1800s, step 30s
- **Mục đích**: Cho phép owner thoát/đăng nhập lại mà không phải kết thúc phòng
- **Hành vi trong grace period**:
  - Phòng vẫn ở trạng thái ACTIVE
  - Participants khác vẫn có thể tiếp tục họp
  - **Cảnh báo UX với grace dài**: Nếu `ownerGraceSeconds > 600` (10 phút), UI form tạo phòng hiển thị warning: _"Grace period dài có thể gây bất tiện cho participants khi owner mất kết nối"_
  - Owner có thể rejoin để tiếp tục
- **Sau grace period (v1.7 - ENDED nhưng có participants)**:
  - Phòng tự động chuyển sang ENDED **BẤT KỂ** còn participant hay không
  - Tất cả participants được thông báo
  - WS broadcast `ROOM_AUTO_ENDED` với `reason: "owner_grace_expired"` → participants thấy banner "Owner không quay lại — phòng đã kết thúc"
  - Lý do: Phòng không thể tiếp tục nếu owner không quay lại (cần owner để quản lý, end, kick, etc.)
  - Phòng vẫn có thể REOPEN bởi owner (nếu upgrade lại PRO sau khi bị downgrade, xem R-ROLE-07.1)
- **R-LEAVE-08 (Grace ưu tiên Empty Room)**: Khi owner leave và bắt đầu grace period, **quy tắc Empty Room Timeout (R-END-09) bị tạm dừng**
  - Lý do: Grace 60s có chủ đích (cho phép owner rejoin), ưu tiên hơn timeout chung
  - Trong 60s grace: empty room KHÔNG trigger auto-end
  - Sau grace hết: nếu vẫn trống → ENDED luôn (không cần đợi thêm 5min)

**R-LEAVE-09 (Owner slot reserved trong grace - v1.7)**:

- Khi owner leave, **max effective capacity** trong grace period = `max - 1` (giữ 1 slot cho owner rejoin)
- Ví dụ: Phòng max = 7 → đầy 7/7 (gồm owner) → owner leave → effective max = 6 → 1 user mới có thể vào
- Khi owner rejoin trong grace → slot được restore, effective max = max (7)
- **Nếu phòng đầy theo effective max (7/7 với owner ở lại) → owner leave → effective = 6 → có thể nhận 1 user mới**
- **Nếu grace hết → slot của owner vĩnh viễn được giải phóng** (END), max trở về bình thường cho session sau (nếu reopen)
- **v1.8 UX IMPROVEMENT (UX-07)**: Ở UI lobby/participant info, khi owner đang absent (grace period) → hiển thị tooltip trên tile "Owner đang tạm rời" + small badge "1 slot reserved cho owner" trên capacity indicator (e.g. "5/6 + 1 reserved"). Khi grace hết → badge biến mất, hiển thị "6/6 + owner sẽ close"
- Lý do: Tránh tình huống owner rejoin nhưng không còn slot (vừa kỳ cục vừa mất công rejoin)
- Implement: Khi owner leave, set `room.reservedOwnerSlot = true`. Trong approve/rejoin, check `current_count < (max - 1)` nếu `reservedOwnerSlot = true`, ngược lại check `current_count < max`. Khi owner rejoin, set `reservedOwnerSlot = false`. Khi END, reset cả 2.

**R-LEAVE-10 (Auto-join countdown 3s - v1.8 UX-08)** _(Lưu ý: Rule này liên quan đến Join/Approve flow, đặt ở đây vì gắn với UX flow của Leave section)_:

- Khi user được approve → backend tự động tạo participant + session (R-APPROVE-02.1)
- Trước khi auto-redirect sang SC-06, frontend hiển thị **countdown 3 giây** với nút "Vào phòng ngay" và "Hủy"
- Mục đích: Cho user chuẩn bị (bật mic, kiểm tra camera) trước khi vào phòng
- **Cơ chế**:
  - Sau khi nhận WS `REQUEST_APPROVED`, frontend hiển thị modal "Đã được duyệt! Vào phòng trong 3... 2... 1..."
  - Trong 3s, user có thể:
    - Click "Vào phòng ngay" → redirect ngay
    - Click "Hủy" → huỷ auto-join, user ở lại lobby (state JoinRequest = APPROVED, có thể join thủ công bất cứ lúc nào)
  - Sau 3s không click → auto-redirect
- **Edge case**: User F5 / đóng tab → JoinRequest vẫn = APPROVED, lần sau load lại lobby → check auto-join (vẫn chạy countdown 3s)
- **State JoinRequest sau khi cancel auto-join**: vẫn = APPROVED, không bị reset. User join thủ công → state chuyển ACTIVE như bình thường

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Owner rời và rejoin trong grace period
  - An (owner) rời phòng → owner_left_at = now (T0), reservedOwnerSlot = true
  - Lúc T0 + 30s: An rejoin → owner_left_at = null, reservedOwnerSlot = false, An tiếp tục phòng

Tình huống 2: Owner rời và hết grace period (v1.7 - có participants khác)
  - An (owner) rời phòng → owner_left_at = now (T0), reservedOwnerSlot = true
  - Phòng còn 3 participants khác đang họp
  - Lúc T0 + ownerGraceSeconds (mặc định 60s): An chưa rejoin → Phòng auto-end
  - WS broadcast ROOM_AUTO_ENDED với reason="owner_grace_expired"
  - Participants thấy banner "Owner không quay lại — phòng đã kết thúc"

Tình huống 3: Owner rời, có người vào (v1.7 - slot reserved)
  - Phòng max = 7, đang 7/7 (gồm An owner)
  - An leave → owner_left_at = T0, reservedOwnerSlot = true → effective max = 6
  - 1 participant khác cũng rời → current = 5
  - Bình (was_approved) rejoin → pass (5 < 6) → vào → current = 6
  - Lúc T0 + 30s: An rejoin → owner_left_at = null, reservedOwnerSlot = false → current = 7 (max)

Tình huống 4: Participant rời (không phải owner)
  - Bình rời phòng → slot được giải phóng, reservedOwnerSlot không đổi
  - Không ảnh hưởng đến grace period
  - Bình có thể rejoin bất cứ lúc nào (check effective max)
```

---

### 4.6. Kết thúc phòng (End Room)

**Quy tắc**:

- **R-END-01**: Chỉ owner mới có quyền kết thúc phòng
- **R-END-02**: Khi kết thúc, tất cả participants được thông báo
- **R-END-03**: Phòng chuyển sang trạng thái `ENDED`
- **R-END-04**: Tất cả JoinRequest đang `PENDING` chuyển sang `EXPIRED`
- **R-END-04.1**: User ở lobby có JoinRequest state = PENDING/APPROVED → state = EXPIRED, nhận WS `ROOM_ENDED` event riêng để hiển thị "Phòng đã kết thúc"
- **R-END-04.2**: WS `ROOM_ENDED` broadcast cho **CẢ** user có quan tâm: participants ACTIVE + user có JoinRequest pending (lobby). User ở lobby thấy modal thông báo, không phải tự F5
- **R-END-05**: Participants được giữ lại trong lịch sử (lưu `leftAt`)
- **R-END-06**: Sau khi kết thúc, không ai có thể join/rejoin vào phòng
- **R-END-07**: `was_approved` của từng participant được giữ nguyên (vẫn có giá trị nếu phòng được mở lại)

**Auto-End (Hệ thống tự kết thúc)**:

- **R-END-08 - Owner grace period expired (v1.7)**: Owner rời và quá 60s không rejoin — **ƯU TIÊN CAO NHẤT**, áp dụng **KỂ CẢ KHI PHÒNG CÒN PARTICIPANT KHÁC**
  - Phòng tự động ENDED với `endedReason = "owner_grace_expired"` (audit log)
  - WS broadcast `ROOM_AUTO_ENDED` cho tất cả (participants ACTIVE + lobby user)
  - Participants thấy banner "Owner không quay lại — phòng đã kết thúc"
  - Lý do: Phòng không thể tiếp tục quản lý nếu owner không có mặt
- **R-END-09 - Empty room timeout**: Phòng ACTIVE > 5 phút mà không có participant nào (kể cả owner) — **CHỈ áp dụng khi KHÔNG có grace period active**
  - **v1.8 NEW**: Empty room đếm từ `roomSessionCycle.started_at` (đã reset ở reopen R-REOPEN-04.1), KHÔNG dùng `room.created_at`
  - **v1.8 NEW**: Nếu phòng CHỈ còn owner và owner leave → grace period (R-LEAVE-07) trigger. Sau grace → ENDED. KHÔNG trigger empty timeout 5min
  - **v1.8 NEW**: Nếu phòng trống hoàn toàn (0 participant) KHÔNG có owner → áp dụng empty timeout 5min (không có grace — đây là edge case khi owner force-end và còn 1 user join cuối cùng rời)
  - **v1.8 NEW**: Khi empty timeout trigger, broadcast `ROOM_AUTO_ENDED, reason: "empty_timeout"`. Phòng có thể reopen bình thường
- **R-END-10**: Auto-end hoạt động giống manual end, cập nhật `ended_at` cho tất cả participants
- **R-END-11 (v1.7)**: Auto-end vì grace expired **được ưu tiên hơn** empty timeout
  - Khi owner leave + grace đang chạy → empty timeout bị suspend
  - Khi grace hết → ENDED luôn (kể cả empty hay có participants) → không chờ empty 5min

**R-END-12 (Undo End Room trong 5s - v1.8 NEW)**:

- Sau khi owner click "Kết thúc phòng" (R-END-01) → phòng chuyển sang ENDED, WS broadcast `ROOM_ENDED`
- Đồng thời hiển thị **toast ở owner UI** với button "Hoàn tác" (Undo) + countdown 5s
- **Trong 5 giây**, owner có thể click "Hoàn tác":
  - Phòng chuyển về ACTIVE ngay lập tức
  - Tất cả participants nhận lại full media stream (peer connection re-established)
  - WS broadcast `ROOM_REVIVED` để UI cập nhật
  - State trở về như trước khi END (participants vẫn ACTIVE, room_cycle data preserved)
- **Sau 5 giây**: Toast biến mất, **KHÔNG thể undo** nữa. Owner muốn mở lại phòng → phải dùng R-REOPEN (với effect reset rejectCount, v.v.)
- **Edge case**: Nếu có user mới gửi JoinRequest trong 5s undo window → JoinRequest đã EXPIRED theo R-END-04. Khi owner undo → JoinRequest KHÔNG tự động revert (vì EXPIRED là terminal state). User phải gửi lại JoinRequest mới
- **Edge case**: Nếu owner force-end vì downgrade (R-ROLE-06) → KHÔNG có undo (admin action, không phải owner voluntary)
- **Edge case**: Auto-end (grace_expired / empty_timeout) → KHÔNG có undo (owner absent, không có UI hiển thị toast). Chỉ manual end mới có undo
- **Lý do**: Tránh "oops" moment — owner click nhầm nút End có thể recover ngay

**R-END-12.1 (Participant redirect delay khi ENDED - v1.8 NEW)**:

- Khi participant nhận `ROOM_ENDED` (manual end), frontend **DELAY redirect 5s** (không redirect ngay)
- Hiển thị modal "Phòng đã kết thúc" với countdown 5s
- **Trong 5s**, nếu nhận `ROOM_REVIVED` → dismiss modal, tiếp tục ở phòng bình thường
- **Sau 5s** không có REVIVED → redirect về home/history
- **Lý do**: Đảm bảo participants nhận được `ROOM_REVIVED` nếu owner undo — tránh tình huống owner undo thành công nhưng phòng trống vì mọi người đã redirect
- **Chỉ áp dụng cho manual end**: Auto-end (grace_expired/empty_timeout) không có undo → redirect ngay, không cần delay

**Quy tắc ưu tiên auto-end (v1.7)**:

```
Phòng ACTIVE:
  ├─ Owner left_at < ownerGraceSeconds ago (grace active, mặc định 60s)?
  │      ├─ Có → KHÔNG auto-end (chờ grace hết)
  │      └─ Không → Check empty
  └─ Grace expired (owner_left_at + ownerGraceSeconds < now)?
         ├─ Có → Auto-end với reason="owner_grace_expired" (kể cả có participants)
         └─ Không → Check empty
            └─ Empty (current_count = 0)?
                  ├─ Có → Auto-end sau 5min với reason="empty_timeout"
                  └─ Không → Giữ ACTIVE
```

**Trong grace period → Empty Room timeout bị suspend** (xem R-LEAVE-08).

**Lưu ý thực tế**: Owner là người duy nhất trong phòng + owner leave:

- Grace period ownerGraceSeconds (mặc định 60s) bắt đầu
- Sau ownerGraceSeconds mà không rejoin → ENDED luôn (R-END-08)
- KHÔNG cần đợi thêm 5min vì grace đã cover trường hợp này

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Owner kết thúc thủ công
  - An (owner) nhấn "Kết thúc phòng" → Phòng ENDED
  - Tất cả participants nhận thông báo
  - Các JoinRequest PENDING → EXPIRED

Tình huống 2: Owner rời và hết grace period
  - An rời lúc 10:00
  - 10:01: Phòng auto-end
  - Tất cả participants nhận thông báo "phòng đã kết thúc"

Tình huống 3: Phòng trống quá lâu
  - Cả owner và participants đều rời lúc 10:00
  - 10:05: Phòng auto-end (timeout 5 phút)
```

---

### 4.7. Mở lại phòng (Reopen Room)

**Mô tả**: Sau khi phòng kết thúc, owner có thể mở lại phòng để tái sử dụng cho phiên họp mới. Dùng cho use case "phòng họp cố định theo team".

**Quy tắc**:

- **R-REOPEN-01**: Chỉ owner mới có quyền mở lại phòng
- **R-REOPEN-02**: Chỉ phòng ở trạng thái `ENDED` mới có thể reopen
- **R-REOPEN-03**: Khi reopen, phòng chuyển sang `ACTIVE`, bắt đầu phiên mới
- **R-REOPEN-04**: Lưu `previous_ended_at` (thời điểm kết thúc phiên trước) để audit
- **R-REOPEN-04.1 (Reset started_at - v1.7)**: Mỗi lần reopen, **`started_at = now()`** (reset về thời điểm reopen)
  - Lý do: Empty timeout 5min (R-END-09) tính từ `started_at`. Nếu không reset, phòng có thể auto-end ngay khi reopen
- **R-REOPEN-05**: Tăng `reopened_count` mỗi lần reopen
- **R-REOPEN-06**: Cập nhật `last_reopened_at = now`

**R-REOPEN-07 - Xử lý JoinRequest cũ**:

- Khi reopen, **xoá tất cả JoinRequest** của phiên cũ (PENDING, REJECTED, CANCELLED, EXPIRED)
- Lý do: Reopen = meeting mới, clean slate
- User muốn vào phòng sau khi reopen → phải gửi JoinRequest mới
- **Quan trọng**: Reopen CŨNG reset `rejectCount` của tất cả user về 0 (xem R-REJECT-04.1, Bug 1) — user từng bị LOCKED cũng có cơ hội mới

**R-REOPEN-08 - Xử lý participant cũ**:

- Giữ nguyên các participant rows (với `leftAt` đã set)
- **v1.8 CLARIFICATION**: `was_approved = TRUE` chỉ dành cho user đã từng có **participant row ACTIVE** (đã vào phòng từng lần)
- **User chỉ REJECTED_BY_OWNER (kể cả 3 lần → LOCKED) → was_approved = FALSE** → sau reopen phải gửi JoinRequest mới
- **User đã từng vào phòng (ACTIVE) sau đó leave → was_approved = TRUE** → rejoin ngay sau reopen (không cần duyệt lại)
- **Lưu ý về ROLLOVER**: Khi reopen, `was_approved` không bị reset trên DB — nó là flag ổn định cho cả vòng đời phòng. Sau reopen, user đã ACTIVE-lịch-sử giữ was_approved=TRUE
- **Implement**: `was_approved` = `max(join_status = ACTIVE existed)` cho mỗi (user, room) từ trước đến nay. Update khi user chuyển sang ACTIVE state lần đầu
- Lý do: Persistent room pattern, giữ "quyền" cho người đã **từng tham gia thực sự**, không phải chỉ gửi request

**R-REOPEN-09 - History / Lịch sử phiên**:

- Hệ thống lưu lịch sử các phiên:
  - `previous_ended_at` của phiên gần nhất
  - `reopened_count` (tổng số lần reopen)
  - `last_reopened_at` (lần reopen gần nhất)
- Có thể truy vấn lịch sử qua API

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Reopen cơ bản
  - Ngày 1: An tạo phòng "Daily Sync", họp xong, kết thúc lúc 10:00
  - Ngày 2: An nhấn "Mở lại phòng" → Phòng ACTIVE, reopened_count = 1
  - Bình (đã was_approved từ ngày 1) rejoin → vào phòng ngay

Tình huống 2: Reopen - JoinRequest cũ bị xoá
  - Ngày 1: A, B vào phòng. C gửi yêu cầu -> PENDING
  - Phòng kết thúc -> JoinRequest của C: EXPIRED
  - Ngày 2: An reopen phòng
  - C muốn vào lại -> phải gửi JoinRequest mới (JoinRequest cũ đã bị xoá)

Tình huống 3: Reopen nhiều lần
  - Lần 1: Phòng ACTIVE -> ENDED (reopened_count = 0)
  - Reopen -> ACTIVE (reopened_count = 1)
  - Lần 2: Phòng ACTIVE -> ENDED -> Reopen (reopened_count = 2)
  - Lịch sử hiển thị: phòng đã họp 3 phiên (count từ lần tạo đầu + 2 lần reopen)
```

---

### 4.8. Hiển thị danh tính (Display Name)

**Quy tắc**:

- **R-DISPLAY-01**: Trong phòng, mỗi participant được hiển thị bằng **email** của họ
- **R-DISPLAY-02**: Email là **bất biến** (immutable) - người dùng không thể thay đổi email sau khi đăng ký
- **R-DISPLAY-03**: Email là **single source of truth** cho danh tính hiển thị
- **R-DISPLAY-04**: Hiển thị email giúp dễ nhận biết người tham gia trong phòng họp (đặc biệt với team nội bộ)
- **R-DISPLAY-05**: Trong lịch sử phòng, những người đã tham gia hiển thị email tại thời điểm tham gia (audit trail)
- **R-DISPLAY-06 (Email privacy/short form - v1.8 NEW)**: Tôn trọng privacy/GDPR
  - **Mặc định (cùng tổ chức)**: Hiển thị email full (e.g. `an.nguyen@congty.com`)
  - **External user**: User từ **domain khác** với owner → có nút "Ẩn email" ở SC-06 (settings panel)
  - **Khi ẩn email**: UI hiển thị **short form** (`an@congty.com` → `an.ng***@congty.com`). Tooltip hiển thị email full để dễ nhận biết
  - **Mask rule**: Lấy 3 char sau dấu `.` cuối cùng trước `@`, thay bằng 3 dấu `*`. Nếu local part < 3 chars → mask toàn bộ
    - `an.nguyen@congty.com` → `an.ng***@congty.com`
    - `b@congty.com` → `***@congty.com`
    - `super.long.email@external.com` → `super.long.em***@external.com`
  - **Audit log**: Mỗi lần user toggle "Ẩn email" → ghi `user_privacy_action` log. Owner vẫn thấy email full của participants (cho admin/debug)
  - **Out of MVP**: Email full vẫn được lưu DB và trong API responses (cho client-side mask). Server không enforce mask
  - **Lý do**: Một số user (đặc biệt freelance, consultant) muốn ẩn email khỏi người lạ khi tham gia phòng không phải của mình

**R-DISPLAY-07 (Domain detection - v1.8)**: Hệ thống xác định "cùng tổ chức" bằng **email domain trùng với owner email domain**:

- `an@congty.com` tham gia phòng của `owner@congty.com` → same org → hiển thị full email
- `external@gmail.com` tham gia phòng của `owner@congty.com` → different org → hiển thị nút "Ẩn email" (mặc định vẫn full, user tự ẩn)
- **Whitelist domain** (POST-MVP): chỉ áp dụng cho tenant enterprise

**Lý do chọn email làm display name**:

- Đơn giản hoá quy trình tạo phòng (không cần hỏi tên hiển thị)
- Tránh trùng lặp tên (mỗi email là duy nhất)
- Phù hợp với môi trường team nội bộ (mọi người quen biết email nhau)
- Audit trail chính xác (email không đổi)

**Tác động nghiệp vụ**:

- Người dùng **không có quyền đổi email** sau khi đăng ký
- Nếu cần đổi email (hiếm) → phải tạo tài khoản mới
- Hệ thống lưu `user_id` trong participant, tra cứu email từ bảng user

**Ví dụ nghiệp vụ**:

```
Tình huống 1: Hiển thị trong phòng
  - Phòng "Daily Sync" active
  - Danh sách participants:
    - an@company.com (Owner)
    - binh@company.com
    - cuong@company.com

Tình huống 2: Lịch sử tham gia
  - Phòng "Daily Sync" phiên 1 (đã ENDED)
  - 5 người đã tham gia: a@x.com, b@x.com, c@x.com, d@x.com, e@x.com
  - (Hiển thị trong lịch sử)
```

---

### 4.9. Trạng thái Media (Camera & Microphone)

**Mô tả**: Mỗi participant có thể bật/tắt camera và microphone của mình trong phòng. Hệ thống lưu trạng thái media để đồng bộ giữa các client.

**Quy tắc**:

- **R-MEDIA-01**: Mỗi participant có 2 trạng thái media độc lập:
  - `camera_on` (boolean): camera có đang bật không
  - `mic_on` (boolean): microphone có đang bật không
- **R-MEDIA-02**: Mặc định khi vào phòng: `camera_on = FALSE`, `mic_on = FALSE` (off theo privacy)
  - Lý do: tôn trọng quyền riêng tư, user chủ động bật khi sẵn sàng
- **R-MEDIA-03**: User tự do bật/tắt camera và mic bất cứ lúc nào
- **R-MEDIA-04**: Owner **không có** quyền ép participant phải bật camera/mic (tôn trọng privacy)
  - **v1.8 EXCEPTION**: Owner CÓ quyền **mute mic từ xa** (R-ADMIN-05 — REMOTE_MUTE permission). Đây là action 1 chiều (mute, KHÔNG ép bật)
  - **v1.8**: Camera KHÔNG thể bị remote-off (chỉ mic)
- **R-MEDIA-05**: Trạng thái media thay đổi theo thời gian thực, các client khác nhận update qua WebSocket
- **R-MEDIA-06**: Khi participant rời phòng, trạng thái media được lưu cuối cùng vào history (audit)
- **R-MEDIA-07**: Khi participant rejoin, trạng thái media reset về mặc định (`camera_off, mic_off`)
- **R-MEDIA-08**: Khi phòng reopen, tất cả participants reset về trạng thái mặc định
- **R-MEDIA-09 (Mic state machine - v1.8)**: Thêm state `micState` cho mỗi participant:
  - `UNMUTED` (default): user tự bật mic
  - `SELF_MUTED`: user tự tắt mic
  - `MUTED_BY_OWNER` (v1.8 NEW): owner đã remote-mute (xem R-ADMIN-05). User không thể tự bật lại trong 30s cooldown
  - **WS broadcast**: `PARTICIPANT_MIC_STATE_CHANGED` kèm `micState` (không chỉ boolean)
- **R-MEDIA-10 (Mic switch un-mute cooldown 30s - v1.8)**: User bị MUTED_BY_OWNER:
  - Trong 30 giây: User KHÔNG thể tự bật mic (vì micState = MUTED_BY_OWNER, backend enforce)
  - Sau 30 giây: User có thể bật mic (micState → UNMUTED, broadcast `PARTICIPANT_MIC_UNMUTED`)
  - **Lý do**: Tránh loop mute/unmute (user bị mute → bật lại ngay → owner lại mute → ...)
  - **Implement**: Backend lưu `micMutedByOwnerAt + cooldownUntil`. Khi user cố bật mic → check `now() < cooldownUntil` → HTTP 429 với message
- **R-MEDIA-11 (Camera giữ nguyên rule "không ép bật" - v1.8 clarification)**: Owner không có quyền remote-off camera. Lý do: camera gắn liền với hình ảnh cá nhân, nhạy cảm hơn mic
- **R-MEDIA-12 (Idle ghost participant indicator - v1.8 NEW)**: Phát hiện user "idle" trong phòng:
  - **Định nghĩa idle**: User không có hoạt động tương tác trong **10 phút** (no chat message, no media toggle, no reaction)
  - **Indicator**: Tile của user idle hiển thị icon `zZz` ở góc
  - **Sau 30 phút idle**: Vẫn ở ACTIVE (KHÔNG auto-kick). "Nghe thầm" là use case hợp lệ (R-MEDIA-04)
  - **Tracking**: Backend lưu `lastInteractionAt` mỗi user. Update mỗi khi nhận: chat message, media toggle, reaction
  - **Privacy**: Tracker không bao gồm "xem ai đang nói" (active speaker) — đó là WS packet, không phải tương tác bằng chứng user
  - **UX**: Khi user hover vào tile idle → tooltip "An đang nghe thầm (idle 12 phút)"
  - **Lý do**: Giúp owner biết ai đang "ngồi đó" mà không tương tác, dễ mời phát biểu hoặc xác nhận vẫn còn tham gia

**Trạng thái Media khi tham gia**:

| Trạng thái  | Mặc định | Mô tả                                  |
| ----------- | -------- | -------------------------------------- |
| `camera_on` | FALSE    | Camera tắt, người khác không thấy hình |
| `mic_on`    | FALSE    | Mic tắt, người khác không nghe thấy    |

**Kịch bản sử dụng**:

```
Kịch bản 1: Họp chính thức
  - User vào phòng (camera off, mic off)
  - User chủ động bật camera + mic khi muốn phát biểu
  - User tắt mic khi không nói để giảm noise

Kịch bản 2: Bandwidth yếu
  - User mạng chậm → chỉ bật mic, tắt camera
  - Vẫn tham gia họp được, chỉ không hiện hình

Kịch bản 3: Nghe thầm / Xem thầm
  - User chỉ muốn nghe/xem → vào phòng với camera_off, mic_off
  - Hợp lệ, không bị kick

Kịch bản 4: Demo / Presentation
  - Người demo bật mic + camera (chia sẻ nội dung)
  - Người xem chỉ bật mic khi có câu hỏi, camera để xem demo

Kịch bản 5: Lỡ tay bật mic
  - User vô tình bật mic, phát ra tiếng ồn
  - User tự tắt mic → trạng thái cập nhật realtime cho người khác
```

**Lưu ý về quyền riêng tư**:

- Người dùng có **quyền tuyệt đối** với camera/mic của mình
- Hệ thống **không** ép buộc bật media
- Owner có thể yêu cầu (verbal) nhưng không ép technical
- Nếu user vi phạm nghiêm trọng (spam, nội dung xấu) → owner dùng quyền kick (R-ADMIN-01)

**R-MEDIA-13 - Xử lý sự cố media**:

- Nếu mất kết nối media stream (network issue) → trạng thái hiển thị "Connecting..."
- Nếu user từ chối cấp quyền camera/mic → vẫn vào phòng được, media = off
- Nếu trình duyệt không hỗ trợ WebRTC → hiển thị cảnh báo, vẫn cho vào phòng (chỉ voice-only fallback)

**R-MEDIA-14 - Audit media history**:

- Lưu lại lần cuối cùng user có `camera_on`/`mic_on` là khi nào
- Dùng cho audit: "user X có bật mic khi nói không phù hợp không?"
- Lưu ý: KHÔNG lưu nội dung audio/video (privacy)

---

### 4.9.5. Xếp hạng participant (Participant Rank - v1.8 NEW)

**Mô tả**: Một số UI cần biết "ai vào phòng sớm nhất" (oldest), "ai vào gần nhất" (newest), phục vụ cho visual cue (highlight tile, badge).

**R-RANK-01 (Oldest participant definition - v1.8 NEW)**:

- **Oldest participant** = participant có `joinedAt` sớm nhất trong `RoomSessionCycle` hiện tại + `state = ACTIVE`
- **Tie-breaker**: Nếu cùng `joinedAt` (hiếm) → ưu tiên userId nhỏ hơn (UUID order)
- **Scope**: Chỉ tính trong cycle hiện tại. Participant rows từ cycle cũ (sau reopen) KHÔNG tính vào
- **Lý do**: Sau reopen, "first joiner" của cycle mới là người vào đầu tiên của cycle mới, không phải từ cycle cũ
- **Implement**: Query `SELECT * FROM participants WHERE roomSessionCycleId = :currentCycle AND state = 'ACTIVE' ORDER BY joinedAt ASC LIMIT 1`
- **Use case**: UI có thể highlight tile của oldest với badge "Vào đầu tiên" (POST-MVP)

**R-RANK-02 (Newest participant - v1.8 NEW)**:

- **Newest participant** = participant có `joinedAt` muộn nhất trong `RoomSessionCycle` hiện tại + `state = ACTIVE`
- **Tie-breaker**: Cùng `joinedAt` → ưu tiên userId lớn hơn
- **Use case**: UI có thể gợi ý "Chào mừng {newest_email} vừa tham gia!" (toast 3s, optional)

**R-RANK-03 (Out of MVP - các loại rank khác)**:

- ❌ Voice activity ranking (top N người nói nhiều nhất)
- ❌ Reaction count ranking
- ❌ Attendance streak (tham gia N meeting liên tiếp)
- ❌ Leaderboard toàn phòng (PVP-style)

---

### 4.10. Phân quyền Role (PRO vs USER)

**Mô tả**: Live Room phân biệt rõ 2 role trong hệ thống IAM:

- **PRO**: Có quyền tạo và quản lý phòng (trở thành owner)
- **USER (non-PRO)**: Chỉ tham gia phòng với vai trò Participant, không có quyền tạo phòng

**Quy tắc**:

- **R-ROLE-01**: User có role `PRO` mới có quyền tạo phòng
  - Nếu user không có role PRO → trả về `403 FORBIDDEN` với message `LIVEROOM_PRO_REQUIRED`
- **R-ROLE-02**: Khi tạo phòng, owner **luôn là PRO** — role này không thay đổi trong suốt phiên (kể cả khi PRO bị downgrade giữa phiên — xem R-ROLE-06)
- **R-ROLE-03**: PRO có thể có **nhiều phòng đồng thời** (persistent rooms), không giới hạn số phòng
- **R-ROLE-04**: USER (non-PRO) vẫn có đầy đủ quyền Participant:
  - Join / Leave / Rejoin phòng
  - Bật/tắt camera/mic
  - Xem lịch sử phòng (nếu đã từng tham gia)
- **R-ROLE-05**: USER không có quyền quản lý phòng: không thể duyệt/từ chối JoinRequest, không thể end/reopen/kick

**R-ROLE-06 - Downgrade giữa phiên (v1.7)**:

- Khi user bị downgrade từ PRO xuống USER (admin thay đổi role):
  - **Phòng do user TẠO (user là owner) đang ACTIVE** → hệ thống **force-end ngay lập tức**:
    - status = ENDED, endedReason = "force_role_change"
    - Tất cả participants nhận thông báo "phòng đã kết thúc do thay đổi quyền của chủ phòng"
    - Lý do force-end: owner không đủ quyền để tiếp tục quản lý
  - **Phòng do user TẠO nhưng đã ENDED (lịch sử)** → giữ nguyên, chỉ owner không thể reopen
  - **Phòng ENDED → Reopen** → bị chặn (R-ROLE-07)
  - **Phòng user là PARTICIPANT (của owner khác) đang ACTIVE (v1.7)**: KHÔNG bị force-end, KHÔNG bị ảnh hưởng
    - User vẫn là participant bình thường trong phòng đó
    - Quyền participant không phụ thuộc vào role hệ thống (PRO/USER), chỉ phụ thuộc vào `participant.state = ACTIVE`
    - Lý do: Quyền trong phòng theo vai trò (owner/participant), không theo role hệ thống (R-ROLE-09)
    - **Lưu ý**: Khi user là participant ở phòng owner khác → user bị downgrade → user vẫn tham gia được bình thường, không cần rejoin

**R-ROLE-07 - Giới hạn sau downgrade**:

- User bị downgrade PRO → USER:
  - **KHÔNG thể reopen** phòng cũ (trong lúc đang USER)
  - **KHÔNG thể tạo phòng mới** (trong lúc đang USER)
  - Vẫn có thể join/rejoin vào phòng của owner khác (với vai trò Participant)
- Lý do: Bảo vệ chất lượng dịch vụ, chỉ PRO mới được "host" meeting

**R-ROLE-07.1 - Upgrade lại PRO sau downgrade**:

- Khi user được upgrade USER → PRO (sau khi đã từng bị downgrade):
  - Có thể tạo phòng mới ngay lập tức
  - **Có thể reopen phòng cũ** (kể cả phòng đã ENDED từ lâu) — vì `rejectCount` đã reset theo R-REJECT-04.1 (Bug 1)
  - Các quyền khác (approve/reject/kick/...) khôi phục bình thường
  - Tất cả phòng owner trước đây (cả ENDED lẫn ACTIVE) trở thành phòng có thể reopen

**R-ROLE-08 - Upgrade từ USER lên PRO**:

- User được upgrade từ USER lên PRO:
  - Có thể tạo phòng mới ngay lập tức
  - Không ảnh hưởng đến phòng đã tham gia (vẫn là Participant)
  - Không tự động trở thành owner của phòng nào

**R-ROLE-09 - PRO tham gia phòng của owner khác**:

- PRO hoàn toàn **được phép** join phòng của owner khác với vai trò Participant
- Trong phòng của owner khác, PRO **chỉ là Participant** thông thường:
  - Có đầy đủ quyền Participant như R-ROLE-04
  - **KHÔNG có quyền owner** trong phòng đó (không duyệt/reject/kick/end/reopen)
  - Quyền owner chỉ thuộc về owner gốc của phòng
- Lý do: Phân quyền theo **vai trò trong phòng** (owner/participant), không phân quyền theo **role hệ thống** (PRO/USER)

**R-ROLE-10 - Multi-session (1 user, nhiều phòng)**:

- 1 user có thể đồng thời:
  - Là owner phòng A (đang quản lý)
  - Là participant phòng B (đang tham gia)
  - Là participant phòng C (đang tham gia)
- Hỗ trợ multi-tab và multi-device (VD: An mở tab 1 quản lý phòng mình, tab 2 tham gia phòng B)
- Không giới hạn số session đồng thời
- Realtime gateway xử lý mỗi session riêng biệt theo key `userId + roomId`

**R-ROLE-11 - Quy tắc giới hạn cho chính phòng mình**:

- PRO **không tự join được phòng của chính mình** (vì đã là owner, không cần JoinRequest)
- Nếu PRO muốn "test phòng" → có quyền owner thao tác trực tiếp (End/Reopen/Kick...)
- Lý do: Tránh JoinRequest thừa cho chính phòng mình

**R-ROLE-12 (1 room = 1 owner tuyệt đối - v1.8 NEW)**:

- **KHÔNG có co-owner** trong MVP. 1 phòng chỉ có đúng 1 owner tại mọi thời điểm
- **Transfer ownership**: MVP KHÔNG hỗ trợ chuyển owner cho user khác. Nếu owner muốn thoát → dùng R-LEAVE (hoặc R-END nếu muốn đóng phòng). Phòng persistent khi owner downgrade sẽ vẫn lưu owner_id (audit)
- **Downgrade behavior** (R-ROLE-06 recap): Phòng ACTIVE → force-end ngay, KHÔNG auto-transfer
- **Upgrade back behavior** (R-ROLE-07.1 recap): Khi user được upgrade lại PRO → có thể REOPEN phòng cũ (giữ owner_id của user đó trong `room.owner_id`)
- **Lý do**: Đơn giản hoá RBAC, tránh conflict permission. Multi-ownership là complex feature (ngoài MVP scope)

**R-ROLE-13 (Room ownership audit - v1.8 NEW)**:

- Khi owner bị downgrade → phòng ENDED, **KHÔNG xoá** `room.owner_id` (giữ nguyên để audit)
- Khi owner upgrade back → có thể REOPEN phòng cũ với owner_id giữ nguyên
- **Audit table** `room_ownership_history`:
  - `id, roomId, ownerUserId, changedAt, changeType, reason`
  - `changeType`: `INITIAL_CREATE | ROLE_DOWNGRADE | ROLE_UPGRADE | FORCE_END`
  - Lưu MỖI LẦN owner thay đổi/quay lại
- **API** (POST-MVP): GET /rooms/{id}/ownership-history
- **Lý do**: Audit trail cho việc thay đổi quyền, debug khi có issue về ownership

**R-ROLE-14 (Owner không thể mời user khác làm "quản lý phụ" - v1.8)**: MVP không có cơ chế delegate permission. Owner phải tự approve/reject/kick. Nếu owner cần "trợ lý" → cơ chế chia sẻ temporary owner (POST-MVP)

**Ví dụ nghiệp vụ**:

```
Tình huống 1: USER cố tạo phòng
  - Bình (role = USER) gọi API create room
  - Hệ thống trả 403 với message "Chỉ tài khoản PRO mới có quyền tạo phòng"
  - Bình phải nâng cấp lên PRO hoặc nhờ owner khác tạo phòng

Tình huống 2: PRO downgrade giữa phiên họp
  - An (PRO) tạo phòng "Daily Sync", 5 người đang họp
  - Admin downgrade An xuống USER
  - Hệ thống tự động:
    - Phòng chuyển sang ENDED
    - Tất cả participants nhận thông báo
    - An vẫn là "owner" trong history nhưng không thể reopen

Tình huống 3: PRO downgrade rồi reopen
  - An (PRO) tạo phòng, kết thúc (ENDED)
  - Admin downgrade An xuống USER
  - An thử reopen → 403 (PRO_REQUIRED)
  - An upgrade lại PRO → có thể reopen bình thường

Tình huống 4: PRO có nhiều phòng
  - An (PRO) tạo "Daily Sync", "Sprint Review", "1-on-1 Bình"
  - Cả 3 phòng đều thuộc sở hữu của An
  - An quản lý độc lập từng phòng
```

---

## 5. State Machine (Vòng đời phòng)

```
                    ┌─────────────┐
                    │   ACTIVE    │ ← trạng thái ban đầu sau khi tạo
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┬───────────────────┐
        │                  │                  │                   │
   (owner              (owner           (last person          (60s grace
    leaves)             ends)            leaves)               expired)
        │                  │                  │                   │
        ▼                  ▼                  ▼                   ▼
   (owner_left_at    ┌─────────┐           ┌─────────┐       ┌─────────┐
    != null)         │  ENDED  │           │  ENDED  │       │  ENDED  │
   (grace 60s)       └────┬────┘           └────┬────┘       └────┬────┘
        │                 │                     │                  │
        │                 │                     │                  │
        │ (owner rejoin   │                     │                  │
        │  trong 60s)     │                     │                  │
        │                 │                     │                  │
        └──────► ACTIVE   │                     │                  │
                          │                     │                  │
                          │ (owner reopen)      │                  │
                          │                     │                  │
                          ▼                     ▼                  ▼
                    ┌─────────────────────────────────────────────────┐
                    │                  ACTIVE                         │
                    │  (reopen - reopened_count++, last_reopened_at) │
                    └─────────────────────────────────────────────────┘
                          │
                          (loop - có thể ENDED -> ACTIVE nhiều lần)
```

**Các trạng thái chính**:

| Trạng thái | Mô tả                                                  |
| ---------- | ------------------------------------------------------ |
| `ACTIVE`   | Phòng đang hoạt động, có thể join/leave                |
| `ENDED`    | Phòng đã kết thúc, không thể join/leave, có thể reopen |

**Các trigger chuyển trạng thái (v1.7)**:

| Trigger              | Từ             | Đến            | Điều kiện                                                              |
| -------------------- | -------------- | -------------- | ---------------------------------------------------------------------- |
| Owner leaves         | ACTIVE         | ACTIVE (grace) | owner_left_at = now, reservedSlot = true (R-LEAVE-09)                  |
| Owner rejoins        | ACTIVE (grace) | ACTIVE         | Trong ownerGraceSeconds (mặc định 60s), owner_left_at = null, reservedSlot = false |
| Owner ends           | ACTIVE         | ENDED          | Owner action (manual)                                                  |
| Grace expired (v1.7) | ACTIVE (grace) | ENDED          | owner_left_at + ownerGraceSeconds < now (kể cả có participants khác - R-END-08 v1.7) |
| Empty timeout        | ACTIVE         | ENDED          | current_count = 0 + 5min, KHÔNG có grace active                        |
| Owner reopens (v1.7) | ENDED          | ACTIVE         | Owner action, started_at = now(), rejectCount = 0                      |

**v1.7 LƯU Ý quan trọng về State Machine**:

- Owner leave KHÔNG đi thẳng tới ENDED - luôn vào trạng thái ACTIVE (grace)
- Trong grace: owner_left_at = now, reservedOwnerSlot = true → max effective = max - 1 (R-LEAVE-09)
- Sau grace (ownerGraceSeconds, mặc định 60s) → ENDED với endedReason = "owner_grace_expired" (R-END-08)
- KỂ CẢ khi phòng còn participants khác (v1.7 thay đổi so với v1.6)
- Trigger "last person leaves" chỉ áp dụng khi người rời cuối cùng KHÔNG PHẢI owner - empty timeout 5min sẽ trigger sau

---

## 6. Tình huống nghiệp vụ (Use Cases chi tiết)

### 6.1. UC-01: Tạo và họp ngay

```
Actor: Owner (An)
Mục đích: Tạo phòng và bắt đầu họp ngay lập tức

Luồng chính:
1. An nhấn "Tạo phòng mới"
2. An nhập tên phòng (VD: "Daily Sync Team A")
3. Hệ thống tạo phòng:
   - status = ACTIVE
   - An được thêm vào với vai trò owner
   - capacity = 7 (mặc định)
   - current_count = 1
4. An vào phòng, có thể mời người khác

Luồng thay thế:
- 3a. Nếu có lỗi hệ thống → thông báo lỗi, không tạo phòng
```

### 6.2. UC-02: Tham gia phòng (lần đầu)

```
Actor: Participant (Bình)
Mục đích: Vào phòng đang họp

Luồng chính:
1. Bình nhấn "Tham gia phòng" với roomCode
2. Hệ thống kiểm tra:
   - Phòng tồn tại và ACTIVE
   - Bình có phải was_approved không? → KHÔNG (lần đầu)
3. Bình gửi JoinRequest với status PENDING
4. An (owner) nhận thông báo có người muốn vào
5. An duyệt yêu cầu:
   - Còn slot → APPROVED, Bình vào phòng
   - Hết slot (v1.6) → REJECTED_BY_CAPACITY, reason "Room is full"
6. Bình nhận thông báo duyệt, vào phòng

Luồng thay thế:
- 2a. Phòng không tồn tại → 404
- 2b. Phòng đã ENDED → 403
- 5a. An từ chối → REJECTED_BY_OWNER, Bình có thể gửi yêu cầu mới
```

**UX-05 (SC-04 progress bar step - v1.8)**: Top bar hiển thị "Bước X/4" để user biết họ đang ở đâu trong flow:

- **Phase 1 (Bước 1/4) — Nhập roomCode**: SC-02 form nhập mã phòng
- **Phase 2 (Bước 2/4) — Pre-join (SC-04)**: Preview mic/cam, điền tên hiển thị (optional)
- **Phase 3 (Bước 3/4) — Chờ duyệt (SC-05)**: Trang "Đang chờ chủ phòng duyệt..." (PENDING state)
- **Phase 4 (Bước 4/4) — Vào phòng (SC-06)**: Loading + auto-join (countdown 3s theo R-LEAVE-10)
- **Visual**:
  - Top bar với 4 ô tròn, đánh số 1, 2, 3, 4
  - Bước hiện tại: filled (màu primary)
  - Bước đã qua: filled nhạt + checkmark
  - Bước chưa tới: outline only
  - Bước bị skip (e.g., was_approved → skip Phase 3): hiển thị icon ↪️ "Bỏ qua"
- **Line kết nối**: giữa các bước có line ngang, đổi màu khi pass
- **Lý do**: User biết họ đang ở đâu, không bị "mysterious flow" (giống Google Meet form)
- **Edge case**: User là was_approved → skip Phase 3 (không cần chờ duyệt) → bar hiển thị "Bước 1/3" (compressed)

### 6.3. UC-03: Rejoin phòng

```
Actor: Participant (Bình)
Mục đích: Vào lại phòng đã từng tham gia

Luồng chính:
1. Bình đã từng vào phòng (was_approved = TRUE)
2. Bình thực hiện thao tác "Join" với roomCode
3. Hệ thống kiểm tra:
   - Phòng ACTIVE
   - Bình có was_approved = TRUE → được phép rejoin
4. Kiểm tra slot:
   - Còn slot → Bình vào phòng ngay
   - Hết slot → từ chối, Bình đợi
5. Khi có slot, Bình thử lại → vào phòng

Luồng thay thế:
- 3a. Phòng ENDED → 403 (không cho rejoin)
- 4a. Hết slot → thông báo "phòng đầy, vui lòng đợi"
```

### 6.4. UC-04: Owner rời và quay lại

```
Actor: Owner (An)
Mục đích: Tạm rời phòng và quay lại

Luồng chính:
1. An nhấn "Rời phòng"
2. Hệ thống đánh dấu:
   - owner_left_at = now
   - An không còn trong danh sách participants
3. An thực hiện rejoin trong 60s
4. Hệ thống reset:
   - owner_left_at = null
   - An được thêm lại vào phòng
5. Phòng tiếp tục họp bình thường

Luồng thay thế:
- 3a. An không rejoin trong 60s → phòng auto-end
```

### 6.5. UC-05: Kết thúc phòng

```
Actor: Owner (An)
Mục đích: Kết thúc phiên họp

Luồng chính:
1. An nhấn "Kết thúc phòng"
2. Hệ thống xác nhận (modal "Bạn có chắc?")
3. Hệ thống cập nhật:
   - status = ENDED
   - ended_at = now
   - Tất cả participants: leftAt = now
   - Tất cả JoinRequest PENDING → EXPIRED
4. Tất cả participants nhận thông báo
5. An có thể chọn "Mở lại phòng" hoặc "Đóng"
```

### 6.6. UC-06: Mở lại phòng (Persistent Room)

```
Actor: Owner (An)
Mục đích: Tái sử dụng phòng cho phiên họp mới

Luồng chính:
1. Phòng đã ENDED
2. An nhấn "Mở lại phòng"
3. Hệ thống cập nhật:
   - status = ACTIVE
   - previous_ended_at = ended_at cũ
   - reopened_count++
   - last_reopened_at = now
   - current_count = 0
   - Xoá tất cả JoinRequest cũ
   - Giữ nguyên participant rows (với was_approved)
4. An tự động vào phòng (với vai trò owner)
5. Những người đã was_approved có thể rejoin ngay

Luồng thay thế:
- 2a. Phòng không phải ENDED → lỗi (chỉ reopen được khi ENDED)
- 2b. An không phải owner → 403
```

### 6.7. UC-07: Auto-end khi phòng trống

```
Actor: Hệ thống (scheduler)
Mục đích: Tự động kết thúc phòng không có ai

Luồng chính:
1. Scheduler chạy mỗi 60s
2. Tìm phòng ACTIVE có current_count = 0 và started_at > 5 phút trước
3. Auto-end phòng:
   - status = ENDED
   - ended_at = now
   - previous_ended_at = ended_at cũ (nếu có)
4. Ghi log audit

Lưu ý: Grace period cho owner (60s) được xử lý riêng bởi cơ chế R-LEAVE-04
```

### 6.8. UC-08: Bật/tắt camera và mic trong phòng

```
Actor: Participant (Bình)
Mục đích: Kiểm soát camera và microphone của mình

Luồng chính (bật camera):
1. Bình đang trong phòng, camera_off, mic_on
2. Bình nhấn nút "Bật camera"
3. Hệ thống gửi yêu cầu cấp quyền camera (nếu chưa cấp)
4. User chấp nhận → camera_on = TRUE
5. WebSocket broadcast trạng thái media mới cho các participant khác
6. Người khác thấy video của Bình

Luồng chính (tắt mic):
1. Bình đang nói, mic_on = TRUE
2. Bình nhấn nút "Tắt mic"
3. mic_on = FALSE
4. Người khác không nghe thấy Bình nữa
5. Bình vẫn nghe người khác nói

Luồng thay thế:
- 3a. User từ chối cấp quyền → camera_on = FALSE, hiển thị cảnh báo
- 3b. Thiết bị không có camera → camera_on = FALSE, hiển thị "No camera available"
- 3c. Trình duyệt không hỗ trợ WebRTC → hiển thị cảnh báo, vẫn cho vào phòng
```

### 6.9. UC-09: Nghe thầm / Xem thầm

```
Actor: Participant (Bình)
Mục đích: Tham gia phòng chỉ để nghe/xem, không tương tác

Luồng chính:
1. Bình vào phòng (camera_off, mic_off theo mặc định)
2. Bình KHÔNG bật camera hoặc mic
3. Bình nghe mọi người nói, xem mọi người nói
4. Người khác thấy Bình trong danh sách nhưng camera_off

Quy tắc:
- Đây là kịch bản hợp lệ, không bị kick
- Dùng cho observer, người học, người xem demo
- Owner có thể cấu hình: có cho phép "chỉ nghe" không (mặc định: cho phép)
```

---

## 7. Edge Cases & Xung đột (Business Conflicts)

### 7.1. Conflict 1: Phòng đầy + Rejoin

**Tình huống**:

- Bình đã từng vào phòng (was_approved = TRUE)
- Bình rời phòng
- Sau đó phòng đầy 7/7 với người mới
- Bình muốn rejoin

**Giải pháp nghiệp vụ**:

- `was_approved = TRUE` = user **được phép** rejoin (không cần duyệt)
- NHƯNG vẫn phải tuân thủ FIFO: nếu phòng đầy → đợi slot
- Không có queue ưu tiên, ai cũng phải đợi khi phòng đầy

### 7.2. Conflict 2: Phòng đầy + Phê duyệt mới

**Tình huống**:

- Phòng đầy 7/7
- Có người gửi JoinRequest mới
- Owner muốn duyệt

**Giải pháp nghiệp vụ (v1.6)**:

- Approve khi phòng đầy → REJECT luôn với reason "Room is full" (R-APPROVE-05)
- User bị reject → toast "Phòng đã đầy, vui lòng thử lại sau"
- Owner muốn tạo slot → kick người khác, sau đó user mới gửi JoinRequest lại
- **KHÔNG có queue/APPROVED_WAITING** (bỏ từ v1.6)

### 7.3. Conflict 3: Reopen + JoinRequest cũ (đã chốt)

**Tình huống**:

- Phiên 1: C gửi JoinRequest → bị REJECTED
- Owner kết thúc phòng
- Owner reopen phòng (phiên 2)
- C muốn vào

**Giải pháp nghiệp vụ** (đã chốt Option 1):

- Khi reopen, xoá hết JoinRequest cũ
- C phải gửi JoinRequest mới
- Lý do: Reopen = meeting mới, clean slate, dễ audit

### 7.4. Conflict 4: Reopen + Participant cũ (đã chốt)

**Tình huống**:

- Phiên 1: A, B tham gia (was_approved = TRUE)
- Phòng ENDED
- Reopen phòng

**Giải pháp nghiệp vụ** (đã chốt Option 1):

- Giữ nguyên was_approved = TRUE cho A, B
- A, B có thể rejoin ngay khi phòng ACTIVE (không cần duyệt lại)
- Lý do: Persistent room pattern, người quen thuộc không cần duyệt lại

### 7.5. Conflict 5: Email bất biến + Audit

**Tình huống**:

- User đã tham gia phòng với email "a@company.com"
- (Lưu ý: vì email bất biến, điều này không thể xảy ra trong thực tế)
- Nếu user bị xoá (GDPR), participant row sẽ orphan

**Giải pháp nghiệp vụ**:

- Vì email bất biến → hiển thị email luôn chính xác (không cần sync)
- Nếu user bị xoá → hiển thị "deleted user" trong lịch sử
- Audit trail vẫn chính xác (chỉ cần user_id, tra cứu lại được)

### 7.6. Conflict 6: Owner leave + Reopen

**Tình huống**:

- Owner rời phòng, grace period 60s
- Trong 60s, owner nhấn "Mở lại phòng" (reopen) thay vì rejoin

**Giải pháp nghiệp vụ**:

- Reopen chỉ áp dụng cho phòng ENDED
- Trong grace period, phòng vẫn ACTIVE → không thể reopen
- Owner chỉ có 2 lựa chọn: rejoin hoặc đợi auto-end

### 7.7. Conflict 7: User không cấp quyền camera/mic

**Tình huống**:

- User truy cập phòng nhưng trình duyệt hỏi cấp quyền camera/mic
- User từ chối (click "Block")

**Giải pháp nghiệp vụ**:

- User **vẫn vào phòng được** (chỉ voice-only / chỉ xem)
- Trạng thái media = off
- Hiển thị hướng dẫn: "Bạn có thể bật camera/mic bất cứ lúc nào"
- Không có chức năng "bắt buộc bật media"

### 7.8. Conflict 8: Nhiều người bật camera khi mạng yếu

**Tình huống**:

- Phòng 7 người, tất cả bật camera
- 2-3 người dùng mạng yếu → video giật/lag/đứng hình

**Giải pháp nghiệp vụ**:

- Không có giải pháp nghiệp vụ, đây là vấn đề kỹ thuật
- Frontend nên hiển thị cảnh báo "Mạng không ổn định, nên tắt camera"
- Có thể khuyến nghị trong UI: "Tắt camera nếu mạng chậm"
- Không tự động tắt camera của user (tôn trọng quyền kiểm soát)

### 7.9. Conflict 9: Owner yêu cầu bật camera, user từ chối

**Tình huống**:

- Owner yêu cầu (verbal) tất cả bật camera cho buổi thuyết trình
- Một số user không muốn bật

**Giải pháp nghiệp vụ**:

- Owner **không có quyền ép** user bật camera
- Nếu user vi phạm nghiêm trọng (spam, không phù hợp) → owner dùng quyền kick
- Nếu chỉ đơn giản là không muốn bật camera → tôn trọng

### 7.10. Conflict 10: Mic/camera lỗi giữa cuộc họp

**Tình huống**:

- Đang họp, mic/camera user bị lỗi (driver, thiết bị)
- User vẫn muốn tiếp tục họp (chỉ nghe, hoặc dùng thiết bị khác)

**Giải pháp nghiệp vụ**:

- Hệ thống tự động chuyển trạng thái media = off nếu mất stream
- User có thể thử refresh, chuyển thiết bị
- Không bị kick khỏi phòng vì lỗi media

### 7.11. Conflict 11: Guest bị REJECTED 3 lần liên tiếp

**Tình huống**:

- User Bình gửi JoinRequest → REJECTED do owner (lần 1)
- User Bình gửi lại → REJECTED do owner (lần 2)
- User Bình gửi lại → REJECTED do owner (lần 3)
- User Bình gửi lại lần 4

**Giải pháp nghiệp vụ** (R-REJECT-04 v1.7 + R-REJECT-04.1):

- Hệ thống đếm **`rejectCountByOwner`** của Bình cho phòng này trong **cùng 1 phiên** (KHÔNG tính REJECTED do phòng đầy)
- Khi `rejectCountByOwner >= 3` → **LOCKED** cho phiên hiện tại của phòng này
- JoinRequest tiếp theo → **403 FORBIDDEN** + message `LIVEROOM_REQUEST_LOCKED`
- **Khi owner reopen phòng** → CẢ 2 counter (`rejectCountByOwner` + `rejectCountByCapacity`) được reset về 0 (R-REJECT-04.1), Bình có thể gửi request mới
- **Quyết định MVP**: KHÔNG có whitelist trong phiên. Owner nên cân nhắc kỹ trước khi reject (vì lock chỉ có hiệu lực đến khi reopen)
- Triết lý: "Lock chỉ là hình phạt cho phiên đó, không phải lifetime vĩnh viễn" — công bằng hơn cho user

**Phân biệt 2 loại REJECTED (v1.7)**:

- **REJECTED_BY_OWNER**: Owner chủ động reject (có chủ đích) → tính vào `rejectCountByOwner`
- **REJECTED_BY_CAPACITY**: Phòng đầy (R-APPROVE-05) → chỉ tính vào `rejectCountByCapacity`, KHÔNG trigger LOCKED
- Khi user nhận REJECTED, message sẽ khác nhau:
  - REJECTED_BY_OWNER: "Yêu cầu đã bị từ chối" (chung chung)
  - REJECTED_BY_CAPACITY: "Phòng đã đầy, vui lòng thử lại sau" (có thể retry thoải mái)

**Ví dụ (v1.7 - tách 2 counter)**:

```
Bình gửi request 1 → An reject (rejectCountByOwner = 1)
Bình gửi request 2 → An reject (rejectCountByOwner = 2)
Bình gửi request 3 → An reject (rejectCountByOwner = 3) → LOCKED
Bình gửi request 4 → 403 "Bạn đã bị từ chối 3 lần, không thể gửi yêu cầu cho phiên này"
Bình thử lại lần 5 → vẫn 403 (LOCKED trong phiên)
Phòng ENDED → reopen (cả 2 counter reset = 0) → Bình thử lại → 201 OK ✓

Trường hợp khác: Bình gửi request → An vô tình Approve khi phòng đầy
→ REJECTED với rejectionReason = ROOM_FULL → rejectCountByCapacity = 1, rejectCountByOwner = 0
→ Bình gửi lại ngay → 201 OK (không bị LOCKED)
```

### 7.12. Conflict 12: PRO bị downgrade giữa phiên

**Tình huống**:

- An (PRO) đang là owner phòng "Daily Sync", 5 người đang họp
- Admin downgrade An xuống USER (vì lý do nào đó: hết hạn gói, vi phạm, ...)
- Phòng đang ACTIVE

**Giải pháp nghiệp vụ** (R-ROLE-06):

- Hệ thống **force-end phòng ngay lập tức**:
  - status = ENDED
  - ended_at = now
  - Tất cả participants nhận thông báo "Phòng đã kết thúc"
  - Lý do: owner không đủ quyền để tiếp tục quản lý
- v1.4 (Bug 5): An vẫn giữ ownership của phòng trong lịch sử (giữ user_id), và **CÓ THỂ reopen phòng này SAU KHI upgrade lại PRO** (vì rejectCount đã reset theo R-REJECT-04.1 khi reopen)
- An vẫn có thể join phòng của PRO khác (với vai trò Participant) ngay cả khi đang USER

**Ví dụ (v1.4 - vẫn có thể reopen sau upgrade)**:

```
Lúc 10:00: An (PRO) tạo phòng "Daily Sync", 5 người vào
Lúc 10:30: Admin downgrade An xuống USER
Lúc 10:31: Hệ thống tự động end phòng
  - Tất cả participants thấy thông báo "Phòng đã kết thúc"
  - An thấy "Bạn không còn quyền PRO, phòng đã tự kết thúc"
Lúc 11:00: An upgrade lại PRO
  - An có thể tạo phòng mới
  - Phòng "Daily Sync" cũ vẫn ENDED, An CÓ THỂ reopen (xem R-ROLE-07.1)
```

### 7.13. EC-24: Race condition khi approve/rejoin đồng thời (v1.7 NEW)

**Tình huống**:

- Phòng P còn 1 slot trống (6/7)
- User A và User B cùng gửi request join đồng thời
- Owner approve A và B cùng lúc

**Giải pháp nghiệp vụ (R-CAPACITY-02)**:

- Backend sử dụng pessimistic lock `SELECT ... FOR UPDATE` trên `LiveRoom` row
- Request A acquire lock trước → check slot (6 < 7 OK) → tạo participant → current = 7 → release lock
- Request B acquire lock → check slot (7 >= 7 FULL) → REJECT với reason `ROOM_FULL` (R-APPROVE-05)
- Kết quả: A vào phòng, B nhận "Phòng đã đầy"

**Không có lock**:

- Request A và B cùng đọc `current_count = 6` → đều pass
- Cả 2 cùng update → `current_count = 8` → vượt max → **BUG**
- Triệu chứng: WebSocket bị lỗi, video stream bị nhiễu, hoặc user không nhận được stream

**Tương tự cho rejoin**:

- User A và User B cùng rejoin sau khi slot trống
- Lock đảm bảo FIFO tự nhiên (ai acquire lock trước vào trước)

**Implement** (Spring Boot):

```java
@Transactional
public void approveJoinRequest(UUID roomId, UUID requestId) {
  LiveRoom room = entityManager.find(
    LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE
  );
  if (room.getCurrentParticipantCount() >= room.getMaxParticipants()) {
    throw new BusinessException(LiveroomErrorCode.ROOM_FULL);
  }
  // ... approve logic
}
```

---

### 7.5. Chat trong phòng (v1.5 NEW)

### R-CHAT-01 (Scope)

- **Group chat chung** cho toàn bộ phòng (1 channel duy nhất)
- **Không có private DM** trong MVP
- Tất cả ACTIVE participant đều thấy + gửi được
- Owner cũng là participant → có thể gửi/nhận bình thường

### R-CHAT-02 (Permission gửi message)

- Actor phải có `participant.state = ACTIVE` trong phòng
- **Không giới hạn theo role** (owner/participant đều gửi được)
- User bị KICKED / LEFT / OFFLINE / ENDED → KHÔNG gửi được
- User ở lobby (JoinRequest PENDING/REJECTED/...) → KHÔNG gửi được

### R-CHAT-03 (Message structure)

```
ChatMessage {
  id: UUID
  roomId: UUID
  roomSessionCycleId: UUID
  userId: UUID
  userEmail: string (display name)
  content: string (max 500 chars)
  sentAt: timestamp
}
```

- **Display name**: dùng email (theo R-DISPLAY-01, immutable)
- KHÔNG lưu `was_approved` hay role trong ChatMessage (chỉ cần userId)

### R-CHAT-04 (Giới hạn message)

- **Max length**: 500 ký tự (validation ở backend + frontend)
- **Empty message**: KHÔNG cho phép gửi (validation trim + non-empty)
- **Whitespace only**: KHÔNG cho phép
- **HTML/Script**: KHÔNG render HTML — escape tất cả content khi hiển thị (XSS prevention)
- **Markdown**: KHÔNG support trong MVP (text plain)
- **Emoji**: Unicode support (cho phép emoji)

### R-CHAT-05 (Rolling window - storage limit - v1.8 CLARIFIED)

- **Lưu trữ DB**: Backend lưu **TẤT CẢ** messages vào DB, KHÔNG xoá theo rolling window (lưu vĩnh viễn cho audit trail)
- **Hiển thị mặc định qua WS**: Broadcast **200 messages gần nhất của cycle hiện tại** khi user join (`CHAT_HISTORY_SNAPSHOT` event)
  - Query: `WHERE roomSessionCycleId = :currentCycle ORDER BY sentAt DESC LIMIT 200`
  - Sau reopen, phòng mới chỉ có 0 messages (cycle mới) — không lẫn với chat cycle cũ
- **Pagination API (POST-MVP)**: `GET /api/v1/liveroom/{roomId}/chat/messages?cursor={msgId}&size=50`:
  - `cursor` = id của message cũ nhất client đang có → trả về 50 messages cũ hơn
  - Nếu `cursor` không truyền → trả 200 messages mới nhất
  - Response: `{messages: [...], hasMore: boolean, nextCursor: string}`
- **Lý do giữ 200 default**: Performance UI (cuộn 200 messages mượt), không cần load tất cả DB vào memory
- **Lý do KHÔNG xoá rolling window**: Một số user muốn xem lại nội dung meeting cũ (audit, search), không nên mất
- **Implement**: DB table `chat_messages` (id, roomId, userId, content, sentAt, sessionCycleId) với index `(roomId, sentAt DESC)`. **KHÔNG cleanup khi phòng ENDED** — chỉ cleanup sau 90 ngày theo R-CHAT-06 v1.8 (configurable POST-MVP, xem `liveroom-jobs.md` §9)
- **Out of MVP**: Search messages, export chat, message pinning

### R-CHAT-06 (Persistence & lifecycle - v1.8 CLARIFIED)

- **Trong session ACTIVE**: message được lưu in-memory + persist DB (để truy vấn sau)
- **Khi phòng ENDED**: lịch sử chat **vẫn được lưu trong DB** (audit trail). Cleanup job xoá sau **90 ngày** (configurable POST-MVP)
- **Khi phòng REOPEN**: chat history **giữ nguyên** trong DB (vì mỗi message có `sessionCycleId` phân biệt cycle nào). UI khi load chat **MẶC ĐỊNH chỉ hiển thị cycle hiện tại** (R-CHAT-05). User có thể toggle "Xem chat cycle cũ" → load từ DB
- **Lưu ý**: `roomSessionCycleId` được lưu trên mỗi ChatMessage (xem R-REOPEN-04.1). Cho phép query chat theo cycle
- **Lý do**: Chat gắn liền với phiên họp nhưng vẫn cần audit dài hạn (compliance, search meeting records)

### R-CHAT-07 (Realtime delivery)

- Khi user gửi message:
  - Backend persist ChatMessage
  - Backend broadcast `CHAT_MESSAGE_RECEIVED` qua WS cho TẤT CẢ participant ACTIVE trong phòng
  - Trừ user gửi (đã hiển thị local rồi)
- Khi user mới join → backend trả về 200 messages gần nhất qua API `GET /:id/chat/messages`

### R-CHAT-08 (Out of MVP - chat features)

- ❌ File share (image, document)
- ❌ Reactions (emoji reaction lên message)
- ❌ Edit message sau khi gửi
- ❌ Delete message
- ❌ @mention user
- ❌ Read receipt (đã đọc / chưa đọc)
- ❌ Reply thread
- ❌ Private DM
- ❌ Typing indicator ("X đang nhập...")
- ❌ Pin message

**Tất cả các tính năng trên là post-MVP.**

---

### 7.6. Edge case cho Chat (v1.5 NEW)

#### EC-13: Chat khi phòng ENDED (v1.8 CLARIFIED)

```
User A ở SC-06 ACTIVE → đang chat bình thường → Owner end phòng:
1. POST /:id/end → 200 → Room status = ENDED
2. Backend: KHÔNG xoá ChatMessage - lưu DB vĩnh viễn theo R-CHAT-06 v1.8 (audit trail)
3. WS ROOM_ENDED broadcast cho tất cả (Bug 6)
4. UI User A: modal ENDED → redirect
5. Lịch sử chat vẫn tồn tại trong DB - query được qua API GET /chat/messages (POST-MVP)
   hoặc xem cycle cũ qua UI SC-07 session history (R-CHAT-06 v1.8)
```

#### EC-14: Chat khi phòng REOPEN (v1.8 CLARIFIED)

```
User A đang ở phòng P (có 50 chat messages)
Owner end phòng P → reopen phòng P:
1. POST /:id/reopen → 200
2. Backend: giữ nguyên 50 ChatMessage cũ theo cycle (R-CHAT-06 v1.8) - mỗi message
   đã có sessionCycleId phân biệt cycle nào
3. Room status = ACTIVE, rejectCount reset, new RoomSessionCycleId
4. User A vào phòng P lại → UI mặc định chỉ load chat cycle hiện tại (empty cho cycle mới)
   - Toggle "Xem chat cycle cũ" qua SC-07 history view (R-CHAT-06 v1.8, R-ANNOT-08)
```

#### EC-15: User bị KICKED trong khi đang chat

```
User A đang gõ message → bị owner kick:
1. POST /:id/end → WS ROOM_FORCE_KICKED
2. UI A: modal KICKED → leave
3. Nếu A click Send trước khi disconnect → POST /:id/chat/messages
   - Backend check participant.state = ACTIVE
   - Nếu state = KICKED → 403 LIVEROOM_NOT_IN_SESSION
   - Nếu state = ACTIVE (race condition) → message được gửi
```

---

### 7.7. UC-10: Send chat message (v1.5 NEW)

```
Actor: ACTIVE participant trong phòng (kể cả owner)
Precondition: actor.participant.state = ACTIVE
Trigger: User nhập text vào chat input → nhấn Enter hoặc click Send

Flow chính:
1. Frontend validate:
   - content không rỗng sau trim
   - content.length <= 500
2. POST /api/v1/liverooms/:id/chat/messages
   - Body: { content: string }
3. Backend:
   - Check actor có participant.state = ACTIVE
   - Lưu ChatMessage vào DB
   - WS broadcast CHAT_MESSAGE_RECEIVED cho các participant ACTIVE khác
4. Frontend update:
   - User gửi: thêm message vào local state với status = SENT
   - User nhận: thêm message vào local state từ WS event

Edge cases:
- Empty content: 400 LIVEROOM_CHAT_EMPTY
- Too long (> 500): 400 LIVEROOM_CHAT_TOO_LONG
- Not in session: 403 LIVEROOM_NOT_IN_SESSION
- Room ENDED (race condition): 409 LIVEROOM_ROOM_ENDED
```

---

### 7.8. UC-11: Load chat history (v1.5 NEW)

```
Actor: ACTIVE participant vừa join session
Trigger: User vào SC-06 ACTIVE

Flow:
1. Frontend gọi GET /api/v1/liverooms/:id/chat/messages
2. Backend trả về danh sách message (limit 200, mới nhất trước)
3. Frontend render lên chat panel
4. Realtime message mới qua WS

Edge cases:
- Room ENDED: 409 LIVEROOM_ROOM_ENDED (nhưng không sao, user sắp redirect)
- Empty history: trả về [] → UI hiển thị "Chưa có tin nhắn nào"
```

---

### 7.9. Shared Listening trong phòng (v1.6 NEW)

### R-MUSIC-01 (Scope)

- Phòng có thể **phát 1 bài hát tại 1 thời điểm** (single track, không queue)
- Nguồn nhạc: **module Voice đã có** — user upload file MP3/M4A từ trước, lưu trong `Song` entity
- Khi user A chọn bài của A → bài cũ (nếu đang phát) bị stop, bài mới bắt đầu từ đầu
- User **CHỈ được chọn bài của chính mình** (Song.userId = currentUser.id)

### R-MUSIC-02 (Song selection permission)

```
Actor.participant.state = ACTIVE trong phòng
Song.userId = actor.userId (chỉ được chọn bài của mình)

Nếu Song.userId != actor.userId → 403 LIVEROOM_MUSIC_NOT_OWN_SONG
Nếu Song.status != PROCESSED → 409 LIVEROOM_MUSIC_NOT_READY (chưa xử lý xong)
Nếu actor chưa join session → 403 LIVEROOM_NOT_IN_SESSION
Nếu room không ACTIVE → 409 LIVEROOM_ROOM_ENDED
```

### R-MUSIC-03 (Playback control sync - v1.8 CLARIFIED)

- Mọi ACTIVE participant đều có quyền điều khiển: `play`, `pause`, `seek`, `change volume`
- **V1.8 RESCTRICTION**: Khi owner absent (trong grace period), non-owner **KHÔNG thể resume play** — chỉ pause/seek/volume (xem R-MUSIC-11)
- Khi 1 người điều khiển → broadcast `MUSIC_PLAYBACK_STATE_CHANGED` cho cả phòng
- **Race condition protection (R-MUSIC-10 v1.8)**: Backend dùng **optimistic lock** với `@Version` trên PlaybackState row
  - 2 user cùng play/pause trong 100ms → request sau validate version cũ → reject với HTTP 409 `MUSIC_STATE_CONFLICT`
  - Client nhận 409 → re-fetch state mới nhất → apply
  - **Last-write-wins semantics**: Cùng timestamp → order theo `lastUpdatedAt` (server timestamp)
  - **Implement**: Java `@Version` field tự động increment mỗi update. SQL update với `WHERE version = :expectedVersion`
- **Event ordering (v1.8 NEW)**: Mỗi event `MUSIC_PLAYBACK_STATE_CHANGED` kèm `serverTimestamp` + `sequenceNumber`
  - Client apply state theo `sequenceNumber` (monotonic increasing)
  - Trường hợp nhận event cũ hơn (out-of-order) → discard
- **Tolerance latency**: ±500ms (chấp nhận được, giống Spotify Jam)
- **Volume GLOBAL**: Khi 1 người chỉnh volume → cả phòng sync theo (0-100%)
  - **Known UX risk**: Nếu user A set 100% và user B set 20% → loop điều chỉnh. Hầu hết app (Spotify Jam, Discord) dùng personal volume
  - **MVP decision**: Giữ GLOBAL cho đơn giản. POST-MVP cân nhắc: tách **master volume** (global, owner-only) + **personal volume** (local, mỗi user)

### R-MUSIC-04 (Playback state)

```
PlaybackState {
  songId: UUID | null       // null = không phát gì
  songOwnerId: UUID | null  // user đã chọn bài (null nếu không phát)
  songTitle: string | null
  songArtist: string | null
  songDurationSeconds: int
  status: PLAYING | PAUSED  // không có STOP riêng, STOP = chọn bài khác hoặc ENDED
  currentPositionSeconds: double  // position hiện tại (real-time)
  volumePercent: int  // 0-100, global
  startedAt: timestamp  // lúc PLAYING bắt đầu (để tính currentPositionSeconds)
  lastUpdatedAt: timestamp  // server timestamp (cho ordering)
  sequenceNumber: long  // monotonic increment mỗi update (cho client ordering)
  lastUpdatedBy: UUID  // user đã điều khiển cuối cùng
  version: integer  // JPA @Version (optimistic lock, R-MUSIC-10 v1.8)
}
```

### R-MUSIC-05 (Sync khi user mới join)

- User vừa vào SC-06 ACTIVE → backend gửi `MUSIC_PLAYBACK_STATE_CHANGED` (initial state)
- Frontend apply state ngay lập tức (play/pause + position + volume)
- Lý do: user join giữa chừng cần nghe đúng vị trí hiện tại

### R-MUSIC-06 (Owner leave → pause nhạc)

- Khi owner leave → nhạc **PAUSE ngay lập tức** (R-MUSIC-04: status = PAUSED)
- Phòng vẫn ACTIVE (grace 60s — không end theo nhạc)
- Participants khác vẫn ngồi trong phòng, thấy banner "Owner đã rời — nhạc tạm dừng"
- Nếu owner rejoin trong grace → WS broadcast `OWNER_REJOINED` + nhạc **không tự resume** (user phải click play)
- Nếu grace hết → phòng ENDED → nhạc stop luôn

### R-MUSIC-07 (Room ended → music cleanup)

- Khi phòng ENDED → backend xoá `PlaybackState` row của phòng đó
- WS broadcast `ROOM_ENDED` cho cả lobby (giống Bug 6)
- Client dừng phát nhạc ngay lập tức

### R-MUSIC-08 (Out of MVP - music features)

- ❌ Queue nhiều bài liên tiếp (Spotify Jam style)
- ❌ Loop/repeat track
- ❌ Shuffle
- ❌ Multiple concurrent tracks (nhiều phòng track)
- ❌ Equalizer
- ❌ Visualizer (spectrum analyzer)
- ❌ Lyrics display sync
- ❌ Playlist share (share library với user khác)

### R-MUSIC-09 (Music communication chỉ qua STOMP WebSocket - v1.8 NEW - QUYẾT ĐỊNH MC-08)

**MC-08 RESOLUTION**: Music KHÔNG dùng REST API riêng lẻ cho control. Toàn bộ control qua STOMP WebSocket.

**Lý do quyết định**:

- Music là real-time feature, latency-critical (play/pause/seek cần feedback tức thì)
- REST + poll loop → waste bandwidth, race condition nhiều
- WS broadcast tự nhiên đẩy state cho mọi client
- Đơn giản hoá protocol: 1 transport (WS) thay vì 2 (REST + WS)

**Endpoints (STOMP only)**:

| Action                       | STOMP destination                        | Direction       | Payload                              |
| ---------------------------- | ---------------------------------------- | --------------- | ------------------------------------ |
| Subscribe (lắng nghe)        | `/topic/liveroom/{roomId}/music`         | Server → Client | `MUSIC_PLAYBACK_STATE_CHANGED` event |
| Play                         | `/app/liveroom/{roomId}/music/play`      | Client → Server | `{songId: UUID}`                     |
| Pause                        | `/app/liveroom/{roomId}/music/pause`     | Client → Server | `{}`                                 |
| Seek                         | `/app/liveroom/{roomId}/music/seek`      | Client → Server | `{positionSeconds: double}`          |
| Change volume                | `/app/liveroom/{roomId}/music/volume`    | Client → Server | `{volumePercent: int}`               |
| Get current state (one-shot) | `/app/liveroom/{roomId}/music/get-state` | Client → Server | `{}` (response trên topic)           |

**REST chỉ dùng cho**:

- List/select song metadata: `GET /api/v1/liveroom/{roomId}/songs` (query DB)
- Upload song: `POST /api/v1/songs` (gọi Voice module)
- **KHÔNG** có REST endpoint `POST /music/play` (đã bỏ từ v1.8)

**Permission**:

- Subscribe `/topic/liveroom/{roomId}/music` chỉ được khi user có `participant.state = ACTIVE` trong phòng (R-JOIN-10 check)
- Control (play/pause/seek/volume) yêu cầu ACTIVE + R-MUSIC-11 (owner absent check)

**WS scope (R-WS-SCOPE-01)**: User chỉ nhận event music khi ở trong phòng (subscribe thành công). Reconnect → re-subscribe. End/Reopen → unsubscribe tự động

### R-MUSIC-10 (Music race condition protection - v1.8 NEW)

- **2 user cùng play/pause trong 100ms** → race condition
- **Backend handling**: Optimistic lock với JPA `@Version` trên `PlaybackState`
  - SQL: `UPDATE playback_state SET version = version + 1, status = ?, ... WHERE room_id = ? AND version = ?`
  - Nếu 0 rows affected → version conflict → return HTTP 409 `MUSIC_STATE_CONFLICT`
- **Client handling**:
  - User nhận 409 → toast "Nhạc đã được điều khiển bởi người khác, vui lòng thử lại"
  - Client tự re-fetch state mới nhất qua `/app/liveroom/{roomId}/music/get-state`
  - UI update theo state mới nhất
- **Sequence number ordering**: Mỗi event `MUSIC_PLAYBACK_STATE_CHANGED` có `sequenceNumber` từ server
  - Client lưu `lastSequenceNumber` đã apply
  - Event mới với `sequenceNumber <= lastSequenceNumber` → discard (out-of-order)
- **Last-write-wins**: Cùng `lastUpdatedAt` → order theo `sequenceNumber` (server-generated, monotonic)
- **Lý do**: Tránh 2 user cùng tap Play → 1 người success, 1 người 409, không bị mixed state

### R-MUSIC-11 (Music pause khi owner absent - v1.8 NEW - UX-04)

- Khi owner leave (grace period active) → nhạc **PAUSE** (R-MUSIC-06). UI hiển thị banner "Owner đã rời — nhạc tạm dừng"
- **Trong grace period**:
  - Non-owner **KHÔNG thể RESUME** play (click Play → button disabled + tooltip "Chờ owner quay lại")
  - Non-owner CÓ THỂ pause, seek, change volume (vì nhạc đang pause, không cần owner permission)
  - Non-owner CÓ THỂ **chọn bài mới** (R-MUSIC-02) — nhạc mới tự động PLAY khi owner absent? **KHÔNG** — bài mới cũng ở PAUSED state, chờ owner quay lại
- **Khi owner rejoin**:
  - Banner biến mất
  - Button Play enabled cho cả phòng
  - Owner hoặc bất kỳ ai có thể click Play → resume
- **Lý do**: Owner là người chịu trách nhiệm về nội dung phòng (tránh tình huống user chọn nhạc không phù hợp khi owner vắng)
- **Edge case**: Nếu owner bị downgrade → phòng ENDED → music cleanup (R-MUSIC-07), KHÔNG có grace choice nào



### 7.11. Edge Cases cho Music (v1.6 NEW)

#### EC-18: Chọn bài mới khi đang phát (v1.8 STOMP)

```
Phòng P đang phát bài A (3:00 / 5:00)
User B chọn bài B của B vào phòng:
1. STOMP send /app/liveroom/{roomId}/music/play { songId: B }
2. Backend check B.userId = B.id (OK)
3. Backend stop bài A → PlaybackState = { songId: B, status: PLAYING, positionSeconds: 0 }
4. WS broadcast MUSIC_SONG_CHANGED → tất cả ACTIVE participant
5. UI: hiển thị bài B, progress = 0, playing
```

#### EC-19: User mới join giữa chừng bài hát

```
User A vào phòng P lúc bài X đang phát ở 2:30/5:00
1. User join session → ACTIVE
2. Backend nhận request → fetch PlaybackState
3. WS gửi MUSIC_PLAYBACK_STATE_CHANGED initial cho A:
   { songId: X, status: PLAYING, positionSeconds: 150, volumePercent: 80 }
4. UI A: hiển thị bài X, autoplay từ 2:30, volume 80%
```

#### EC-20: Owner leave pause nhạc

```
Owner đang ở SC-06 ACTIVE, nhạc đang phát 2:00
Owner click Leave:
1. POST /:id/sessions/me/leave (owner là participant đặc biệt)
2. Backend set PlaybackState.status = PAUSED, lưu positionSeconds = 120
3. Backend start grace period 60s
4. WS broadcast OWNER_LEFT + MUSIC_PLAYBACK_STATE_CHANGED (status=PAUSED)
5. UI participants: banner "Owner đã rời — nhạc tạm dừng" + player show paused
6. Nếu owner rejoin trong 60s:
   - WS OWNER_REJOINED
   - Nhạc KHÔNG tự resume (user phải click play)
   - Player vẫn ở paused state
7. Nếu grace hết:
   - Phòng ENDED
   - WS ROOM_ENDED
   - Nhạc cleanup
```

#### EC-21: 2 user cùng pause cùng lúc (race condition)

```
User A bấm pause lúc 10:00:00.000
User B bấm pause lúc 10:00:00.100
Cả 2 request cùng đến backend:
1. Request A: set status=PAUSED, positionSeconds=120, lastUpdatedBy=A, lastUpdatedAt=10:00:00.000
2. Request B: set status=PAUSED, positionSeconds=120, lastUpdatedBy=B, lastUpdatedAt=10:00:00.100
3. Last-write-wins: B thắng (lastUpdatedAt mới hơn)
4. WS broadcast MUSIC_PLAYBACK_STATE_CHANGED với lastUpdatedBy=B
5. UI A và B đều thấy paused, lastUpdatedBy=B

Hậu quả: KHÔNG có conflict nghiêm trọng (cả 2 đều pause, chỉ khác "ai pause cuối")
```

---

### 7.14. UC-12: Select song (v1.6 NEW → v1.8 STOMP UPDATED)

```
Actor: ACTIVE participant trong phòng
Trigger: User click vào icon 🎵 → mở "Song Picker" → chọn bài của mình

Flow (v1.8 STOMP):
1. User mở Song Picker (UI): hiển thị danh sách Song của user hiện tại (Song.userId = currentUser.id)
   - Filter: status = PROCESSED (chỉ bài đã xử lý xong mới phát được)
   - Sort: mới nhất trước / theo tên
2. User click chọn bài → STOMP send /app/liveroom/{roomId}/music/play
   - Body: { songId: UUID }
3. Backend (qua STOMP @MessageMapping):
   - Check actor.participant.state = ACTIVE
   - Check Song.userId = actor.userId (chỉ được chọn bài của mình)
   - Check Song.status = PROCESSED
   - Check owner_left_at IS NULL (R-MUSIC-11) - hoặc currentUser là owner
   - Optimistic lock PlaybackState (R-MUSIC-10 v1.8) - race condition 2 user
   - Stop bài cũ (nếu có) → set PlaybackState mới:
     - songId = newSongId
     - status = PLAYING
     - positionSeconds = 0
     - volumePercent = giữ nguyên volume cũ (hoặc default 80%)
     - sequenceNumber++
   - WS broadcast MUSIC_SONG_CHANGED + MUSIC_PLAYBACK_STATE_CHANGED
4. UI tất cả ACTIVE participant: chuyển sang bài mới, autoplay
5. Nếu race condition: STOMP ERROR LIVEROOM_MUSIC_STATE_CONFLICT → client re-fetch state

Edge cases:
- Không phải bài của mình → STOMP ERROR LIVEROOM_MUSIC_NOT_OWN_SONG
- Song chưa PROCESSED → STOMP ERROR LIVEROOM_MUSIC_NOT_READY
- Chưa join session → STOMP ERROR LIVEROOM_NOT_IN_SESSION
- Room ENDED → STOMP ERROR LIVEROOM_ROOM_ENDED
- Owner absent + non-owner → STOMP ERROR LIVEROOM_MUSIC_OWNER_ABSENT (R-MUSIC-11 v1.8)
- User có 0 bài → UI hiển thị "Bạn chưa upload bài hát nào. Tải lên trong module Voice."
```

---

### 7.15. UC-13: Control playback (v1.6 NEW → v1.8 STOMP UPDATED)

```
Actor: ACTIVE participant trong phòng
Trigger: User click play/pause/seek/volume trên player

Flow play (v1.8 STOMP - chỉ resume bài hiện tại, không chọn bài mới):
1. User click Play → STOMP send /app/liveroom/{roomId}/music/play
   - Body: {} (không có songId - chỉ resume)
   - Nếu muốn chọn bài mới → xem UC-12
2. Backend (qua STOMP @MessageMapping):
   - Check actor.participant.state = ACTIVE
   - Check PlaybackState.songId IS NOT NULL
   - Check owner_left_at IS NULL (R-MUSIC-11) - hoặc currentUser là owner
   - Optimistic lock PlaybackState (R-MUSIC-10 v1.8)
   - Set PlaybackState.status = PLAYING, lastUpdatedAt = now, lastUpdatedBy = actor, sequenceNumber++
3. WS broadcast MUSIC_PLAYBACK_STATE_CHANGED
4. UI tất cả: resume playback
5. Nếu race condition: STOMP ERROR LIVEROOM_MUSIC_STATE_CONFLICT → client re-fetch state

Flow pause (v1.8 STOMP):
1. User click Pause → STOMP send /app/liveroom/{roomId}/music/pause
   - Body: {}
2. Backend set PlaybackState.status = PAUSED, lastUpdatedAt = now, lastUpdatedBy = actor, sequenceNumber++
3. WS broadcast MUSIC_PLAYBACK_STATE_CHANGED
4. UI tất cả: pause playback, lưu positionSeconds

Flow seek (v1.8 STOMP):
1. User kéo progress bar → STOMP send /app/liveroom/{roomId}/music/seek
   - Body: { positionSeconds: 120 }
2. Backend set PlaybackState.positionSeconds = 120, lastUpdatedAt = now, lastUpdatedBy = actor, sequenceNumber++
3. WS broadcast MUSIC_PLAYBACK_STATE_CHANGED
4. UI tất cả: seek audio đến 2:00

Flow volume GLOBAL (v1.8 STOMP):
1. User kéo volume slider → STOMP send /app/liveroom/{roomId}/music/volume
   - Body: { volumePercent: 80 }
2. Backend set PlaybackState.volumePercent = 80, lastUpdatedAt = now, lastUpdatedBy = actor, sequenceNumber++
3. WS broadcast MUSIC_PLAYBACK_STATE_CHANGED
4. UI tất cả: set volume 80% (cả phòng đồng bộ)

Edge cases:
- Owner leave → auto PAUSE (R-MUSIC-06, không cần user action)
- Grace period hết → phòng ENDED → nhạc stop
- Không có bài đang phát → STOMP ERROR LIVEROOM_MUSIC_NOT_PLAYING
- Position > duration → STOMP ERROR LIVEROOM_MUSIC_INVALID_POSITION
- Volume < 0 hoặc > 100 → STOMP ERROR LIVEROOM_MUSIC_INVALID_VOLUME
- Owner absent + non-owner action → STOMP ERROR LIVEROOM_MUSIC_OWNER_ABSENT (R-MUSIC-11 v1.8)
```

---

### 7.16. UX Guidelines cho Music (v1.8)

**UX-11 (Music mobile progress bar - v1.8)**: Optimize progress bar cho mobile:

- **Touch target**: Track của progress bar có height >= 44px (Apple HIG) để dễ tap
  - Trên desktop: 8px (visual), vùng tap 44px
  - Trên mobile: visual 12px, vùng tap 44px
- **Snap to 5s**: Khi user tap vào progress bar (không phải drag) → position snap về multiple của 5s gần nhất
  - Ví dụ: tap tại 2:37 → snap về 2:35 (hoặc 2:40, tùy gần hơn)
  - Lý do: Tránh seek tới 2:37.234 (không có ý nghĩa), 5s là đơn vị dễ nhớ
- **Drag behavior**: Drag thumb chính xác (không snap), hiển thị bubble preview với timestamp + song title
- **Tap** vs **Drag**: Phân biệt qua touch duration
  - Tap: < 200ms và không move → snap
  - Drag: >= 200ms hoặc move > 5px → no snap
- **Visual feedback**: Highlight vùng tap tạm thời (200ms) khi tap
- **Lý do**: Mobile user khó tap chính xác, snap-to-5s cải thiện UX

---

## 8. Callback nghiệp vụ (i18n Messages)

Các message nghiệp vụ cần đa ngôn ngữ (EN + VI):

### 8.1. Success messages

| Key                          | EN                        | VI                             |
| ---------------------------- | ------------------------- | ------------------------------ |
| `LIVEROOM_ROOM_CREATED`      | Room created successfully | Phòng đã được tạo              |
| `LIVEROOM_ROOM_JOINED`       | You have joined the room  | Bạn đã tham gia phòng          |
| `LIVEROOM_ROOM_LEFT`         | You have left the room    | Bạn đã rời phòng               |
| `LIVEROOM_ROOM_ENDED`        | Room has been ended       | Phòng đã kết thúc              |
| `LIVEROOM_ROOM_REOPENED`     | Room has been reopened    | Phòng đã được mở lại           |
| `LIVEROOM_REQUEST_APPROVED`  | Join request approved     | Yêu cầu tham gia đã được duyệt |
| `LIVEROOM_REQUEST_REJECTED`  | Join request rejected     | Yêu cầu tham gia đã bị từ chối |
| `LIVEROOM_REQUEST_CANCELLED` | Join request cancelled    | Yêu cầu tham gia đã được hủy   |

### 8.2. Warning messages

| Key                                           | EN                                                         | VI                                                       |
| --------------------------------------------- | ---------------------------------------------------------- | -------------------------------------------------------- |
| `LIVEROOM_ROOM_FULL` (v1.6 update)            | Room is full. Please try later                             | Phòng đã đầy, vui lòng thử lại sau                       |
| `LIVEROOM_REJECTED_BY_OWNER` (v1.7)           | Join request rejected by owner                             | Yêu cầu tham gia đã bị chủ phòng từ chối                 |
| `LIVEROOM_ROOM_AUTO_ENDED_OWNER_GRACE` (v1.7) | Room ended - owner did not return                          | Phòng đã kết thúc - chủ phòng không quay lại             |
| `LIVEROOM_OWNER_GRACE_EXPIRING`               | Owner has {0} seconds to rejoin                            | Chủ phòng còn {0} giây để quay lại                       |
| `LIVEROOM_AUTO_ENDED_GRACE`                   | Room ended automatically (owner left)                      | Phòng tự kết thúc (chủ phòng đã rời)                     |
| `LIVEROOM_AUTO_ENDED_EMPTY`                   | Room ended (no participants)                               | Phòng tự kết thúc (không có người)                       |
| `LIVEROOM_MEDIA_PERMISSION_DENIED`            | Camera/microphone permission denied. You can still listen. | Không có quyền truy cập camera/mic. Bạn vẫn có thể nghe. |
| `LIVEROOM_MEDIA_NETWORK_UNSTABLE`             | Network unstable. Consider turning off video.              | Mạng không ổn định. Nên tắt video.                       |
| `LIVEROOM_MEDIA_CONNECTING`                   | Connecting to media stream...                              | Đang kết nối media...                                    |

### 8.3. Error messages

| Key                                           | EN                                                                     | VI                                                                    |
| --------------------------------------------- | ---------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `LIVEROOM_ROOM_NOT_FOUND`                     | Room not found                                                         | Không tìm thấy phòng                                                  |
| `LIVEROOM_ROOM_ENDED`                         | Room has ended                                                         | Phòng đã kết thúc                                                     |
| `LIVEROOM_ALREADY_IN_ROOM`                    | You are already in this room                                           | Bạn đã ở trong phòng này                                              |
| `LIVEROOM_NOT_OWNER`                          | Only the owner can perform this action                                 | Chỉ chủ phòng mới có quyền này                                        |
| `LIVEROOM_REQUEST_NOT_FOUND`                  | Join request not found                                                 | Không tìm thấy yêu cầu tham gia                                       |
| `LIVEROOM_REQUEST_EXPIRED`                    | Join request has expired                                               | Yêu cầu tham gia đã hết hạn                                           |
| `LIVEROOM_CANNOT_REOPEN`                      | Only ended rooms can be reopened                                       | Chỉ phòng đã kết thúc mới có thể mở lại                               |
| `LIVEROOM_PRO_REQUIRED`                       | Only PRO accounts can create rooms                                     | Chỉ tài khoản PRO mới có quyền tạo phòng                              |
| `LIVEROOM_REQUEST_LOCKED`                     | You have been rejected 3 times. Cannot send more requests to this room | Bạn đã bị từ chối 3 lần. Không thể gửi yêu cầu cho phòng này          |
| `LIVEROOM_ROOM_FORCE_ENDED_ROLE_CHANGE`       | Room ended due to owner role change                                    | Phòng đã kết thúc do thay đổi quyền của chủ phòng                     |
| `LIVEROOM_CHAT_EMPTY` (v1.5)                  | Chat message content is empty                                          | Tin nhắn không được để trống                                          |
| `LIVEROOM_CHAT_TOO_LONG` (v1.5)               | Chat message exceeds maximum length                                    | Tin nhắn vượt quá 500 ký tự                                           |
| `LIVEROOM_NOT_IN_SESSION` (v1.5)              | User is not in active session, cannot send chat                        | Bạn không trong phòng, không thể gửi tin nhắn                         |
| `LIVEROOM_MUSIC_NOT_OWN_SONG` (v1.6)          | User tried to select a song not owned by them                          | Bạn chỉ có thể chọn bài hát của chính mình                            |
| `LIVEROOM_MUSIC_NOT_READY` (v1.6)             | Song status is not PROCESSED                                           | Bài hát chưa sẵn sàng để phát                                         |
| `LIVEROOM_MUSIC_NOT_PLAYING` (v1.6)           | No song is currently playing/paused in the room                        | Không có bài hát nào đang phát                                        |
| `LIVEROOM_MUSIC_INVALID_POSITION` (v1.6)      | Position out of range                                                  | Vị trí phát không hợp lệ                                              |
| `LIVEROOM_MUSIC_INVALID_VOLUME` (v1.6)        | Volume must be 0-100                                                   | Âm lượng phải từ 0-100                                                |
| `LIVEROOM_MUSIC_OWNER_PAUSED` (v1.6)          | Owner left, music auto-paused                                          | Chủ phòng đã rời, nhạc tạm dừng                                       |
| `LIVEROOM_MUSIC_SONG_LOAD_FAILED` (v1.6)      | Failed to load audio file from Voice module                            | Không tải được file nhạc                                              |

| `LIVEROOM_KICKED_COOLDOWN` (v1.8 NEW)         | You were kicked from this room. Please try again in {0} minutes        | Bạn đã bị đuổi khỏi phòng này. Vui lòng thử lại sau {0} phút          |
| `LIVEROOM_SELF_KICK_NOT_ALLOWED` (v1.8 NEW)   | You cannot kick yourself                                               | Bạn không thể tự đuổi chính mình                                      |
| `LIVEROOM_MIC_MUTED_BY_OWNER` (v1.8 NEW)      | Owner has muted your microphone                                        | Chủ phòng đã tắt mic của bạn                                          |
| `LIVEROOM_MIC_MUTE_COOLDOWN` (v1.8 NEW)       | You cannot unmute for {0} seconds                                      | Bạn không thể bật mic trong {0} giây                                  |
| `LIVEROOM_MUSIC_STATE_CONFLICT` (v1.8 NEW)    | Music was controlled by another user. Please try again                 | Nhạc đã được điều khiển bởi người khác. Vui lòng thử lại              |
| `LIVEROOM_MUSIC_OWNER_ABSENT` (v1.8 NEW)      | Owner has left. Music is paused. Please wait for owner to return       | Chủ phòng đã rời. Nhạc đang tạm dừng. Vui lòng chờ chủ phòng quay lại |
| `LIVEROOM_DUPLICATE_REQUEST` (v1.8 NEW)       | You already have a pending request                                     | Bạn đã có yêu cầu đang chờ                                            |
| `LIVEROOM_MULTI_TAB_CONFLICT` (v1.8 NEW)      | You are in this room in another tab                                    | Bạn đang ở phòng này ở tab khác                                       |
| `LIVEROOM_GRACE_INVALID` (v1.8 NEW)           | Grace period must be between 30 and 1800 seconds                       | Grace period phải từ 30 đến 1800 giây                                 |
| `LIVEROOM_ROOM_REVIVED` (v1.8 NEW)            | Room has been revived by owner                                         | Phòng đã được hồi sinh bởi chủ phòng                                  |

---

## 9. Giới hạn nghiệp vụ (Business Constraints)

| Thuộc tính                                  | Giá trị                                 | Lý do                                                          |
| ------------------------------------------- | --------------------------------------- | -------------------------------------------------------------- |
| **Capacity min**                            | 1                                       | Phòng mới tạo có thể chỉ có owner                              |
| **Capacity max**                            | 7                                       | Tối ưu cho voice + video meeting nhóm nhỏ                      |
| **Capacity default**                        | 7                                       | Match max, cho phép họp nhóm lớn nhất                          |
| **Owner grace period**                      | 60s (default), 30s-1800s (configurable) | Đủ để owner thoát/đăng nhập lại                                |
| **Empty room timeout**                      | 5 phút                                  | Tránh phòng "ma" (không ai nhưng vẫn ACTIVE)                   |
| **Reopen cooldown**                         | Không giới hạn                          | Owner có thể reopen bao nhiêu lần cũng được                    |
| **JoinRequest expiry**                      | Khi phòng ENDED                         | Auto-expire khi phiên kết thúc                                 |
| **Rejected limit (per room)**               | 3 lần                                   | Chống spam, **reset = 0 khi phòng reopen** (xem R-REJECT-04.1) |
| **Room code length** (v1.8)                 | 6 ký tự A-Z0-9                          | 36^6 = 2.1 tỷ combinations, đủ chống brute-force               |
| **Room name unique**                        | Per-owner (case-insensitive, trim)      | 2 owner khác nhau có thể cùng tên phòng                        |
| **Create room role**                        | PRO only                                | Chỉ PRO mới có quyền tạo và quản lý phòng                      |
| **Max rooms per PRO**                       | Không giới hạn                          | PRO có thể tạo nhiều persistent rooms                          |
| **Media mặc định khi join**                 | camera=off, mic=off                     | Tôn trọng privacy, user tự bật khi sẵn sàng                    |
| **Max video streams**                       | 7 (mesh)                                | 7 người bật camera đồng thời (giới hạn cho mạng nội bộ)        |
| **Media state update latency**              | < 1 giây                                | Realtime, các client khác thấy ngay khi user bật/tắt           |
| **Chat message max length** (v1.5)          | 500 ký tự                               | Đủ cho text ngắn, tránh spam                                   |
| **Chat history max messages** (v1.5)        | 200 messages/room                       | Rolling window, tiết kiệm storage                              |
| **Chat persistence** (v1.8)                 | Lưu DB vĩnh viễn, cleanup 90 ngày      | R-CHAT-06 v1.8 — audit trail dài hạn                          |
| **Chat scope** (v1.5)                       | Group chat (1 channel)                  | Không có private DM trong MVP                                  |
| **Music: track count** (v1.6)               | 1 bài / phòng / thời điểm               | Single track, không queue                                      |
| **Music: control latency** (v1.6)           | ±500ms                                  | Chấp nhận được, giống Spotify Jam                              |
| **Music: volume range** (v1.6)              | 0-100%                                  | Global sync (R-MUSIC-03)                                       |
| **Music: source** (v1.6)                    | Module Voice (Song entity)              | User upload từ trước, reuse API                                |

| **Room code brute-force rate limit** (v1.8) | 5 attempts/IP/giờ                       | Chống BOT attack                                               |
| **KICKED cooldown** (v1.8)                  | 5 phút (300s)                           | User bị kick phải chờ trước khi rejoin                         |
| **REMOTE_MUTE cooldown** (v1.8)             | 30s                                     | User bị mute không tự bật mic ngay                             |
| **Idle ghost threshold** (v1.8)             | 10 phút (indicator), 30 phút (review)   | Hiển thị icon zZz khi user không tương tác                     |
| **End room undo window** (v1.8)             | 5 giây                                  | Owner có thể hoàn tác nếu click End nhầm                       |
| **Auto-join countdown** (v1.8)              | 3 giây + cancel                         | User chuẩn bị trước khi vào phòng                              |
| **Idempotency key TTL** (v1.8)              | 24 giờ                                  | Tránh duplicate JoinRequest                                    |

### 9.1. Ma trận quyền (Permission Matrix)

Ma trận xác định quyền của từng vai trò trong từng phòng. **Quyền theo vai trò trong phòng** (owner/participant), không phải theo role hệ thống (PRO/USER).

| Hành động                                    | PRO (Owner) | PRO (Participant)          | USER (Participant)         | Trạng thái yêu cầu       |
| -------------------------------------------- | ----------- | -------------------------- | -------------------------- | ------------------------ |
| Tạo phòng mới                                | ✅          | ❌                         | ❌                         | —                        |
| Vào phòng của mình                           | ✅ (owner)  | —                          | —                          | ACTIVE                   |
| Join phòng khác                              | —           | ✅                         | ✅                         | ACTIVE                   |
| Join phòng của chính mình                    | ❌          | —                          | —                          | (R-ROLE-11)              |
| Rejoin (sau khi rời)                         | ✅ (owner)  | ✅                         | ✅                         | ACTIVE                   |
| Rời phòng (leave)                            | ✅          | ✅                         | ✅                         | ACTIVE                   |
| Approve JoinRequest                          | ✅          | ❌                         | ❌                         | PENDING                  |
| Reject JoinRequest                           | ✅          | ❌                         | ❌                         | PENDING                  |
| Kick participant                             | ✅          | ❌                         | ❌                         | ACTIVE                   |
| Remote mute mic participant                  | ✅          | ❌                         | ❌                         | ACTIVE (v1.8 NEW)        |
| End phòng                                    | ✅          | ❌                         | ❌                         | ACTIVE                   |
| Reopen phòng                                 | ✅          | ❌                         | ❌                         | ENDED                    |
| Bật/tắt camera                               | ✅          | ✅                         | ✅                         | ACTIVE                   |
| Bật/tắt mic                                  | ✅          | ✅                         | ✅                         | ACTIVE                   |
| Multi-session (nhiều phòng đồng thời)        | ✅          | ✅                         | ✅                         | —                        |
| Gửi chat message                             | ✅          | ✅                         | ✅                         | ACTIVE                   |
| Đọc chat history                             | ✅          | ✅                         | ✅                         | ACTIVE                   |
| Chọn bài hát (của chính mình) vào phòng      | ✅          | ✅                         | ✅                         | ACTIVE (v1.6)            |
| Điều khiển playback (play/pause/seek/volume) | ✅          | ✅                         | ✅                         | ACTIVE (v1.6)            |


**Giải thích chi tiết**:

- **"PRO (Owner)"**: PRO là owner của phòng này → có đầy đủ quyền owner
- **"PRO (Participant)"**: PRO tham gia phòng của owner khác (R-ROLE-09) → chỉ có quyền participant
- **"USER (Participant)"**: USER (không phải PRO) tham gia phòng của owner nào đó → chỉ có quyền participant
- **"Multi-session"**: 1 user có thể đồng thời là owner phòng A và participant phòng B (R-ROLE-10)

**Lưu ý quan trọng**:

- PRO không "tự động" có quyền owner ở mọi phòng — quyền owner chỉ có ở phòng do mình tạo
- PRO ở phòng của owner khác = Participant thông thường (không có đặc quyền)
- USER ở phòng PRO = Participant (đầy đủ quyền tham gia, không có hạn chế)

### 9.2. Khuyến nghị kỹ thuật (không bắt buộc nghiệp vụ)

> **Lưu ý**: Đây là khuyến nghị, không phải quy tắc nghiệp vụ. Team kỹ thuật có thể điều chỉnh.

- **Video resolution**: 360p - 480p (đủ cho 7 người cùng xem)
- **Audio bitrate**: 32 kbps (Opus codec)
- **Fallback khi mất video**: tự động chuyển sang audio-only
- **Reconnect**: client tự reconnect khi mất kết nối media, không cần rejoin room

---

## 10. Tích hợp với hệ thống khác

### 10.1. IAM (Identity & Access Management)

- Live Room phụ thuộc vào IAM để xác thực user
- User phải đăng nhập mới có thể tạo/tham gia phòng
- Email lấy từ IAM làm display name
- **Quan trọng**: IAM không cho phép đổi email (invariant)

### 10.2. Realtime Gateway (WebSocket)

- Live Room sử dụng Realtime Gateway để:
  - Gửi thông báo realtime (có người vào/rời)
  - Broadcast sự kiện phòng (ENDED, REOPENED)
  - Sync trạng thái participants

**R-WS-SCOPE-01 (Subscription scope rule - v1.8 NEW)**:

- **Ai được subscribe topic của phòng**: chỉ user có `JoinRequest.state ∈ {PENDING, APPROVED}` (JoinRequest còn active) HOẶC `participant.state ∈ {ACTIVE, RECONNECTING, OWNER_GRACE}` (đang trong phòng)
- **Các trạng thái KHÔNG được subscribe**:
  - `JoinRequest.state ∈ {REJECTED_BY_OWNER, REJECTED_BY_CAPACITY, CANCELLED, EXPIRED, LOCKED}` → unsubscribe ngay
  - `participant.state ∈ {KICKED, LEFT, ENDED, OFFLINE}` → unsubscribe ngay
  - **Lý do**: User không còn quyền quan tâm phòng → giảm traffic WS, tránh leak state
- **Enforcement**:
  - Khi user gửi SUBSCRIBE request → Realtime Gateway check JoinRequest state + participant state trước khi accept
  - Nếu không hợp lệ → reject subscription với error code `WS_UNAUTHORIZED`
  - Khi state thay đổi (REJECTED → user bị reject) → backend **CHỦ ĐỘNG unsubscribe** + force disconnect session WS
- **ROUTE_CAPACITY_CHANGED**: chỉ user có JoinRequest còn active (PENDING/APPROVED) HOẶC ACTIVE trong phòng mới nhận
  - User REJECTED/EXPIRED/CANCELLED → KHÔNG nhận `ROOM_CAPACITY_CHANGED` (MVP tránh leak info)
  - Lý do: User không còn quan tâm phòng này → không cần biết phòng đầy/empty
- **Lobby state (POST-MVP)**: Cho phép user subscribe "lobby view" của phòng REOPEN (chỉ metadata: roomName, capacity, ...) - nhưng KHÔNG nhận realtime event participant state. Out of MVP
- **Reconnect logic**: Khi user reconnect WS → re-validate scope → nếu state vẫn valid → re-subscribe; nếu không → force disconnect

**R-WS-SCOPE-02 (Topic path convention - v1.8 NEW)**:

- Topic chính: `/topic/liveroom/{roomId}` (nhận generic event của phòng)
- Topic con cho music: `/topic/liveroom/{roomId}/music` (R-MUSIC-09)
- Topic con cho chat: `/topic/liveroom/{roomId}/chat` (R-CHAT-07)
- **Authentication**: Mỗi SUBSCRIBE request kèm JWT token (giống REST). Realtime Gateway validate + parse userId → check scope rule
- **Naming**: Dùng `{roomId}` UUID, KHÔNG dùng roomCode (vì roomCode có thể đổi khi update, UUID stable)

### 10.3. Notification (Optional)

- Có thể tích hợp notification service để:
  - Gửi email khi có JoinRequest mới (cho owner)
  - Gửi email khi được duyệt (cho user)
  - Gửi email khi phòng sắp auto-end (grace period)

### 10.4. WebSocket Events Catalog (v1.7 NEW)

Tổng hợp các WS event mà Realtime Gateway cần broadcast cho Live Room module:

| Event                                                           | Trigger                                   | Receiver                                 | Payload                                                                                    |
| --------------------------------------------------------------- | ----------------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------ |
| `PARTICIPANT_JOINED`                                            | User join thành công                      | Tất cả ACTIVE participant                | `{ roomId, userId, userEmail, joinedAt }`                                                  |
| `PARTICIPANT_LEFT` (v1.7 NEW)                                   | User leave chủ động (không phải kick)     | Tất cả ACTIVE participant                | `{ roomId, userId, leftAt }`                                                               |
| `PARTICIPANT_KICKED`                                            | Owner kick user                           | User bị kick + tất cả ACTIVE participant | `{ roomId, userId, kickedBy, reason }`                                                     |
| `OWNER_LEFT` (v1.7 NEW)                                         | Owner leave (bắt đầu grace)               | Tất cả ACTIVE participant + lobby user   | `{ roomId, ownerId, ownerLeftAt, graceExpiresAt }`                                         |
| `OWNER_REJOINED` (v1.7 NEW)                                     | Owner rejoin trong grace                  | Tất cả ACTIVE participant + lobby user   | `{ roomId, ownerId, rejoinedAt }`                                                          |
| `ROOM_CAPACITY_CHANGED` (v1.7 NEW: thêm reservedOwnerSlot)      | Participant join/leave/timeout            | Tất cả ACTIVE participant + lobby user   | `{ roomId, currentCount, maxParticipants, reservedOwnerSlot, timestamp }`                  |
| `CAPACITY_REACHED` (v1.7 NEW)                                   | currentCount >= maxParticipants           | Lobby user (PENDING)                     | `{ roomId, currentCount, maxParticipants }`                                                |
| `JOIN_REQUEST_CREATED`                                          | User gửi JoinRequest mới                  | Owner                                    | `{ roomId, requestId, userId, userEmail, createdAt }`                                      |
| `JOIN_REQUEST_CANCELLED`                                        | User tự huỷ JoinRequest                   | Owner                                    | `{ roomId, requestId, userId, cancelledAt }`                                               |
| `REQUEST_APPROVED`                                              | Owner approve user                        | User được approve                        | `{ roomId, requestId, autoJoin: true, roomState }`                                         |
| `JOIN_REQUEST_REJECTED` (v1.7 NEW: phân biệt owner vs capacity) | Owner reject hoặc phòng đầy               | User bị reject                           | `{ roomId, requestId, reason: "OWNER_REJECT" \| "ROOM_FULL", rejectCountByOwner }`         |
| `REQUEST_LOCKED`                                                | User đạt 3 lần reject của owner           | User bị lock                             | `{ roomId, rejectCountByOwner: 3 }`                                                        |
| `ROOM_MANUAL_ENDED`                                             | Owner end phòng thủ công                  | Tất cả (ACTIVE + lobby)                  | `{ roomId, endedAt, reason: "manual" }`                                                    |
| `ROOM_AUTO_ENDED` (v1.7 NEW)                                    | Phòng auto-end vì grace hoặc empty        | Tất cả (ACTIVE + lobby)                  | `{ roomId, endedAt, reason: "owner_grace_expired" \| "empty_timeout" }`                    |
| `ROOM_REOPENED`                                                 | Owner reopen phòng                        | Owner + participant cũ (was_approved)    | `{ roomId, reopenedCount, previousEndedAt, startedAt }`                                    |
| `MEDIA_STATE_CHANGED`                                           | User bật/tắt camera/mic                   | Tất cả ACTIVE participant                | `{ roomId, userId, cameraOn, micOn, timestamp }`                                           |
| `PARTICIPANT_CONNECTION_CHANGED`                                | WS connect/disconnect                     | Tất cả ACTIVE participant                | `{ roomId, userId, status: "RECONNECTING" \| "OFFLINE", timestamp }`                       |
| `CHAT_MESSAGE_RECEIVED` (v1.5)                                  | User gửi chat message                     | Tất cả ACTIVE participant                | `{ roomId, messageId, userId, userEmail, content, sentAt }`                                |
| `CHAT_HISTORY_LOADED` (v1.5)                                    | User load history (qua API, không qua WS) | Client gọi API                           | —                                                                                          |
| `MUSIC_SONG_CHANGED` (v1.6)                                     | User chọn bài mới                         | Tất cả ACTIVE participant                | `{ roomId, songId, songTitle, songArtist, songDurationSeconds, songOwnerId }`              |
| `MUSIC_PLAYBACK_STATE_CHANGED` (v1.6)                           | Play/pause/seek/volume                    | Tất cả ACTIVE participant                | `{ roomId, status, positionSeconds, volumePercent, lastUpdatedBy, lastUpdatedAt }`         |

| `REQUEST_REJECTED_BY_OWNER` (v1.8 NEW)                          | Owner reject user                         | User bị reject                           | `{ roomId, requestId, reason: "OWNER_REJECT", rejectCountByOwner }`                        |
| `REQUEST_REJECTED_BY_CAPACITY` (v1.8 NEW)                       | Phòng đầy khi approve                     | User bị reject                           | `{ roomId, requestId, reason: "CAPACITY_FULL", rejectCountByCapacity }`                    |
| `PARTICIPANT_MIC_MUTED_BY_OWNER` (v1.8 NEW)                     | Owner remote-mute user                    | User bị mute + tất cả ACTIVE participant | `{ roomId, userId, mutedBy, mutedAt, cooldownUntil }`                                      |
| `PARTICIPANT_MIC_UNMUTED` (v1.8 NEW)                            | User tự bật mic sau mute cooldown         | Tất cả ACTIVE participant                | `{ roomId, userId, unmutedAt }`                                                            |
| `ROOM_REVIVED` (v1.8 NEW)                                       | Owner undo end phòng (trong 5s)           | Tất cả (ACTIVE + lobby)                  | `{ roomId, revivedAt, revokedEndedAt }`                                                    |

**Lưu ý v1.8**:

- `REQUEST_REJECTED_BY_OWNER` vs `REQUEST_REJECTED_BY_CAPACITY` (split event cho dễ xử lý frontend, R-JOIN-09)
- `PARTICIPANT_MIC_MUTED_BY_OWNER` thông báo user bị mute + cooldown end timestamp để user biết khi nào có thể self-unmute
- `ROOM_REVIVED` giúp UI revert sang ACTIVE state khi owner undo end trong 5s (R-END-12)
- Music events giờ chỉ qua STOMP WS (R-MUSIC-09), không qua REST control

**Lưu ý v1.7**:

- `OWNER_LEFT` và `OWNER_REJOINED` giúp UI hiển thị banner "Owner đã rời — nhạc tạm dừng" / "Owner đã quay lại"
- `PARTICIPANT_LEFT` (vs `PARTICIPANT_KICKED`) phân biệt leave chủ động với bị đuổi → UI hiển thị khác nhau
- `CAPACITY_REACHED` thông báo cho lobby user biết phòng vừa đầy → frontend tắt nút "Xin vào" real-time
- `ROOM_AUTO_ENDED` (với reason) thay vì chỉ `ROOM_ENDED` để UI phân biệt lý do (grace vs empty vs manual)
- `JOIN_REQUEST_REJECTED` có `reason` (OWNER_REJECT hoặc ROOM_FULL) để frontend hiển thị message phù hợp

---

## 11. Metrics nghiệp vụ (KPIs)

Hệ thống nên track các metrics sau để đánh giá:

- **Tỷ lệ tạo phòng thành công**: (số phòng tạo thành công) / (số yêu cầu tạo)
- **Tỷ lệ JoinRequest được duyệt**: (số APPROVED) / (số PENDING)
- **Thời gian duyệt trung bình**: thời gian từ PENDING → APPROVED
- **Tỷ lệ rejoin**: (số rejoin) / (tổng số join)
- **Tỷ lệ auto-end**: (số auto-end) / (tổng số end)
- **Số lần reopen trung bình**: reopened_count trung bình trên persistent rooms
- **Capacity utilization**: current_count / max_participants trung bình

---

## 12. Phụ lục: Glossary mở rộng

| Thuật ngữ                        | Định nghĩa                                                                                                                                                                                                         |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Meeting**                      | Một cuộc họp giữa nhiều người (voice + video)                                                                                                                                                                      |
| **RoomSessionCycle** (v1.7)      | Một phiên hoạt động của phòng (từ ACTIVE → ENDED, hoặc ACTIVE → REOPEN). Đổi tên từ "Session" trong v1.6 để tránh nhầm với ParticipantSession. Lifecycle: tạo khi ACTIVE → kết thúc khi ENDED → tạo mới khi REOPEN |
| **ParticipantSession** (v1.7)    | Phiên tham gia của 1 user trong 1 phòng (từ join → leave). Nhiều ParticipantSession có thể tồn tại trong cùng 1 RoomSessionCycle                                                                                   |
| **Live Room**                    | Phòng họp trực tuyến (tên sản phẩm)                                                                                                                                                                                |
| **Persistent Room**              | Phòng có thể tái sử dụng qua nhiều session                                                                                                                                                                         |
| **Instant Meeting**              | Phòng họp không cần lên lịch trước                                                                                                                                                                                 |
| **Voice + Video**                | Họp vừa nói vừa thấy mặt nhau (giống Google Meet)                                                                                                                                                                  |
| **Room State**                   | Trạng thái hiện tại của phòng (ACTIVE/ENDED)                                                                                                                                                                       |
| **Participant State**            | Trạng thái tham gia của user (in room / left / approved / pending)                                                                                                                                                 |
| **Media State**                  | Trạng thái camera/mic của user (on/off)                                                                                                                                                                            |
| **camera_on**                    | User có đang bật camera hay không                                                                                                                                                                                  |
| **mic_on**                       | User có đang bật microphone hay không                                                                                                                                                                              |
| **Listen-only Mode**             | User chỉ nghe/xem, không nói, không hiện hình                                                                                                                                                                      |
| **WebRTC**                       | Công nghệ truyền media realtime giữa browser-browser                                                                                                                                                               |
| **rejectCountByOwner** (v1.7)    | Số lần user bị owner chủ động REJECTED trong cùng RoomSessionCycle. Đếm vào LOCKED limit (3 lần)                                                                                                                   |
| **rejectCountByCapacity** (v1.7) | Số lần user bị REJECTED do phòng đầy (R-APPROVE-05) trong cùng RoomSessionCycle. KHÔNG trigger LOCKED                                                                                                              |
| **reservedOwnerSlot** (v1.7)     | Flag = true khi owner leave (grace period). Khi true, max effective capacity = max - 1 (giữ slot cho owner rejoin)                                                                                                 |
| **Mesh Network**                 | Mỗi user kết nối trực tiếp với các user khác (giới hạn ~7 người)                                                                                                                                                   |

---

**Người viết**: Senior Dev Team  
**Reviewer**: PO, BA, QA  
**Ngày review**: Pending
