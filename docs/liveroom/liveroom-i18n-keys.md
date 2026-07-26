# Live Room — i18n Keys Reference

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-api-spec.md` v1.0, `liveroom-ws-protocol.md` v1.0
**Đối tượng đọc**: Backend Dev, Frontend Dev
**Mục đích**: Mapping đầy đủ i18n keys cho module Live Room (3 locale: default, en, vi). Spec cho implementation + test.

---

## 1. Quy ước

### 1.1. Naming Convention

| Pattern | Scope | Ví dụ |
|---|---|---|
| `LIVEROOM_<UPPER_SNAKE>` | Module-specific (API response, toast) | `LIVEROOM_ROOM_CREATED` |
| `validation.<field>.<rule>` | Validation error (Spring Bean Validation) | `validation.room.name.length` |
| `liveroom.<context>.<key>` | UI label | `liveroom.chat.placeholder` |

### 1.2. Locale Files

3 file BẮT BUỘC update cho mỗi key mới:

```
Backend/shared-web/src/main/resources/messages/
├── messages.properties       (fallback = EN)
├── messages_en.properties
└── messages_vi.properties
```

Frontend tương ứng:

```
frontend/messages/
├── en.json
└── vi.json
```

---

## 2. Success Messages

| Key | EN | VI | Trigger |
|---|---|---|---|
| `LIVEROOM_ROOM_CREATED` | Room created | Phòng đã được tạo | POST /liverooms 201 |
| `LIVEROOM_ROOM_ENDED` | Room ended | Phòng đã kết thúc | POST /liverooms/{id}/end 200 |
| `LIVEROOM_ROOM_REVIVED` | Room revived | Phòng đã được khôi phục | POST /liverooms/{id}/undo-end 200 |
| `LIVEROOM_ROOM_REOPENED` | Room reopened | Phòng đã được mở lại | POST /liverooms/{id}/reopen 200 |
| `LIVEROOM_ROOM_LEFT` | You have left the room | Bạn đã rời phòng | POST /liverooms/{id}/leave 200 |
| `LIVEROOM_ROOM_FOUND` | Room found | Tìm thấy phòng | GET /liverooms/by-code/{code} 200 |
| `LIVEROOM_REQUEST_CREATED` | Join request sent | Yêu cầu tham gia đã được gửi | POST /join-requests 201 |
| `LIVEROOM_REQUEST_APPROVED` | Your request has been approved | Yêu cầu của bạn đã được duyệt | POST /approve 200 |
| `LIVEROOM_REQUEST_REJECTED_BY_OWNER` | Request rejected by owner | Yêu cầu bị owner từ chối | POST /reject 200 |
| `LIVEROOM_REQUEST_CANCELLED` | Request cancelled | Yêu cầu đã được hủy | POST /cancel 200 |
| `LIVEROOM_CHAT_SENT` | Message sent | Tin nhắn đã được gửi | POST /chat/messages 201 |
| `LIVEROOM_PARTICIPANT_KICKED` | Participant has been kicked | Người tham gia đã bị kick | POST /participants/{id}/kick 200 |
| `LIVEROOM_MIC_MUTED_BY_OWNER` | Microphone muted by owner | Mic đã bị owner tắt | POST /participants/{id}/mute-mic 200 |

---

## 3. Warning Messages

| Key | EN | VI | Trigger |
|---|---|---|---|
| `LIVEROOM_AUTO_ENDED_GRACE` | Room ended because owner was absent | Phòng kết thúc vì owner vắng mặt | ROOM_AUTO_ENDED (grace expired) |
| `LIVEROOM_AUTO_ENDED_EMPTY` | Room ended because no participants | Phòng kết thúc vì không có người tham gia | ROOM_AUTO_ENDED (empty timeout) |
| `LIVEROOM_OWNER_LEFT` | Owner has left the room | Owner đã rời phòng | OWNER_LEFT WS event |
| `LIVEROOM_OWNER_GRACE_EXPIRING` | Owner grace period expiring soon | Thời gian grace của owner sắp hết | OWNER_GRACE_EXPIRING WS event |
| `LIVEROOM_REJECTED_BY_OWNER` | Your request was rejected | Yêu cầu của bạn bị từ chối | REQUEST_REJECTED_BY_OWNER WS |
| `LIVEROOM_ROOM_FULL` | Room is full | Phòng đã đầy | REQUEST_REJECTED_BY_CAPACITY WS |
| `LIVEROOM_KICKED_BY_OWNER` | You have been kicked by the owner | Bạn đã bị owner kick | PARTICIPANT_KICKED (for kicked user) |
| `LIVEROOM_PARTICIPANT_IDLE` | Participant is idle | Người tham gia đang không hoạt động | PARTICIPANT_IDLE WS |
| `LIVEROOM_REQUEST_LOCKED` | Too many rejections. Cannot retry | Quá nhiều lần bị từ chối. Không thể thử lại | REQUEST_LOCKED WS |
| `LIVEROOM_CONNECTION_LOST` | Connection lost. Reconnecting... | Mất kết nối. Đang kết nối lại... | WS disconnect |
| `LIVEROOM_KICKED_COOLDOWN` | Please wait {seconds}s before rejoining | Vui lòng đợi {seconds}s trước khi tham gia lại | 429 KICKED_COOLDOWN |

---

## 4. Error Messages

### 4.1. Validation Errors

| Key | EN | VI | Trigger |
|---|---|---|---|
| `validation.room.name.length` | Room name must be 1-100 characters | Tên phòng phải từ 1-100 ký tự | Bean Validation `@Size` |
| `validation.room.name.blank` | Room name cannot be empty | Tên phòng không được để trống | `@NotBlank` |
| `validation.room.code.format` | Room code must be 6 characters A-Z0-9 | Mã phòng phải có 6 ký tự A-Z0-9 | `@Pattern` |
| `validation.room.capacity.range` | Max participants must be 1-7 | Số người tối đa phải từ 1-7 | `@Min @Max` |
| `validation.chat.empty` | Message cannot be empty | Tin nhắn không được để trống | `@NotBlank` |
| `validation.chat.length` | Message must be 1-500 characters | Tin nhắn phải từ 1-500 ký tự | `@Size` |
| `validation.annotation.empty` | Annotation cannot be empty | Annotation không được để trống | `@NotBlank` |
| `validation.annotation.length` | Annotation must be 1-200 characters | Annotation phải từ 1-200 ký tự | `@Size` |
| `validation.annotation.position` | Annotation position must be within song duration | Vị trí annotation phải nằm trong bài hát | custom check |

### 4.2. Business Logic Errors

| Key | EN | VI | HTTP Code |
|---|---|---|---|
| `LIVEROOM_PRO_REQUIRED` | PRO role required to create room | Cần quyền PRO để tạo phòng | 403 |
| `LIVEROOM_NOT_OWNER` | Only the room owner can perform this action | Chỉ owner phòng mới có thể thực hiện | 403 |
| `LIVEROOM_NOT_IN_SESSION` | You are not in this room | Bạn không có trong phòng này | 403 |
| `LIVEROOM_ALREADY_PARTICIPANT` | You are already in this room | Bạn đã có trong phòng này | 409 |
| `LIVEROOM_MULTI_TAB_CONFLICT` | This room is open in another tab | Phòng này đang mở ở tab khác | 409 |
| `LIVEROOM_ROOM_NOT_FOUND` | Room not found | Không tìm thấy phòng | 404 |
| `LIVEROOM_ROOM_FULL` | Room is full | Phòng đã đầy | 409 |
| `LIVEROOM_ROOM_ENDED` | Room has ended | Phòng đã kết thúc | 409 |
| `LIVEROOM_ROOM_ALREADY_ENDED` | Room has already ended | Phòng đã kết thúc trước đó | 409 |
| `LIVEROOM_ROOM_NOT_ENDED` | Room has not ended | Phòng chưa kết thúc | 409 |
| `LIVEROOM_ROOM_NAME_DUPLICATE` | You already have a room with this name | Bạn đã có phòng với tên này | 409 |
| `LIVEROOM_UNDO_WINDOW_EXPIRED` | Cannot undo. The 5-second window has passed | Không thể hoàn tác. Đã quá 5 giây | 409 |
| `LIVEROOM_REQUEST_NOT_FOUND` | Join request not found | Không tìm thấy yêu cầu tham gia | 404 |
| `LIVEROOM_REQUEST_NOT_PENDING` | Request has already been resolved | Yêu cầu đã được xử lý | 409 |
| `LIVEROOM_DUPLICATE_REQUEST` | You already have a pending request | Bạn đã có yêu cầu đang chờ | 409 |
| `LIVEROOM_REQUEST_LOCKED` | Your requests have been rejected too many times | Yêu cầu của bạn đã bị từ chối quá nhiều lần | 403 |
| `LIVEROOM_PARTICIPANT_NOT_FOUND` | Participant not found | Không tìm thấy người tham gia | 404 |
| `LIVEROOM_SELF_KICK_NOT_ALLOWED` | You cannot kick yourself | Bạn không thể tự kick mình | 400 |
| `LIVEROOM_ANNOTATION_NOT_APPROVED` | Only approved participants can create annotations | Chỉ người đã được duyệt mới có thể tạo annotation | 403 |
| `LIVEROOM_IDEMPOTENCY_KEY_REQUIRED` | Idempotency key is required | Idempotency key là bắt buộc | 400 |
| `LIVEROOM_GRACE_INVALID` | Grace period must be 30-1800 seconds | Thời gian grace phải từ 30-1800 giây | 400 |
| `LIVEROOM_CAPACITY_INVALID` | Capacity must be 1-7 | Sức chứa phải từ 1-7 | 400 |
| `LIVEROOM_BRUTE_FORCE_LIMIT` | Too many attempts. Please try again later | Quá nhiều lần thử. Vui lòng thử lại sau | 429 |
| `LIVEROOM_KICKED_COOLDOWN` | Please wait before rejoining | Vui lòng đợi trước khi tham gia lại | 429 |
| `LIVEROOM_MIC_MUTE_COOLDOWN` | Microphone is muted. Please wait {seconds}s | Mic đang bị tắt. Vui lòng đợi {seconds}s | 403 |
| `LIVEROOM_MUSIC_NOT_OWN_SONG` | You can only play your own songs | Bạn chỉ có thể phát bài hát của bạn | 403 |
| `LIVEROOM_MUSIC_NOT_READY` | Song is not ready for playback | Bài hát chưa sẵn sàng phát | 409 |
| `LIVEROOM_MUSIC_NOT_PLAYING` | No song is playing | Không có bài hát đang phát | 400 |
| `LIVEROOM_MUSIC_OWNER_ABSENT` | Music controls are disabled when owner is absent | Điều khiển nhạc bị tắt khi owner vắng mặt | 403 |
| `LIVEROOM_MUSIC_STATE_CONFLICT` | Playback state conflict. Please retry | Xung đột trạng thái phát. Vui lòng thử lại | 409 |
| `LIVEROOM_MUSIC_INVALID_VOLUME` | Volume must be 0-100 | Âm lượng phải từ 0-100 | 400 |
| `LIVEROOM_ANNOTATION_INVALID_POSITION` | Annotation position must be within song duration | Vị trí annotation không hợp lệ | 400 |
| `LIVEROOM_CHAT_EMPTY` | Message cannot be empty | Tin nhắn không được để trống | 400 |
| `LIVEROOM_CHAT_TOO_LONG` | Message must be at most 500 characters | Tin nhắn không được vượt quá 500 ký tự | 400 |
| `LIVEROOM_CONCURRENT_OPERATION` | Another operation is in progress. Please retry | Một thao tác khác đang thực hiện. Vui lòng thử lại | 409 |
| `LIVEROOM_INTERNAL_ERROR` | An unexpected error occurred | Đã xảy ra lỗi không mong muốn | 500 |

---

## 5. UI Labels (Frontend)

### 5.1. Common

| Key | EN | VI |
|---|---|---|
| `liveroom.common.create` | Create room | Tạo phòng |
| `liveroom.common.join` | Join | Tham gia |
| `liveroom.common.leave` | Leave | Rời |
| `liveroom.common.end` | End room | Kết thúc phòng |
| `liveroom.common.undo` | Undo | Hoàn tác |
| `liveroom.common.cancel` | Cancel | Hủy |
| `liveroom.common.confirm` | Confirm | Xác nhận |
| `liveroom.common.close` | Close | Đóng |
| `liveroom.common.retry` | Retry | Thử lại |
| `liveroom.common.loading` | Loading... | Đang tải... |

### 5.2. Room Create Form (SC-01)

| Key | EN | VI |
|---|---|---|
| `liveroom.create.title` | Create a new room | Tạo phòng mới |
| `liveroom.create.name.label` | Room name | Tên phòng |
| `liveroom.create.name.placeholder` | e.g., Daily Sync | Ví dụ: Họp hàng ngày |
| `liveroom.create.name.help` | 1-100 characters | 1-100 ký tự |
| `liveroom.create.capacity.label` | Max participants | Số người tối đa |
| `liveroom.create.capacity.help` | 1-7 participants | 1-7 người |
| `liveroom.create.grace.label` | Owner grace period | Thời gian grace cho owner |
| `liveroom.create.grace.help` | 30s - 30min (default 60s) | 30 giây - 30 phút (mặc định 60 giây) |
| `liveroom.create.submit` | Create room | Tạo phòng |
| `liveroom.create.success.toast` | Room created successfully | Tạo phòng thành công |
| `liveroom.create.code.label` | Room code (share with others) | Mã phòng (chia sẻ cho người khác) |
| `liveroom.create.copy` | Copy code | Sao chép mã |
| `liveroom.create.copied` | Code copied | Đã sao chép mã |

### 5.3. Pre-Join Screen (SC-03)

| Key | EN | VI |
|---|---|---|
| `liveroom.prejoin.title` | Join Room | Tham gia phòng |
| `liveroom.prejoin.code.label` | Enter room code | Nhập mã phòng |
| `liveroom.prejoin.code.placeholder` | 6 characters | 6 ký tự |
| `liveroom.prejoin.code.help` | e.g., ABC123 | Ví dụ: ABC123 |
| `liveroom.prejoin.device.label` | Choose your device | Chọn thiết bị |
| `liveroom.prejoin.mic.test` | Test microphone | Kiểm tra micro |
| `liveroom.prejoin.camera.test` | Test camera | Kiểm tra camera |
| `liveroom.prejoin.submit` | Join now | Tham gia ngay |
| `liveroom.prejoin.cancel` | Cancel | Hủy |
| `liveroom.prejoin.error.notfound` | Room not found. Check the code | Không tìm thấy phòng. Kiểm tra lại mã |
| `liveroom.prejoin.error.ended` | This room has ended | Phòng đã kết thúc |
| `liveroom.prejoin.error.full` | Room is full | Phòng đã đầy |
| `liveroom.prejoin.error.ratelimit` | Too many attempts. Please wait | Quá nhiều lần thử. Vui lòng đợi |

### 5.4. Waiting Room (SC-04)

| Key | EN | VI |
|---|---|---|
| `liveroom.waiting.title` | Waiting for owner approval | Đang chờ owner duyệt |
| `liveroom.waiting.roomInfo` | {roomName} by {ownerEmail} | {roomName} của {ownerEmail} |
| `liveroom.waiting.cancel` | Cancel request | Hủy yêu cầu |
| `liveroom.waiting.approved.toast` | You have been approved! | Bạn đã được duyệt! |
| `liveroom.waiting.rejected.toast` | Your request was rejected | Yêu cầu của bạn đã bị từ chối |
| `liveroom.waiting.timeout.toast` | Request timed out | Yêu cầu đã hết thời gian chờ |

### 5.5. In-Room (SC-06)

| Key | EN | VI |
|---|---|---|
| `liveroom.room.participants` | Participants ({count}/{max}) | Người tham gia ({count}/{max}) |
| `liveroom.room.owner.badge` | Owner | Owner |
| `liveroom.room.mic.on` | Microphone on | Mic đang bật |
| `liveroom.room.mic.off` | Microphone off | Mic đang tắt |
| `liveroom.room.camera.on` | Camera on | Camera đang bật |
| `liveroom.room.camera.off` | Camera off | Camera đang tắt |
| `liveroom.room.mic.mutedByOwner` | Microphone muted by owner | Mic đã bị owner tắt |
| `liveroom.room.leave.confirm.title` | Leave this room? | Rời khỏi phòng này? |
| `liveroom.room.leave.confirm.message` | Other participants will see you leave | Người tham gia khác sẽ thấy bạn rời đi |
| `liveroom.room.leave.confirm.yes` | Yes, leave | Có, rời đi |
| `liveroom.room.end.confirm.title` | End this room for everyone? | Kết thúc phòng cho tất cả? |
| `liveroom.room.end.confirm.message` | This will disconnect all participants | Điều này sẽ ngắt kết nối tất cả người tham gia |
| `liveroom.room.end.confirm.yes` | Yes, end room | Có, kết thúc phòng |
| `liveroom.room.end.undo.toast` | Room ended. Tap to undo within 5s | Phòng đã kết thúc. Nhấn để hoàn tác trong 5 giây |
| `liveroom.room.idle.ghost.label` | Idle | Không hoạt động |

### 5.6. Music Panel

| Key | EN | VI |
|---|---|---|
| `liveroom.music.title` | Shared Music | Nhạc chia sẻ |
| `liveroom.music.play` | Play | Phát |
| `liveroom.music.pause` | Pause | Tạm dừng |
| `liveroom.music.next` | Next | Bài tiếp |
| `liveroom.music.prev` | Previous | Bài trước |
| `liveroom.music.volume` | Volume | Âm lượng |
| `liveroom.music.select` | Select song | Chọn bài hát |
| `liveroom.music.empty` | No song playing. Choose one from your library | Không có bài hát đang phát. Chọn từ thư viện của bạn |
| `liveroom.music.notOwnSong.toast` | You can only play songs from your library | Bạn chỉ có thể phát bài từ thư viện của bạn |
| `liveroom.music.ownerAbsent.toast` | Music controls disabled while owner is away | Điều khiển nhạc bị tắt khi owner vắng mặt |
| `liveroom.music.ownerPaused.toast` | Owner paused playback | Owner đã tạm dừng phát nhạc |
| `liveroom.music.stateConflict.toast` | Another user is controlling. Please retry | Người khác đang điều khiển. Vui lòng thử lại |

### 5.7. Chat

| Key | EN | VI |
|---|---|---|
| `liveroom.chat.title` | Chat | Trò chuyện |
| `liveroom.chat.placeholder` | Type a message... | Nhập tin nhắn... |
| `liveroom.chat.send` | Send | Gửi |
| `liveroom.chat.history.loadMore` | Load older messages | Tải tin nhắn cũ hơn |
| `liveroom.chat.empty` | No messages yet | Chưa có tin nhắn |

### 5.8. Annotation

| Key | EN | VI |
|---|---|---|
| `liveroom.annotation.title` | Annotations | Ghi chú |
| `liveroom.annotation.create.placeholder` | Add a note at this moment... | Thêm ghi chú tại thời điểm này... |
| `liveroom.annotation.create.submit` | Add note | Thêm ghi chú |
| `liveroom.annotation.position.label` | At {time} | Tại {time} |
| `liveroom.annotation.empty` | No annotations yet | Chưa có ghi chú nào |
| `liveroom.annotation.positionInvalid.toast` | Position must be within the song | Vị trí phải nằm trong bài hát |
| `liveroom.annotation.popup.title` | Note from {user} at {time} | Ghi chú của {user} tại {time} |

### 5.9. Session History (SC-07)

| Key | EN | VI |
|---|---|---|
| `liveroom.history.title` | Session History | Lịch sử phiên |
| `liveroom.history.cycle.label` | Session {number} | Phiên {number} |
| `liveroom.history.cycle.duration` | {duration} • {participants} participants | {duration} • {participants} người tham gia |
| `liveroom.history.cycle.annotations` | {count} annotations | {count} ghi chú |
| `liveroom.history.empty` | No previous sessions | Không có phiên trước đó |

### 5.10. Kicked User Landing (SC-13)

| Key | EN | VI |
|---|---|---|
| `liveroom.kicked.title` | You have been kicked | Bạn đã bị kick |
| `liveroom.kicked.message` | The room owner has removed you from the room | Owner phòng đã loại bạn khỏi phòng |
| `liveroom.kicked.cooldown` | You can rejoin in {minutes} minutes | Bạn có thể tham gia lại sau {minutes} phút |
| `liveroom.kicked.cooldown.seconds` | You can rejoin in {seconds} seconds | Bạn có thể tham gia lại sau {seconds} giây |
| `liveroom.kicked.back` | Back to home | Về trang chủ |

### 5.11. Multi-Tab Conflict

| Key | EN | VI |
|---|---|---|
| `liveroom.multitab.title` | Room open in another tab | Phòng đang mở ở tab khác |
| `liveroom.multitab.message` | You can only be in this room from one tab at a time | Bạn chỉ có thể ở trong phòng này từ một tab tại một thời điểm |
| `liveroom.multitab.switch` | Switch to other tab | Chuyển sang tab khác |
| `liveroom.multitab.close` | Close this tab | Đóng tab này |

---

## 6. Tooltip & Helper Text

| Key | EN | VI |
|---|---|---|
| `liveroom.tooltip.ownerSlot` | Your seat is reserved. You can return within {grace}s | Ghế của bạn được giữ. Bạn có thể quay lại trong {grace}s |
| `liveroom.tooltip.rejoinGrace` | Owner grace period | Thời gian grace của owner |
| `liveroom.tooltip.idleGhost` | Idle for {minutes} minutes | Không hoạt động {minutes} phút |
| `liveroom.tooltip.micMuteCooldown` | You can unmute in {seconds}s | Bạn có thể bật mic sau {seconds}s |
| `liveroom.tooltip.kickedCooldown` | Cooldown: {minutes}m {seconds}s | Chờ: {minutes}p {seconds}s |
| `liveroom.tooltip.sequenceNumber` | Sync version {seq} | Phiên bản đồng bộ {seq} |

---

## 7. Validation Messages Mapping

### 7.1. Backend (messages.properties)

```properties
# Room
validation.room.name.length=Room name must be 1-100 characters
validation.room.name.blank=Room name cannot be empty
validation.room.code.format=Room code must be 6 characters A-Z0-9
validation.room.capacity.range=Max participants must be 1-7
validation.room.grace.range=Grace period must be 30-1800 seconds

