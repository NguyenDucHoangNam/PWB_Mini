# Live Room — REST API Specification

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-data-model.md` v1.0
**Đối tượng đọc**: Backend Dev, Frontend Dev, QA
**Mục đích**: Quy chuẩn Contract REST API cho module Live Room. Code backend **BẮT BUỘC** tuân theo spec này.

---

## 1. Quy ước chung

### 1.1. Base URL

```
Production: https://api.pwb-mini.com/api/v1/liverooms
Staging:    https://api-staging.pwb-mini.com/api/v1/liverooms
Local:      http://localhost:8080/api/v1/liverooms
```

### 1.2. Authentication

- Mọi endpoint yêu cầu JWT Bearer token trong header `Authorization`
- Format: `Authorization: Bearer <jwt_token>`
- Token chứa `userId`, `role` (PRO/USER), `email`
- Validate bởi Spring Security filter trước khi vào controller
- Token expiration: 1 giờ (refresh qua `/api/v1/auth/refresh`)

### 1.3. Common Headers

| Header | Required | Mô tả |
|---|---|---|
| `Authorization` | YES | Bearer token |
| `Accept-Language` | NO | `en` / `vi` (mặc định `en`) |
| `Content-Type` | YES (POST/PUT/PATCH) | `application/json` |
| `Idempotency-Key` | YES (cho create JoinRequest) | UUID v4, TTL 24h |
| `X-Request-Id` | NO | UUID, dùng cho tracing |
| `X-Client-Version` | NO | Frontend version (vd: `1.0.0`) |

### 1.4. Response Format

**Success response**:
```json
{
  "success": true,
  "data": { ... },
  "message": "Phòng đã được tạo",
  "traceId": "uuid"
}
```

**Error response** (xem `liveroom-error-code-mapper.md`):
```json
{
  "success": false,
  "error": {
    "code": "LIVEROOM_ROOM_NOT_FOUND",
    "message": "Không tìm thấy phòng",
    "details": { "field": "value" }
  },
  "traceId": "uuid"
}
```

### 1.5. HTTP Status Code

| Code | Ý nghĩa | Dùng khi |
|---|---|---|
| 200 OK | Success | GET / PUT / PATCH success |
| 201 Created | Resource created | POST success |
| 204 No Content | Success, no body | DELETE success |
| 400 Bad Request | Validation error | Invalid input |
| 401 Unauthorized | Missing/invalid token | Auth fail |
| 403 Forbidden | Permission denied | Not owner, not PRO |
| 404 Not Found | Resource not found | Room not existed |
| 409 Conflict | State conflict | Room full, dup request |
| 429 Too Many Requests | Rate limit | Brute force, KICKED cooldown |
| 500 Internal Server Error | Server error | Unexpected |

### 1.6. Pagination

Cursor-based pagination cho list endpoint:
```
GET /api/v1/liverooms/{roomId}/chat/messages?cursor=<msgId>&size=50
```
- `cursor`: ID của message cuối cùng đã có (NULL = load mới nhất)
- `size`: 1-100 (default 50)
- Response: `{ messages: [...], hasMore: boolean, nextCursor: string }`

### 1.7. Rate Limiting

Áp dụng cho:
- **Room code brute-force** (R-CREATE-05 v1.8): 5 attempts/IP/giờ → HTTP 429
- **Idempotency key** (R-JOIN-07): 24h TTL

Header response khi rate limit:
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 2026-07-26T16:00:00Z
Retry-After: 3600
```

---

## 2. Endpoint Catalog

### 2.1. Room Management

#### 2.1.1. `POST /api/v1/liverooms` — Tạo phòng

**Permission**: PRO role

**Request body**:
```json
{
  "roomName": "Daily Sync",
  "maxParticipants": 7,
  "ownerGraceSeconds": 60
}
```

| Field | Type | Required | Validation | Message key |
|---|---|---|---|---|
| `roomName` | string | YES | `@NotBlank @Size(min=1, max=100)` | validation.room.name.length |
| `maxParticipants` | int | NO | `@Min(1) @Max(7)`, default 7 | LIVEROOM_CAPACITY_INVALID |
| `ownerGraceSeconds` | int | NO | `@Min(30) @Max(1800)`, default 60 | LIVEROOM_GRACE_INVALID |

**Response 201**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomCode": "ABC123",
    "roomName": "Daily Sync",
    "ownerId": "uuid",
    "ownerEmail": "an@congty.com",
    "maxParticipants": 7,
    "currentParticipantCount": 0,
    "status": "ACTIVE",
    "ownerGraceSeconds": 60,
    "startedAt": "2026-07-26T10:00:00Z",
    "createdAt": "2026-07-26T10:00:00Z"
  },
  "message": "LIVEROOM_ROOM_CREATED",
  "traceId": "uuid"
}
```

**Error codes**:
- `400 LIVEROOM_VALIDATION_FAILED` — validation fail
- `403 LIVEROOM_PRO_REQUIRED` — user not PRO
- `409 LIVEROOM_ROOM_NAME_DUPLICATE` — cùng owner có tên trùng (sau trim+lowercase)
- `500 LIVEROOM_INTERNAL_ERROR` — server error

**Business logic**:
1. Validate input
2. Generate `roomCode` 6 chars A-Z0-9 (retry 5 lần nếu unique constraint fail)
3. Compute `roomNameNormalized = LOWER(TRIM(roomName))`
4. Insert `liveroom_rooms` row
5. Tạo `RoomSessionCycle` đầu tiên (cycle_number=1)
6. Insert `liveroom_rooms.current_session_cycle_id = cycle.id`
7. Không tạo participant row cho owner (owner tự động join qua UI)

---

#### 2.1.2. `GET /api/v1/liverooms/by-code/{roomCode}` — Lookup phòng bằng code

**Permission**: Authenticated user

**Path params**:
- `roomCode`: 6 chars A-Z0-9

**Response 200**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomCode": "ABC123",
    "roomName": "Daily Sync",
    "ownerEmail": "an@congty.com",
    "maxParticipants": 7,
    "currentParticipantCount": 5,
    "hasSlot": true,
    "status": "ACTIVE",
    "isParticipant": false,
    "wasApproved": false,
    "ownerId": "uuid"
  },
  "message": "LIVEROOM_ROOM_FOUND"
}
```

