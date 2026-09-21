# F20–F28 · Phòng live

Module lớn nhất (234 file gốc). Bảng chính:

`rooms(id, owner_id, room_code UNIQUE, room_name, normalized_name, status, max_participants, owner_grace_seconds, current_participant_count, reserved_owner_slot, owner_left_at, current_cycle_id, reopened_count, ended_at, ended_reason)`
`session_cycles(id, room_id, started_at, ended_at, ended_reason)`
`participants(id, room_id, cycle_id, user_id, user_email, role, state, mic_state, camera_on, joined_at, left_at)`
`join_requests(id, room_id, cycle_id, user_id, user_email, state, idempotency_key, decided_by, decided_at)`
`room_members(room_id, user_id, was_approved, kicked_cooldown_until, reject_count_by_owner, reject_count_by_capacity)`
`chat_messages(id, room_id, cycle_id, user_id, user_email, content, sent_at)`
`playback_states(id, room_id, cycle_id, song_id, status, position_seconds, started_at, sequence_number, version)`

**Hai khái niệm phải nắm trước khi code dòng đầu tiên:**

**`cycle` (phiên)** — một phòng tồn tại lâu dài, nhưng mỗi lần mở là một *phiên* mới. Người tham gia, tin nhắn, trạng thái phát nhạc đều gắn vào `cycle_id`, không gắn vào `room_id`. Nhờ vậy đóng phòng rồi mở lại là một phiên sạch, mà lịch sử phòng vẫn còn.

**`room_members` khác `participants`** — `participants` là "ai đang ở trong phiên này", `room_members` là "hệ thống nhớ gì về người này với phòng này, qua mọi phiên": đã từng được duyệt chưa, đang bị cooldown sau khi bị kick không, đã bị từ chối mấy lần. Một bảng duy nhất vì cả ba đều được đọc bởi cùng một phép kiểm ở cửa.

---

## F20 · Tạo phòng — `POST /api/v1/liveroom/rooms`

### Luồng thật
1. Parse `roomName` → chuẩn hoá, kiểm trùng theo `(ownerId, normalizedName)`.
2. Sức chứa: mặc định 7, khoảng hợp lệ 1–7. `ownerGraceSeconds`: mặc định 60, khoảng 30–1800.
3. **Sinh mã phòng**: 6 ký tự `A-Z0-9`, thử tối đa N lần cho tới khi không đụng; hết lượt → `ROOM_CODE_GENERATION_FAILED`.
4. Lưu phòng, `status = ACTIVE`.
5. **Mở cycle đầu tiên** và gắn `currentCycleId` vào phòng.
6. **Tự nạp chủ phòng vào làm người tham gia** với role `OWNER`.
7. Ghi `room_ownership_history` kiểu `INITIAL_CREATE`.
8. Lưu phòng lần hai (vì bước 5 và 6 đã đổi `currentCycleId` và `currentParticipantCount`).

### Tự code
- **A.** Bước 1, 2, 4 — phòng trơn, chưa cycle, chưa participant.
- **B.** Bước 3 (`RoomCode` là value object: chuẩn hoá hoa, regex 6 ký tự, có `display()` trả `ABC-123`).
- **C.** Bước 5–8.

### Bẫy
Chủ phòng **phải** được nạp làm participant ngay lúc tạo. Nếu không, phòng vừa tạo có `currentParticipantCount = 0` và mọi phép đếm sức chứa về sau đều lệch 1 — lỗi này chỉ lộ ra khi phòng đầy.

---

## F21 · Tra phòng bằng mã — `GET /api/v1/liveroom/rooms/by-code/{roomCode}`

### Luồng thật
1. Chuẩn hoá mã (uppercase, bỏ dấu `-`).
2. **Throttle riêng theo IP** cho endpoint này.
3. Tra phòng `ACTIVE`; không có → 404.
4. Trả bản rút gọn: tên phòng, chủ phòng, số người / sức chứa. **Không** trả danh sách người tham gia.