# Chat
validation.chat.empty=Message cannot be empty
validation.chat.length=Message must be 1-500 characters

# Annotation
validation.annotation.empty=Annotation cannot be empty
validation.annotation.length=Annotation must be 1-200 characters
validation.annotation.position=Annotation position must be within song duration
```

### 7.2. Backend (messages_vi.properties)

```properties
# Room
validation.room.name.length=Tên phòng phải từ 1-100 ký tự
validation.room.name.blank=Tên phòng không được để trống
validation.room.code.format=Mã phòng phải có 6 ký tự A-Z0-9
validation.room.capacity.range=Số người tối đa phải từ 1-7
validation.room.grace.range=Thời gian grace phải từ 30-1800 giây

# Chat
validation.chat.empty=Tin nhắn không được để trống
validation.chat.length=Tin nhắn phải từ 1-500 ký tự

# Annotation
validation.annotation.empty=Annotation không được để trống
validation.annotation.length=Annotation phải từ 1-200 ký tự
validation.annotation.position=Vị trí annotation phải nằm trong bài hát
```

---

## 8. Placeholder & Argument Convention

### 8.1. Argument Placeholders

Backend dùng `MessageFormat`-style:
```properties
LIVEROOM_KICKED_COOLDOWN=Please wait {0}s before rejoining
```

Frontend dùng ICU MessageFormat hoặc i18next interpolation:
```json
{
  "LIVEROOM_KICKED_COOLDOWN": "Please wait {{seconds}}s before rejoining"
}
```

### 8.2. Argument Types

| Placeholder | Type | Example | Used in |
|---|---|---|---|
| `{0}`, `{seconds}` | int/long | `30`, `300` | LIVEROOM_KICKED_COOLDOWN |
| `{1}`, `{minutes}` | int/long | `5`, `60` | LIVEROOM_KICKED_COOLDOWN |
| `{2}`, `{time}` | String | `02:30` | Annotation position |
| `{user}` | String (email/name) | `an@congty.com` | Annotation popup |

### 8.3. Multi-argument Example

```properties
# Backend
LIVEROOM_ANNOTATION_POPUP=Note from {0} at {1}