**Error codes**:
- `404 LIVEROOM_ROOM_NOT_FOUND` — code không tồn tại
- `429 LIVEROOM_BRUTE_FORCE_LIMIT` — vượt rate limit 5/IP/giờ

**Rate limit**: 5/IP/giờ (R-CREATE-05 v1.8)

**Business logic**:
1. Rate limit check theo IP
2. Uppercase + trim `roomCode`
3. Query `liveroom_rooms` WHERE `room_code = ?`
4. Compute `isParticipant`: user có participant row ACTIVE trong room không
5. Compute `wasApproved`: user có `was_approved = TRUE` trong room không (bất kỳ cycle)
6. Compute `hasSlot`: `current_count < max_participants` (account `reservedOwnerSlot`)

---

#### 2.1.3. `GET /api/v1/liverooms/{roomId}` — Get chi tiết phòng

**Permission**: Authenticated user (chỉ thấy phòng ACTIVE hoặc ENDED mà user đã từng tham gia)

**Response 200**: giống 2.1.2

**Error codes**:
- `404 LIVEROOM_ROOM_NOT_FOUND` — không tồn tại hoặc user không có quyền xem

---

#### 2.1.4. `GET /api/v1/liverooms` — List phòng của owner

**Permission**: PRO role

**Query params**:
- `status` (optional): `ACTIVE` / `ENDED` / `ALL` (default ALL)
- `page` (optional): 0-indexed (default 0)
- `size` (optional): 1-50 (default 20)

**Response 200**:
```json
{
  "success": true,
  "data": {
    "rooms": [
      { "id": "uuid", "roomCode": "ABC123", "roomName": "Daily Sync", "status": "ACTIVE", ... }
    ],
    "total": 3,
    "page": 0,
    "size": 20
  }
}
```

---

#### 2.1.5. `POST /api/v1/liverooms/{roomId}/end` — Kết thúc phòng

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "ENDED",
    "endedAt": "2026-07-26T11:00:00Z",
    "endedReason": "manual",
    "undoAvailableUntil": "2026-07-26T11:00:05Z"
  },
  "message": "LIVEROOM_ROOM_ENDED"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER` — không phải owner
- `409 LIVEROOM_ROOM_ALREADY_ENDED` — phòng đã ENDED

**Business logic** (R-END-01 + R-END-12 v1.8):
1. Pessimistic lock `liveroom_rooms` row
2. Set status = ENDED, endedReason = 'manual', endedAt = now
3. Update tất cả participants ACTIVE → state = ENDED, leftAt = now
4. Update tất cả JoinRequest PENDING → state = EXPIRED, rejectionReason = ROOM_ENDED
5. Close `RoomSessionCycle` (endedAt = now)
6. **Trả về `undoAvailableUntil = now + 5s`** (cho toast undo)
7. WS broadcast `ROOM_MANUAL_ENDED` (xem `liveroom-ws-protocol.md`)

**Quan trọng**: KHÔNG xoá `liveroom_rooms` row, chỉ set status.

---

#### 2.1.6. `POST /api/v1/liverooms/{roomId}/undo-end` — Hoàn tác kết thúc (R-END-12 v1.8)

**Permission**: Owner only

**Body**: empty

**Response 200**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "ACTIVE",
    "revivedAt": "2026-07-26T11:00:03Z"
  },
  "message": "LIVEROOM_ROOM_REVIVED"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER`
- `409 LIVEROOM_UNDO_WINDOW_EXPIRED` — quá 5 giây
- `409 LIVEROOM_ROOM_NOT_ENDED` — phòng chưa ENDED

**Business logic**:
1. Lock `liveroom_rooms` row
2. Check `ended_at + 5s > now` — nếu không → 409
3. Set status = ACTIVE, endedReason = NULL, endedAt = NULL
4. Reopen `RoomSessionCycle` (endedAt = NULL) — KHÔNG tạo cycle mới
5. Update participants ENDED → ACTIVE (giữ joinedAt cũ)
6. Update JoinRequest EXPIRED → PENDING (KHÔNG tự động revert — user phải gửi lại)
7. WS broadcast `ROOM_REVIVED`

---