### Bẫy
Throttle ở đây là một adapter Redis **riêng của module**, không phải một rule trong filter rate-limit chung. Lý do: filter chung gộp mọi endpoint vào một key theo IP (`pwb:ratelimit:global:{ip}`), nên đặt cửa sổ một giờ ở đó sẽ kéo dài cửa sổ cho toàn bộ API. Mã 6 ký tự = 2 tỉ tổ hợp, nhưng không throttle thì vẫn quét được.

---

## F22 · Xin vào phòng — `POST /api/v1/liveroom/rooms/{roomId}/join-requests`

### Luồng thật
1. Nạp phòng; không `ACTIVE` → `ROOM_ENDED`.
2. Người gọi là chủ phòng → `SELF_JOIN_NOT_ALLOWED`.
3. **Kiểm idempotency key**: đã có request cùng `(roomId, userId, key)` → trả lại chính nó, không tạo mới.
4. Nạp `room_members`, rồi kiểm theo đúng thứ tự:
   - đang cooldown sau khi bị kick → `KICKED_COOLDOWN` kèm số phút còn lại
   - đã bị khoá (từ chối đủ số lần) → `REQUEST_LOCKED`
   - đã từng được duyệt → `ALREADY_APPROVED` (vào thẳng, không xin nữa)
   - đã có request `PENDING` → `DUPLICATE_REQUEST`
5. Tạo request `PENDING`, broadcast `JOIN_REQUEST_CREATED` lên topic phòng (kèm `avatarUrl`).

### Bẫy
- **Idempotency key phải có.** Mạng chập chờn, client bấm lại, không có key thì chủ phòng thấy 3 dòng xin vào từ cùng một người.
- Bốn phép kiểm ở bước 4 phải đúng thứ tự: người vừa bị kick mà lại từng được duyệt, nếu kiểm `wasApproved` trước thì họ nhận `ALREADY_APPROVED` và vào thẳng, cooldown vô nghĩa.
- `avatarUrl` phải nằm trong event, vì client dựng danh sách người xin vào từ chính event đó chứ không gọi API riêng.

---

## F23 · Duyệt và từ chối — `POST .../{requestId}/approve` · `/reject`

### Luồng thật — duyệt
1. Nạp phòng **bằng `findByIdForUpdate`** (khoá bi quan).
2. Không phải chủ phòng → **`ROOM_NOT_FOUND`**, không phải 403.
3. Phòng đã kết thúc → `ROOM_ENDED`. Request không còn `PENDING` → `REQUEST_EXPIRED`.
4. **Phòng đã đầy trong lúc chờ** → đánh dấu request `REJECTED_BY_CAPACITY`, tăng bộ đếm, gửi riêng cho người xin, và **trả HTTP 200** kèm `state = REJECTED_BY_CAPACITY`.
5. Còn chỗ → `admissions.admit(...)` → `request.approve(...)` → gửi riêng cho người xin → broadcast `PARTICIPANT_JOINED` + `CAPACITY_CHANGED` → gửi trạng thái nhạc hiện tại cho người vừa vào.

### Luồng thật — từ chối
1. Kiểm quyền như trên.
2. `rejectByOwner`, tăng `reject_count_by_owner`.
3. Gửi riêng cho người bị từ chối, kèm **số lần còn lại**.
4. Chạm ngưỡng → gửi thêm event `REQUEST_LOCKED`.

### Bẫy
- **Bước 4 trả 200 chứ không ném lỗi.** Ném exception sẽ rollback đúng cái thay đổi mà chủ phòng cần thấy: request đó đã được xử lý, đừng hiện lại trong hàng chờ. Đây là ví dụ rõ nhất trong dự án về "lỗi nghiệp vụ không phải lúc nào cũng là HTTP lỗi".
- **`findByIdForUpdate` chứ không phải `findById`.** Hai người xin cùng lúc vào chỗ cuối cùng: không khoá thì cả hai đều đọc `hasFreeSlot() == true` và cả hai đều vào, phòng vượt sức chứa. Đây là chỗ để hiểu khoá bi quan bằng một tình huống thật.
- **Không phải chủ phòng thì trả 404, không trả 403.** 403 xác nhận phòng đó tồn tại — dò `roomId` là biết phòng nào có thật.
- Quyết định duyệt/từ chối gửi **riêng cho người xin** (`/user/queue/...`), không broadcast lên topic phòng. Ai bị từ chối là chuyện giữa họ và chủ phòng.