# Usage
messageSource.getMessage(
    "LIVEROOM_ANNOTATION_POPUP",
    new Object[] { "an@congty.com", "02:00" },
    locale
);
```

```json
{
  "LIVEROOM_ANNOTATION_POPUP": "Note from {{user}} at {{time}}"
}
```

---

## 9. Frontend Locale Files

### 9.1. en.json Structure

```json
{
  "liveroom": {
    "common": { "create": "Create room", ... },
    "create": { "title": "Create a new room", ... },
    "prejoin": { ... },
    "waiting": { ... },
    "room": { ... },
    "music": { ... },
    "chat": { ... },
    "annotation": { ... },
    "history": { ... },
    "kicked": { ... },
    "multitab": { ... },
    "tooltip": { ... }
  },
  "validation": {
    "room": { ... },
    "chat": { ... },
    "annotation": { ... }
  },
  "error": {
    "LIVEROOM_PRO_REQUIRED": "PRO role required",
    ...
  }
}
```

### 9.2. vi.json Structure

Cùng cấu trúc, dịch sang tiếng Việt tự nhiên.

### 9.3. Backend usage

```java
@RequiredArgsConstructor
@RestController
public class LiveRoomController {
    
    private static final String MSG_ROOM_CREATED = "LIVEROOM_ROOM_CREATED";
    