#### 2.1.7. `POST /api/v1/liverooms/{roomId}/reopen` — Mở lại phòng ENDED

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "ACTIVE",
    "reopenedCount": 2,
    "previousEndedAt": "2026-07-25T10:00:00Z",
    "startedAt": "2026-07-26T10:00:00Z"
  },
  "message": "LIVEROOM_ROOM_REOPENED"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER`
- `409 LIVEROOM_ROOM_NOT_ENDED` — phòng chưa ENDED
- `403 LIVEROOM_PRO_REQUIRED` — owner hiện tại không còn PRO

**Business logic** (R-REOPEN-01 → R-REOPEN-09):
1. Pessimistic lock `liveroom_rooms` row
2. Validate status = ENDED, owner vẫn là PRO
3. Set status = ACTIVE, endedAt = NULL, endedReason = NULL
4. `started_at = now()`, `reopened_count++`, `last_reopened_at = now`
5. Tạo `RoomSessionCycle` mới (cycle_number = reopened_count + 1)
6. Set `current_session_cycle_id = new_cycle.id`
7. **Xoá JoinRequest cũ** (R-REOPEN-07)
8. **Reset RejectCounter** (R-REJECT-04.1) — xoá theo cycle cũ
9. **Reset PlaybackState** (R-MUSIC-07) — xoá theo room
10. **Keep Annotation theo cycle cũ** (R-ANNOT-05 v1.8) — KHÔNG xoá, chỉ phân biệt theo cycle
11. WS broadcast `ROOM_REOPENED`

---

### 2.2. Join Request Flow

#### 2.2.1. `POST /api/v1/liverooms/{roomId}/join-requests` — Gửi yêu cầu tham gia

**Permission**: Authenticated user (any role)

**Headers**:
- `Idempotency-Key`: UUID v4 (required)

**Request body**: empty

**Response 201**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomId": "uuid",
    "userId": "uuid",
    "userEmail": "binh@congty.com",
    "state": "PENDING",
    "createdAt": "2026-07-26T10:00:00Z"
  },
  "message": "LIVEROOM_REQUEST_CREATED"
}
```

**Error codes**:
- `400 LIVEROOM_IDEMPOTENCY_KEY_REQUIRED`
- `403 LIVEROOM_ALREADY_PARTICIPANT` — user đã ACTIVE trong phòng (R-JOIN-10)
- `404 LIVEROOM_ROOM_NOT_FOUND`
- `409 LIVEROOM_ROOM_ENDED` — phòng ENDED
- `409 LIVEROOM_DUPLICATE_REQUEST` — đã có PENDING/APPROVED request
- `409 LIVEROOM_MULTI_TAB_CONFLICT` — user ACTIVE ở tab khác (R-JOIN-10)
- `429 LIVEROOM_KICKED_COOLDOWN` — bị kick, chưa hết 5 phút (R-ADMIN-04)
- `403 LIVEROOM_REQUEST_LOCKED` — rejectCountByOwner ≥ 3

**Business logic** (R-JOIN-07 v1.8 idempotency):
1. Validate `Idempotency-Key` header
2. Check `kickedCooldownUntil > now` trong `liveroom_rooms.user_id = currentUser.id` → 429
3. Check user có participant ACTIVE trong room → 409 MULTI_TAB_CONFLICT
4. Check JoinRequest PENDING/APPROVED exists (room, user, currentCycle) → 409 DUPLICATE
5. Check `rejectCountByOwner >= 3` → tạo JoinRequest với state = LOCKED, return 403
6. **Insert with idempotency**: UPSERT `liveroom_join_requests` WHERE `idempotency_key = ?` AND `created_at > now - 24h`
   - Nếu key tồn tại → return row cũ (200 OK thay vì 201)
   - Nếu key mới → INSERT new row với state = PENDING
7. WS broadcast `JOIN_REQUEST_CREATED` cho owner

---