---

## F24 · Vào và rời phòng — `POST /participants/me` · `DELETE /participants/me`

### Luồng thật — vào
1. `findByIdForUpdate`; phòng phải `ACTIVE`.
2. Nạp `room_members`. **Nếu không phải chủ phòng**: cooldown kick → chặn; chưa từng được duyệt → `APPROVAL_REQUIRED`.
3. **Nếu là chủ phòng và đang vắng mặt** → `markOwnerReturned()`, phát `OWNER_REJOINED`.
4. `admissions.admit(...)`:
   - đã có participant và đang trong phòng → `ALREADY_IN_ROOM`
   - không còn chỗ → `ROOM_FULL`
   - `room.admitParticipant()` (tăng biến đếm)
   - chưa có row → tạo mới; có rồi (đã rời trước đó) → `rejoin(now)`
   - đánh dấu `room_members.was_approved = true`
5. Broadcast `PARTICIPANT_JOINED`, **gửi trạng thái nhạc hiện tại cho riêng người vừa vào**, broadcast `CAPACITY_CHANGED`, và `CAPACITY_REACHED` nếu vừa đầy.

### Bẫy — bẫy lớn nhất của module
**Có hai cửa vào phòng.** Duyệt một request (F23) gọi thẳng `admissions.admit`, người được duyệt **không đi qua** `JoinRoomUseCase`. Bất cứ việc gì phải xảy ra "khi có người vào phòng" đều phải móc vào **cả hai chỗ**.

Dự án gốc đã vấp đúng chỗ này khi làm nghe nhạc chung: người được duyệt vào giữa bài là người duy nhất không nghe thấy gì. Lỗi chỉ lộ ra khi chạy client thật, không có test nào bắt được.

**Bẫy khác:**
- `ParticipantState` có 6 giá trị nhưng chỉ `ACTIVE` và `RECONNECTING` **chiếm slot** (`occupiesSlot()`). Đếm sức chứa theo "số row participant" là sai ngay.
- `currentParticipantCount` là **giá trị lưu sẵn**, không phải `COUNT(*)`. Phải tăng/giảm khớp tuyệt đối với vào/rời/kick/kết thúc, nếu không nó trôi dần và phòng báo đầy khi thật ra còn trống.

---

## F25 · Kick, tắt mic từ xa, đổi trạng thái media

`POST /participants/{targetUserId}/kick` · `POST /{targetUserId}/mute` · `PATCH /participants/me/media`

### Luồng thật — kick
1. Tự kick mình → `SELF_KICK_NOT_ALLOWED`.
2. `findByIdForUpdate` + kiểm chủ phòng (404 nếu không phải).
3. Tìm participant **đang trong phòng** của cycle hiện tại.
4. `participant.kick(now)` → `room.releaseSlot()`.
5. `room_members.recordKick(now, cooldownUntil)`.
6. Ghi `room_admin_actions` (ai kick ai, lý do, lúc nào).
7. Broadcast `PARTICIPANT_KICKED` + `CAPACITY_CHANGED`.
8. **Đóng WebSocket session của người bị kick.**