    private final MessageSource messageSource;
    
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody RoomCreateRequest request) {
        RoomResponse data = liveRoomService.createRoom(request, LocaleContextHolder.getLocale());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(message(MSG_ROOM_CREATED), data));
    }
    
    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
```

---

## 10. Testing Strategy

### 10.1. i18n Tests

Mỗi key BẮT BUỘC test cho 2 locale (EN, VI):

```java
@Test
void message_resolved_correctly_for_en() {
    String msg = messageSource.getMessage(
        "LIVEROOM_ROOM_CREATED", null, Locale.ENGLISH
    );
    assertThat(msg).isEqualTo("Room created");
}

@Test
void message_resolved_correctly_for_vi() {
    String msg = messageSource.getMessage(
        "LIVEROOM_ROOM_CREATED", null, new Locale("vi")
    );
    assertThat(msg).isEqualTo("Phòng đã được tạo");
}

@Test
void message_with_arguments() {
    String msg = messageSource.getMessage(
        "LIVEROOM_KICKED_COOLDOWN", new Object[]{30}, Locale.ENGLISH
    );
    assertThat(msg).isEqualTo("Please wait 30s before rejoining");
}
```

### 10.2. Key Existence Test

```java
@Test
void all_required_keys_exist_in_all_locales() {
    String[] requiredKeys = {
        "LIVEROOM_ROOM_CREATED",
        "LIVEROOM_ROOM_ENDED",
        "LIVEROOM_REQUEST_APPROVED",
        // ...
    };
    
    for (String key : requiredKeys) {
        assertDoesNotThrow(() -> 
            messageSource.getMessage(key, null, Locale.ENGLISH)
        );
        assertDoesNotThrow(() -> 
            messageSource.getMessage(key, null, new Locale("vi"))
        );
    }
}
```

### 10.3. Vietnamese Translation Quality

- Không Google Translate máy móc
- Dịch tự nhiên, dùng từ ngữ phổ biến trong ứng dụng Việt Nam
- Ví dụ: "Room" → "Phòng" (KHÔNG dịch "Phòng họp" vì cả phòng karaoke cũng dùng)
- Ví dụ: "Join" → "Tham gia" (KHÔNG "Gia nhập")

---

## 11. Workflow khi thêm key mới

### 11.1. Step-by-step

1. **Đặt tên key** theo convention `LIVEROOM_<UPPER_SNAKE>` hoặc `validation.<field>.<rule>` hoặc `liveroom.<context>.<key>`
2. **Thêm vào backend `messages.properties`** (fallback = EN)
3. **Thêm vào `messages_en.properties`** (giống fallback)
4. **Thêm vào `messages_vi.properties`** (dịch tiếng Việt)
5. **Thêm vào frontend `en.json`**
6. **Thêm vào frontend `vi.json`**
7. **Test 2 locale** với key mới
8. **Update tài liệu này** (file này)

### 11.2. Checklist

- [ ] Key theo đúng convention
- [ ] Đã thêm vào CẢ 5 file (3 backend + 2 frontend)
- [ ] EN, VI đều có message
- [ ] Placeholder argument khớp giữa các file
- [ ] Tiếng Việt tự nhiên, không Google Translate
- [ ] Test pass cho 2 locale
- [ ] Đã update `liveroom-i18n-keys.md` (file này)

---

## 12. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **API**: `liveroom-api-spec.md` v1.0
- **WebSocket**: `liveroom-ws-protocol.md` v1.0
- **State machines**: `liveroom-state-machines.md` v1.0
- **Concurrency**: `liveroom-concurrency.md` v1.0
- **Jobs**: `liveroom-jobs.md` v1.0

---

**Người viết**: Senior Dev Team
**Reviewer**: Backend Lead, Frontend Lead, QA Lead
**Ngày review**: Pending