#### 2.2.2. `POST /api/v1/liverooms/{roomId}/join-requests/{requestId}/approve` — Duyệt yêu cầu

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "requestId": "uuid",
    "state": "APPROVED",
    "userId": "uuid",
    "autoJoin": true,
    "participantId": "uuid"
  },
  "message": "LIVEROOM_REQUEST_APPROVED"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER`
- `404 LIVEROOM_REQUEST_NOT_FOUND`
- `409 LIVEROOM_REQUEST_NOT_PENDING` — request đã resolved
- `409 LIVEROOM_ROOM_FULL` — capacity full (R-APPROVE-05)
- `409 LIVEROOM_ROOM_ENDED` — phòng ENDED

**Business logic** (R-APPROVE-02 + R-CAPACITY-02 v1.7):
1. Pessimistic lock `liveroom_rooms` row (`SELECT ... FOR UPDATE`)
2. Lock `liveroom_join_requests` row
3. Validate request.state = PENDING
4. Check `current_participant_count >= max_participants` (account `reservedOwnerSlot`)
   - Nếu full → update JoinRequest state = REJECTED_BY_CAPACITY, rejectionReason = CAPACITY_FULL
     - Increment `RejectCounter.rejectCountByCapacity`
     - Return 409 LIVEROOM_ROOM_FULL
5. Update JoinRequest state = APPROVED, resolvedAt = now, resolvedBy = owner.id
6. Create `Participant` row: state = ACTIVE, joinedAt = now, was_approved = TRUE
7. Increment `liveroom_rooms.current_participant_count`
8. WS broadcast `REQUEST_APPROVED` cho user được approve + `PARTICIPANT_JOINED` cho room

**Transaction**: `@Transactional` bao bọc toàn bộ step 1-7.

---

#### 2.2.3. `POST /api/v1/liverooms/{roomId}/join-requests/{requestId}/reject` — Từ chối yêu cầu

**Permission**: Owner only

**Request body** (optional):
```json
{ "reason": "spam" }
```

**Response 200**:
```json
{
  "success": true,
  "data": {
    "requestId": "uuid",
    "state": "REJECTED_BY_OWNER",
    "rejectionReason": "OWNER_REJECT",
    "rejectCountByOwner": 1
  },
  "message": "LIVEROOM_REQUEST_REJECTED_BY_OWNER"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER`
- `404 LIVEROOM_REQUEST_NOT_FOUND`
- `409 LIVEROOM_REQUEST_NOT_PENDING`

**Business logic** (R-REJECT-01..05 v1.8):
1. Lock `liveroom_join_requests` row
2. Validate state = PENDING
3. Update state = REJECTED_BY_OWNER, rejectionReason = OWNER_REJECT, resolvedAt = now
4. **UPSERT RejectCounter** (atomic increment):
   ```sql
   INSERT INTO liveroom_reject_counters (id, user_id, room_id, room_session_cycle_id, reject_count_by_owner, ...)
   VALUES (uuid, ?, ?, ?, 1, ...)
   ON CONFLICT (user_id, room_id, room_session_cycle_id)
   DO UPDATE SET reject_count_by_owner = liveroom_reject_counters.reject_count_by_owner + 1;
   ```
5. Query lại `rejectCountByOwner` → trả về cho client
6. WS broadcast `REQUEST_REJECTED_BY_OWNER` cho user

---

#### 2.2.4. `POST /api/v1/liverooms/{roomId}/join-requests/{requestId}/cancel` — User tự hủy

**Permission**: User đã gửi request

**Response 200**:
```json
{
  "success": true,
  "data": { "requestId": "uuid", "state": "CANCELLED" },
  "message": "LIVEROOM_REQUEST_CANCELLED"
}
```

**Business logic**:
1. Lock `liveroom_join_requests` row
2. Validate state = PENDING, userId = currentUser.id
3. Update state = CANCELLED, rejectionReason = USER_CANCELLED
4. WS broadcast `JOIN_REQUEST_CANCELLED` cho owner

---

### 2.3. Participant Lifecycle

#### 2.3.1. `POST /api/v1/liverooms/{roomId}/leave` — User rời phòng

**Permission**: ACTIVE participant (own participant row) hoặc owner

**Response 200**:
```json
{
  "success": true,
  "data": {
    "participantId": "uuid",
    "state": "LEFT",
    "leftAt": "2026-07-26T10:30:00Z"
  },
  "message": "LIVEROOM_ROOM_LEFT"
}
```

**Business logic** (R-LEAVE-01..10):
1. Lock `liveroom_rooms` row
2. Validate participant.state = ACTIVE cho currentUser
3. Set participant.state = LEFT, leftAt = now
4. Decrement `liveroom_rooms.current_participant_count`
5. **Nếu là owner**:
   - Set `owner_left_at = now`
   - Set `reserved_owner_slot = TRUE`
   - Giảm effective max = max - 1
   - **Debounce 3s** (R-LEAVE-04.1): Nếu owner rejoin trong 3s → KHÔNG broadcast WS OWNER_LEFT
   - Schedule grace expiry job (xem `liveroom-jobs.md`)
6. WS broadcast `PARTICIPANT_LEFT` (hoặc `OWNER_LEFT` nếu là owner)

---

#### 2.3.2. `POST /api/v1/liverooms/{roomId}/rejoin` — Rejoin phòng (was_approved)

**Permission**: Authenticated user có `was_approved = TRUE` cho room

**Response 200**:
```json
{
  "success": true,
  "data": {
    "participantId": "uuid",
    "state": "ACTIVE",
    "joinedAt": "2026-07-26T11:00:00Z"
  }
}
```

**Business logic** (R-JOIN-04):
1. Pessimistic lock `liveroom_rooms` row
2. Validate user.was_approved = TRUE cho room
3. Check participant row exists cho currentCycle
   - Nếu exists với state ACTIVE → 409 ALREADY_IN_ROOM
   - Nếu exists với state LEFT/KICKED → UPDATE → ACTIVE
4. Check `current_participant_count < max_participants` (account `reservedOwnerSlot`)
   - Nếu full → 409 LIVEROOM_ROOM_FULL
5. Check `kickedCooldownUntil > now` → 429 LIVEROOM_KICKED_COOLDOWN
6. Set participant.state = ACTIVE, joinedAt = now, mic_state = UNMUTED, camera_on = FALSE, mic_on = FALSE
7. Increment `current_participant_count`
8. WS broadcast `PARTICIPANT_JOINED`

---

#### 2.3.3. `POST /api/v1/liverooms/{roomId}/participants/{userId}/kick` — Owner kick user

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "participantId": "uuid",
    "userId": "uuid",
    "state": "KICKED",
    "kickedAt": "2026-07-26T10:30:00Z",
    "kickedCooldownUntil": "2026-07-26T10:35:00Z"
  },
  "message": "LIVEROOM_PARTICIPANT_KICKED"
}
```

**Error codes**:
- `400 LIVEROOM_SELF_KICK_NOT_ALLOWED` — owner tự kick (R-ADMIN-02)
- `403 LIVEROOM_NOT_OWNER`
- `404 LIVEROOM_PARTICIPANT_NOT_FOUND`

**Business logic** (R-ADMIN-04 v1.8):
1. Lock `liveroom_rooms` row
2. Validate target ≠ owner
3. Set participant.state = KICKED, leftAt = now
4. Set `liveroom_rooms.kicked_user_id = targetUserId`, `kicked_at = now`, `kicked_cooldown_until = now + 5min`
5. Decrement `current_participant_count`
6. **Audit log** (R-ADMIN-06): INSERT `liveroom_admin_actions` (action_type=KICK, actor=owner, target=userId)
7. WS broadcast `PARTICIPANT_KICKED` cho room + cho user bị kick
8. Frontend nhận event → navigate user về SC-13 + cache state (R-KICK-04)

---

#### 2.3.4. `POST /api/v1/liverooms/{roomId}/participants/{userId}/mute-mic` — Owner mute mic

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "participantId": "uuid",
    "micState": "MUTED_BY_OWNER",
    "mutedAt": "2026-07-26T10:30:00Z",
    "cooldownUntil": "2026-07-26T10:30:30Z"
  },
  "message": "LIVEROOM_MIC_MUTED_BY_OWNER"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_OWNER`
- `404 LIVEROOM_PARTICIPANT_NOT_FOUND`

**Business logic** (R-ADMIN-05 + R-MEDIA-09/10 v1.8):
1. Validate owner
2. Set participant.mic_state = MUTED_BY_OWNER, mic_on = FALSE
3. Set `mic_muted_by_owner_at = now`, `mic_mute_cooldown_until = now + 30s`
4. **Audit log**: INSERT `liveroom_admin_actions` (action_type=REMOTE_MUTE)
5. WS broadcast `PARTICIPANT_MIC_MUTED_BY_OWNER` cho room
6. **WebRTC**: Backend force pause audio track của user (qua signaling)

---

### 2.4. Chat

#### 2.4.1. `POST /api/v1/liverooms/{roomId}/chat/messages` — Gửi tin nhắn

**Permission**: ACTIVE participant

**Request body**:
```json
{
  "content": "Hello everyone"
}
```

| Field | Type | Required | Validation | Message key |
|---|---|---|---|---|
| `content` | string | YES | `@NotBlank @Size(min=1, max=500)` | LIVEROOM_CHAT_EMPTY / LIVEROOM_CHAT_TOO_LONG |

**Response 201**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomId": "uuid",
    "userId": "uuid",
    "userEmail": "binh@congty.com",
    "content": "Hello everyone",
    "sentAt": "2026-07-26T10:00:00Z"
  },
  "message": "LIVEROOM_CHAT_SENT"
}
```