### Bẫy
- Bước 7 phải đi **trước** bước 8, và bước 8 phải **trễ vài giây**. Đóng socket ngay thì người bị kick mất kết nối trước khi nhận được event giải thích vì sao — họ chỉ thấy app đứng.
- **Đuổi một STOMP session phải đóng `WebSocketSession` thật.** Spring **vứt bỏ** frame `DISCONNECT` gửi từ server xuống client, nên mẹo "push một DISCONNECT" được nhắc khắp nơi trên mạng hoàn toàn không có tác dụng. Phải giữ registry các session sống và gọi `close()`.
- **Cooldown tắt mic của chủ phòng quyết định theo *hạn chót*, không theo `micState`.** Nếu khoá theo trạng thái, người bị tắt mic chỉ cần đổi sang tự-tắt-mic rồi bật lại là thoát cooldown ngay.

---

## F26 · Kết thúc, hoàn tác, mở lại

`POST /{roomId}/end` · `POST /{roomId}/undo-end` · `POST /{roomId}/reopen`

### Luồng thật — kết thúc
Một hàm `terminate(room, reason, at)` dùng chung cho cả kết thúc thủ công lẫn hai scheduler:
1. Đóng mọi participant đang trong phòng (`closeWithRoom`).
2. Cho hết hạn mọi join request `PENDING`.
3. **Đóng băng** trạng thái phát nhạc (pause, không xoá).
4. Xoá comment theo track của cycle.
5. `room.end(reason, at)` → `status = ENDED`.
6. Đóng cycle.

### Luồng thật — hoàn tác
Trong cửa sổ N giây sau khi kết thúc: hồi sinh **đúng cycle cũ**, khôi phục lại những người đang ở trong phòng tại thời điểm `endedAt`, và tính lại `currentParticipantCount` từ số đó.

### Luồng thật — mở lại
Chỉ từ phòng `ENDED`, và mở một **cycle hoàn toàn mới**: participant, chat, nhạc đều bắt đầu từ trống.

### Bẫy
- **Nhạc bị pause chứ không bị xoá khi phòng kết thúc** — ngược với yêu cầu ghi trong tài liệu nghiệp vụ. Lý do: hoàn tác phải trả lại cuộc họp *nguyên vẹn*; xoá row sẽ khiến nhạc là thứ duy nhất không quay về. Đường `reopen` mới là chỗ dọn nó.
- Hoàn tác khôi phục `currentParticipantCount` bằng **đếm lại số người thật**, không bằng cách nhớ số cũ. Số cũ có thể đã sai.
- Phân biệt rõ: **undo-end** = "tôi bấm nhầm, trả lại y như cũ". **reopen** = "dùng lại cái phòng này cho một buổi mới".

---

## F27 · Scheduler

Bốn job, tất cả `@Scheduled`:

| Job | Nhịp | Việc |
|---|---|---|
| `OwnerGraceScheduler` | 10s | chủ phòng vắng quá `ownerGraceSeconds` → `terminate(OWNER_GRACE_EXPIRED)` |
| `EmptyRoomScheduler` | 60s | phòng trống quá lâu → `terminate(EMPTY_TIMEOUT)` |
| `ChatRetentionScheduler` | 4h sáng | xoá tin nhắn quá hạn lưu trữ |
| `IdempotencyKeyCleanupScheduler` | 3h30 sáng | xoá key idempotency cũ |

### Bẫy
- Cả hai job đầu gọi đúng `terminate` mà F26 dùng. Viết đường kết thúc riêng cho scheduler là cách chắc chắn nhất để hai đường lệch nhau sau vài tháng.
- **Không bao giờ hoãn hai lần.** Nếu event publisher đã tự giữ event tới lúc commit, mà caller lại bọc thêm một `afterCommit` của riêng nó, thì callback thứ hai đăng ký từ *bên trong* callback thứ nhất — quá muộn để chạy, và event biến mất **không một lỗi nào**. Spring vẫn báo transaction đang active bên trong `afterCommit` nên không phép kiểm runtime nào bắt được. Dự án gốc mất một giờ vì đúng chỗ này.

---

## F28 · Realtime STOMP

4 controller `@MessageMapping`: chat, music, rtc, track comments.