**Error codes**:
- `403 LIVEROOM_NOT_IN_SESSION` — không ACTIVE
- `400 LIVEROOM_CHAT_EMPTY` / `LIVEROOM_CHAT_TOO_LONG`

**Business logic** (R-CHAT-04, R-CHAT-07):
1. Validate content (trim, length 1-500)
2. Validate participant.state = ACTIVE
3. Insert `liveroom_chat_messages` row
4. WS broadcast `CHAT_MESSAGE_RECEIVED` cho tất cả ACTIVE participants

**Lưu ý**: KHÔNG qua STOMP, gửi REST → backend broadcast qua WS (REST write, WS read).

---

#### 2.4.2. `GET /api/v1/liverooms/{roomId}/chat/messages` — Load chat history

**Permission**: ACTIVE participant

**Query params**:
- `cursor` (optional): ID message cuối đã có
- `size` (optional): 1-100 (default 50)
- `cycleId` (optional): filter theo cycle (default = current cycle)

**Response 200**:
```json
{
  "success": true,
  "data": {
    "messages": [
      { "id": "uuid", "userEmail": "binh@congty.com", "content": "Hello", "sentAt": "..." }
    ],
    "hasMore": true,
    "nextCursor": "uuid"
  }
}
```

**Business logic** (R-CHAT-05 v1.8):
- **Default load**: 200 messages mới nhất (R-CHAT-05)
- **Pagination**: `WHERE cycleId = ? AND sent_at < (SELECT sent_at FROM chat WHERE id = cursor) ORDER BY sent_at DESC LIMIT size`
- **n+1**: 200 messages mặc định qua WS broadcast + REST initial load

---

### 2.5. Music

**Protocol (Hybrid theo R-MUSIC-09 v1.8 + Phương án C)**:

| Action | Transport | Endpoint |
|---|---|---|
| **GET state** (read-only) | REST | `GET /api/v1/liverooms/{roomId}/music/state` |
| **Play (select song + start)** | STOMP | `/app/liveroom/{roomId}/music/play` payload `{songId: UUID}` |
| **Pause** | STOMP | `/app/liveroom/{roomId}/music/pause` payload `{}` |
| **Seek** | STOMP | `/app/liveroom/{roomId}/music/seek` payload `{positionSeconds: double}` |
| **Change volume** | STOMP | `/app/liveroom/{roomId}/music/volume` payload `{volumePercent: int}` |
| **Get state (fallback)** | STOMP | `/app/liveroom/{roomId}/music/get-state` payload `{}` (response trên topic) |
| **Subscribe events** | STOMP SUB | `/topic/liveroom/{roomId}/music` |

**Lý do Hybrid**:
- **REST GET state**: cho phép init state nhanh khi FE mới join (cache friendly, không cần WebSocket round-trip)
- **STOMP control**: latency-critical cho play/pause/seek/volume + atomic ordering qua sequence number (R-MUSIC-10 v1.8)
- **Tuân theo R-MUSIC-09**: "Music KHÔNG dùng REST API riêng lẻ cho **control**" — control qua STOMP, read OK qua REST

#### 2.5.1. `GET /api/v1/liverooms/{roomId}/music/state` — Lấy current playback state

**Permission**: ACTIVE participant

**Response 200**:
```json
{
  "success": true,
  "data": {
    "roomId": "uuid",
    "songId": "uuid",
    "songTitle": "Song A",
    "songArtist": "Artist B",
    "songDurationSeconds": 240,
    "status": "PLAYING",
    "currentPositionSeconds": 120.5,
    "volumePercent": 80,
    "sequenceNumber": 42,
    "lastUpdatedAt": "2026-07-26T10:00:00Z",
    "lastUpdatedBy": "uuid"
  }
}
```

**Business logic**:
- Return null `songId` nếu không có bài đang phát
- Frontend dùng để sync khi mới join (R-MUSIC-05)

---

#### 2.5.2. STOMP `/app/liveroom/{roomId}/music/play` — Chọn bài + phát

**Lưu ý**: Action này **CHỈ** qua STOMP theo R-MUSIC-09 v1.8 (KHÔNG có REST endpoint tương ứng). Endpoint REST tương tự `POST /music/select` đã bị **XOÁ** khỏi spec vì vi phạm R-MUSIC-09.

**STOMP destination**: `/app/liveroom/{roomId}/music/play`

**Permission**: ACTIVE participant, song thuộc user

**Send payload**:
```json
{ "songId": "uuid" }
```

**Response**: Server broadcast `MUSIC_SONG_CHANGED` + `MUSIC_PLAYBACK_STATE_CHANGED` qua `/topic/liveroom/{roomId}/music` (cho cả phòng subscribe được).

**Broadcast payload** (qua topic, sau khi xử lý thành công):
```json
{
  "eventType": "MUSIC_SONG_CHANGED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 300,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "songId": "uuid",
    "songTitle": "Song B",
    "songArtist": "Artist C",
    "songDurationSeconds": 240,
    "songOwnerId": "uuid"
  }
}
```

**Error codes** (qua STOMP ERROR frame):
- `403 LIVEROOM_MUSIC_NOT_OWN_SONG` — song không thuộc user
- `409 LIVEROOM_MUSIC_NOT_READY` — song status ≠ PROCESSED
- `409 LIVEROOM_ROOM_ENDED`
- `403 LIVEROOM_NOT_IN_SESSION`
- `403 LIVEROOM_MUSIC_OWNER_ABSENT` — owner leave, không cho phép (R-MUSIC-11 v1.8)
- `409 LIVEROOM_MUSIC_STATE_CONFLICT` — optimistic lock fail (R-MUSIC-10 v1.8)

**Business logic** (R-MUSIC-01/02/11):
1. Validate `Song.userId == currentUser.id`
2. Validate `Song.status == PROCESSED`
3. Validate room.ACTIVE
4. Validate `owner_left_at IS NULL` (R-MUSIC-11) — hoặc currentUser là owner
5. Optimistic lock `PlaybackState` (JPA `@Version`) — race condition 2 user cùng play (R-MUSIC-10 v1.8)
6. Update PlaybackState: songId, status = PLAYING, positionSeconds = 0, sequenceNumber++, lastUpdatedAt = now
7. WS broadcast `MUSIC_SONG_CHANGED` + `MUSIC_PLAYBACK_STATE_CHANGED`

**Lưu ý về optimistic lock**: Nếu 2 user cùng gửi `play` cách nhau < 100ms, 1 thắng, 1 nhận STOMP ERROR `LIVEROOM_MUSIC_STATE_CONFLICT`. Client xử lý:
- Frontend hiển thị "Nhạc đã được điều khiển bởi người khác, vui lòng thử lại"
- Auto re-fetch state qua STOMP `/app/liveroom/{roomId}/music/get-state` hoặc REST GET `/music/state`
- Apply state mới nhất (sequence number ordering)

**Sample code (Frontend)**:
```typescript
const stompClient = useStompClient();
stompClient.send(`/app/liveroom/${roomId}/music/play`, JSON.stringify({ songId }));

// Subscribe topic để nhận response
const subscription = stompClient.subscribe(
  `/topic/liveroom/${roomId}/music`,
  (message) => {
    const event = JSON.parse(message.body);
    if (event.eventType === 'MUSIC_SONG_CHANGED') {
      // Cập nhật UI
    }
  }
);
```

---

### 2.6. Annotation

#### 2.6.1. `POST /api/v1/liverooms/{roomId}/annotations` — Tạo annotation

**Permission**: ACTIVE participant + Room đang phát nhạc

**Request body**:
```json
{
  "songId": "uuid",
  "positionSeconds": 120.5,
  "content": "đoạn này nhạc to quá"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `songId` | UUID | YES | phải = `PlaybackState.songId` |
| `positionSeconds` | double | YES | `0 ≤ x ≤ song.duration_seconds` |
| `content` | string | YES | `@NotBlank @Size(min=1, max=200)` |

**Response 201**:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomId": "uuid",
    "songId": "uuid",
    "userId": "uuid",
    "userEmail": "an@congty.com",
    "positionSeconds": 120.5,
    "content": "đoạn này nhạc to quá",
    "createdAt": "2026-07-26T10:00:00Z"
  }
}
```

**Error codes**:
- `400 LIVEROOM_ANNOTATION_EMPTY` / `LIVEROOM_ANNOTATION_TOO_LONG` / `LIVEROOM_ANNOTATION_INVALID_POSITION`
- `400 LIVEROOM_MUSIC_NOT_PLAYING` — không có bài đang phát
- `403 LIVEROOM_NOT_IN_SESSION`
- `403 LIVEROOM_ANNOTATION_NOT_APPROVED` — user không ACTIVE hoặc không was_approved