### Kiến trúc
- Endpoint `/ws`, đăng ký **hai lần**: một lần trần, một lần `withSockJS()`.
- `/ws/**` nằm trong danh sách public — trình duyệt không gắn được header `Authorization` vào handshake. **Token đi trong frame `CONNECT`**, một `ChannelInterceptor` đọc nó và dựng `Principal`.
- Broadcast: `/topic/liveroom/{roomId}` (chung) và `/topic/liveroom/{roomId}/chat`.
- Gửi riêng: `/user/queue/liveroom` (quyết định duyệt/từ chối), `/user/queue/liveroom/errors` (lỗi), `/user/queue/liveroom/rtc` (tín hiệu WebRTC).

### Luồng thật — nhạc
`position_seconds` được lưu như một **mốc neo**: nó là vị trí của bài tại thời điểm `started_at`. Vị trí thật = `position_seconds + (now - started_at)` khi đang phát. Mỗi event mang **toàn bộ** trạng thái; loại event chỉ nói cho client biết có cần nạp lại nguồn audio hay không.

### Bẫy
- **Đăng ký `/ws` hai lần là cố ý.** Gọi `withSockJS()` **thay thế** mapping WebSocket thuần của endpoint đó chứ không cộng thêm, nên phải đăng ký riêng mỗi loại một lần thì cả client thuần lẫn fallback của trình duyệt mới chạy.
- **Simple broker chuyển tiếp nguyên văn `SEND` của client tới `/topic/**`.** Không chặn thì bất kỳ ai đã đăng nhập cũng bơm được event giả vào topic của phòng bất kỳ — không qua use case, không kiểm tư cách thành viên, không ghi DB. Phải chặn client `SEND` tới các prefix broker; **mọi thứ client gửi đều phải đi qua `/app/**` và một `@MessageMapping`.**
- **Chặn `/topic/**` và `/queue/**` là chưa đủ.** `UserDestinationMessageHandler` cũng lắng nghe trên `clientInboundChannel`, và một `SEND` của client mang `SimpMessageType.MESSAGE` — đúng nhánh mà resolver lấy người nhận từ **chuỗi destination** chứ không từ principal của session. Thiếu `/user/` trong danh sách chặn thì bất kỳ client nào cũng gửi được event giả tới `/user/{người-khác}/queue/liveroom`.
- **Tín hiệu WebRTC phải đi qua user queue, không qua topic.** Interceptor kiểm phạm vi chỉ đọc `roomId` ở segment đầu, nên một topic dạng `/topic/liveroom/{roomId}/rtc/{targetUserId}` cho phép mọi thành viên trong phòng đăng ký vào kênh RTC của người khác và đọc SDP/ICE của họ. User queue được định tuyến theo tên principal nên **an toàn theo cấu tạo**, không phải an toàn nhờ một luật phải nhớ.
- **`@RestControllerAdvice` không thấy frame STOMP.** Nó gắn với servlet dispatch. Không có một `@ControllerAdvice` + `@MessageExceptionHandler` riêng thì frame bị từ chối sẽ **chết im lặng** ở server trong khi client ngồi chờ một broadcast không bao giờ tới.
- Chat phát **cả cho người gửi**. Bản echo đó mang `id` và `sentAt` thật, là thứ để client đối chiếu với tin nhắn nó đã hiển thị lạc quan trước đó.
- Độ dài chat đếm bằng **code point**, khớp cách PostgreSQL đếm `VARCHAR(500)`. `String.length()` đếm một emoji ngoài BMP thành 2 và sẽ từ chối tin nhắn mà cột chấp nhận được.
- Lỗi optimistic lock của playback nổi lên **lúc commit**, sau khi use case đã return — không bắt được bên trong use case, phải map ở tầng exception handler của STOMP.

---

## Xong F20–F28

Chạy thử bằng 2 trình duyệt: tạo phòng ở A, lấy mã, xin vào ở B, duyệt ở A, chat hai chiều, chọn bài và phát — B phải nghe đúng vị trí A đang nghe. Reload B giữa bài, nó phải nhận lại đúng vị trí.

Đó là lúc bạn đã hiểu toàn bộ dự án.