**Business logic** (R-ANNOT-02/06):
1. Validate participant.state = ACTIVE
2. Validate `PlaybackState.songId = songId`
3. Validate positionSeconds trong range
4. Insert `liveroom_annotations` với `room_session_cycle_id = currentCycle.id`
5. WS broadcast `ANNOTATION_CREATED` cho tất cả ACTIVE participants

---

#### 2.6.2. `GET /api/v1/liverooms/{roomId}/sessions` — List các cycle

**Permission**: Authenticated user đã từng tham gia phòng

**Response 200**:
```json
{
  "success": true,
  "data": {
    "sessions": [
      {
        "cycleId": "uuid",
        "cycleNumber": 1,
        "startedAt": "2026-07-25T10:00:00Z",
        "endedAt": "2026-07-25T11:00:00Z",
        "endedReason": "manual",
        "participantCount": 5,
        "annotationCount": 12
      }
    ]
  }
}
```

**Business logic** (R-ANNOT-08 v1.8):
- Query `liveroom_room_session_cycles` WHERE room_id = ? ORDER BY cycle_number DESC
- JOIN subquery để count participants, annotations

---

#### 2.6.3. `GET /api/v1/liverooms/{roomId}/sessions/{cycleId}/annotations` — List annotations của cycle

**Permission**: User đã từng ACTIVE trong cycle đó

**Response 200**:
```json
{
  "success": true,
  "data": {
    "annotations": [
      { "id": "uuid", "positionSeconds": 120.5, "content": "...", "userEmail": "an@congty.com", "createdAt": "..." }
    ]
  }
}
```

**Business logic**:
- Query `liveroom_annotations` WHERE `room_session_cycle_id = cycleId` ORDER BY `position_seconds ASC`

---

### 2.7. Owner History & Audit

#### 2.7.1. `GET /api/v1/liverooms/{roomId}/ownership-history` — Lịch sử owner

**Permission**: Owner only

**Response 200**:
```json
{
  "success": true,
  "data": {
    "history": [
      {
        "id": "uuid",
        "ownerUserId": "uuid",
        "changeType": "INITIAL_CREATE",
        "changedAt": "2026-07-20T10:00:00Z",
        "reason": null
      }
    ]
  }
}
```

**Business logic** (R-ROLE-13 v1.8):

---

### 2.8. Initial Room State (sync khi vào phòng)

#### 2.8.1. `GET /api/v1/liverooms/{roomId}/state` — Full state để init UI

**Permission**: ACTIVE participant hoặc user có JoinRequest PENDING

**Response 200**:
```json
{
  "success": true,
  "data": {
    "room": { "id": "...", "roomCode": "...", ... },
    "participants": [
      { "id": "uuid", "userId": "uuid", "userEmail": "an@congty.com", "isOwner": true, "cameraOn": false, "micOn": false, "micState": "UNMUTED" }
    ],
    "owner": { "userId": "uuid", "userEmail": "an@congty.com", "isInRoom": true },
    "chat": {
      "messages": [ ... 200 messages mới nhất ... ],
      "hasMore": true,
      "nextCursor": "uuid"
    },
    "music": { "songId": null, "status": "PAUSED", "volumePercent": 80 },
    "annotations": [ ... current cycle ... ],
    "joinRequest": {
      "id": "uuid",
      "state": "PENDING",
      "createdAt": "..."
    },
    "serverTime": "2026-07-26T10:00:00Z"
  }
}
```

**Business logic**:
- Endpoint đầu tiên frontend gọi khi vào SC-06 ACTIVE
- Trả về TẤT CẢ state cần thiết cho UI
- Subscribe WS topic SAU khi nhận response

---

## 3. Error Code Reference

Mapping đầy đủ xem `liveroom-error-code-mapper.md`. Tổng hợp:

| HTTP | Code | Dùng khi |
|---|---|---|
| 400 | LIVEROOM_VALIDATION_FAILED | DTO validation fail |
| 400 | LIVEROOM_IDEMPOTENCY_KEY_REQUIRED | Thiếu Idempotency-Key |
| 400 | LIVEROOM_SELF_KICK_NOT_ALLOWED | Owner tự kick |
| 400 | LIVEROOM_CHAT_EMPTY | Chat content rỗng |
| 400 | LIVEROOM_CHAT_TOO_LONG | Chat > 500 chars |
| 400 | LIVEROOM_MUSIC_NOT_PLAYING | Không có bài đang phát |
| 400 | LIVEROOM_MUSIC_INVALID_VOLUME | Volume không 0-100 |
| 400 | LIVEROOM_ANNOTATION_EMPTY | Annotation rỗng |
| 400 | LIVEROOM_ANNOTATION_TOO_LONG | Annotation > 200 |
| 400 | LIVEROOM_ANNOTATION_INVALID_POSITION | Position không hợp lệ |
| 400 | LIVEROOM_GRACE_INVALID | Grace không 30-1800 |
| 401 | UNAUTHORIZED | Thiếu/invalid token |
| 403 | LIVEROOM_PRO_REQUIRED | User không PRO |
| 403 | LIVEROOM_NOT_OWNER | Không phải owner |
| 403 | LIVEROOM_NOT_IN_SESSION | Chưa ACTIVE |
| 403 | LIVEROOM_REQUEST_LOCKED | RejectCountByOwner ≥ 3 |
| 403 | LIVEROOM_ANNOTATION_NOT_APPROVED | Chưa was_approved |
| 403 | LIVEROOM_MUSIC_NOT_OWN_SONG | Song không thuộc user |
| 403 | LIVEROOM_MUSIC_OWNER_ABSENT | Owner leave, không cho music control |
| 403 | LIVEROOM_MIC_MUTE_COOLDOWN | Cố bật mic trong cooldown 30s |
| 404 | LIVEROOM_ROOM_NOT_FOUND | Room không tồn tại |
| 404 | LIVEROOM_REQUEST_NOT_FOUND | JoinRequest không tồn tại |
| 404 | LIVEROOM_PARTICIPANT_NOT_FOUND | Participant không tồn tại |
| 409 | LIVEROOM_ROOM_FULL | Phòng đầy |
| 409 | LIVEROOM_ROOM_ENDED | Phòng ENDED |
| 409 | LIVEROOM_ROOM_ALREADY_ENDED | Đã ENDED rồi |
| 409 | LIVEROOM_ROOM_NOT_ENDED | Phòng chưa ENDED (cho reopen) |
| 409 | LIVEROOM_DUPLICATE_REQUEST | Đã có PENDING request |
| 409 | LIVEROOM_REQUEST_NOT_PENDING | Request đã resolved |
| 409 | LIVEROOM_MULTI_TAB_CONFLICT | ACTIVE ở tab khác |
| 409 | LIVEROOM_UNDO_WINDOW_EXPIRED | Quá 5s undo |
| 409 | LIVEROOM_ROOM_NAME_DUPLICATE | Tên phòng trùng |
| 409 | LIVEROOM_ALREADY_PARTICIPANT | Đã ACTIVE |
| 409 | LIVEROOM_MUSIC_STATE_CONFLICT | Optimistic lock fail |
| 429 | LIVEROOM_BRUTE_FORCE_LIMIT | Rate limit room code |
| 429 | LIVEROOM_KICKED_COOLDOWN | Trong 5 min cooldown |
| 500 | LIVEROOM_INTERNAL_ERROR | Server error |

---

## 4. Transaction & Locking Strategy

### 4.1. Pessimistic lock (R-CAPACITY-02 v1.7)

Dùng cho các operation thay đổi `current_participant_count`:
- `approve`
- `rejoin`
- `leave`
- `kick`

```java
@Transactional
public void approveJoinRequest(UUID roomId, UUID requestId) {
    LiveRoom room = entityManager.find(
        LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE
    );
    // ... rest logic
}
```

### 4.2. Optimistic lock (R-MUSIC-10 v1.8)

Cho `PlaybackState`:
```java
@Entity
public class PlaybackState {
    @Version
    private Integer version;
}
```

OptimisticLockException → HTTP 409 `LIVEROOM_MUSIC_STATE_CONFLICT`.

### 4.3. Transaction scope

Mỗi endpoint có 1 `@Transactional` ở service level (không ở controller). Bao gồm:
- DB read + write
- WS broadcast (sau commit, dùng `TransactionSynchronizationManager.registerSynchronization`)

### 4.4. Idempotency

JoinRequest POST: dùng `Idempotency-Key` header (R-JOIN-07 v1.8):
- Backend check `liveroom_join_requests.idempotency_key`
- Nếu tồn tại và < 24h → return row cũ
- Nếu không → INSERT row mới

---

## 5. Validation Rules (tổng hợp)

| Field | Validation | Annotation |
|---|---|---|
| roomName | 1-100 chars, trim+lowercase normalize | `@NotBlank @Size(min=1, max=100) @Pattern` |
| roomCode | 6 chars A-Z0-9 | `@Pattern(regexp = "^[A-Z0-9]{6}$")` |
| maxParticipants | 1-7 | `@Min(1) @Max(7)` |
| ownerGraceSeconds | 30-1800 | `@Min(30) @Max(1800)` |
| chat.content | 1-500 chars, not blank | `@NotBlank @Size(min=1, max=500)` |
| annotation.content | 1-200 chars, not blank | `@NotBlank @Size(min=1, max=200)` |
| annotation.positionSeconds | 0.0 ≤ x ≤ song.duration | `@DecimalMin("0.0")` (custom check) |
| music.volumePercent | 0-100 | `@Min(0) @Max(100)` |
| music.positionSeconds | 0.0 ≤ x ≤ song.duration | `@DecimalMin("0.0")` |
| idempotencyKey | UUID format | `@Pattern(regexp = "^[0-9a-fA-F-]{36}$")` |

---

## 6. Out of Scope (POST-MVP)

- ❌ File upload (chỉ metadata)
- ❌ Voice message
- ❌ Video recording
- ❌ Search API
- ❌ Export chat/annotation
- ❌ Webhook cho external integration
- ❌ Admin API (chỉ dùng audit log + DB direct)

---

## 7. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **Data model**: `liveroom-data-model.md` v1.0
- **WebSocket**: `liveroom-ws-protocol.md` (sẽ tạo)
- **State machines**: `liveroom-state-machines.md` (sẽ tạo)
- **Concurrency**: `liveroom-concurrency.md` (sẽ tạo)
- **Error codes**: `liveroom-error-code-mapper.md`

---

**Người viết**: Senior Dev Team
**Reviewer**: Backend Lead, Frontend Lead, QA Lead
**Ngày review**: Pending
