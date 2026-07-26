# Live Room — WebSocket Protocol Specification

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-api-spec.md` v1.0
**Đối tượng đọc**: Backend Dev (Realtime Gateway), Frontend Dev, QA
**Mục đích**: Quy chuẩn STOMP WebSocket protocol cho module Live Room. Code backend VÀ frontend **BẮT BUỘC** tuân theo spec này.

---

## 1. Quy ước chung

### 1.1. Protocol

- **STOMP over WebSocket** (không dùng raw WebSocket)
- Spring Boot sử dụng `spring-boot-starter-websocket` + `@MessageMapping`
- Endpoint: `/ws` (config trong `WebSocketConfig`)
- Heartbeat: server gửi ping mỗi 30s, client phải pong trong 60s
- Subprotocol: `v12.stomp`

### 1.2. Authentication

- Client kết nối tới `/ws?token=<jwt>` (JWT trong query string)
- Server STOMP CONNECT frame xử lý authentication
- Server attach `userId` + `role` vào STOMP session attributes
- Nếu không hợp lệ → STOMP ERROR frame, disconnect

```javascript
// Frontend example
const socket = new SockJS('/ws?token=' + jwtToken);
const stompClient = Stomp.over(socket);
stompClient.connect({}, onConnect, onError);
```

### 1.3. Topic Path Convention (R-WS-SCOPE-02 v1.8)

| Topic | Subscribe | Mô tả |
|---|---|---|
| `/topic/liveroom/{roomId}` | Cả phòng | Generic event của phòng (join, leave, capacity, ...) |
| `/topic/liveroom/{roomId}/music` | ACTIVE trong phòng | Music playback events |
| `/topic/liveroom/{roomId}/chat` | ACTIVE trong phòng | Chat message events |
| `/topic/liveroom/{roomId}/annotations` | ACTIVE trong phòng | Annotation events |
| `/user/queue/liveroom/{roomId}/private` | User đã approve | Private event (REQUEST_APPROVED, ROOM_ENDED, KICKED) |

**Lưu ý**:
- Topic dùng `roomId` UUID (KHÔNG dùng roomCode)
- `/user/queue/...` là private queue (chỉ user đó nhận)

### 1.4. Send Destination Convention

| Destination | Method | Mô tả |
|---|---|---|
| `/app/liveroom/{roomId}/music/play` | R-MUSIC-09 | Chọn bài + phát |
| `/app/liveroom/{roomId}/music/pause` | Khuyến nghị | Pause |
| `/app/liveroom/{roomId}/music/seek` | Khuyến nghị | Seek position |
| `/app/liveroom/{roomId}/music/volume` | Khuyến nghị | Change volume |
| `/app/liveroom/{roomId}/music/get-state` | Khuyến nghị | Request current state (response qua topic) |

### 1.5. Subscription Scope Rule (R-WS-SCOPE-01 v1.8)

**Ai được subscribe**:
- `JoinRequest.state ∈ {PENDING, APPROVED}` (JoinRequest còn active)
- HOẶC `Participant.state ∈ {ACTIVE, RECONNECTING, OWNER_GRACE}` (đang trong phòng)

**Ai KHÔNG được subscribe**:
- `JoinRequest.state ∈ {REJECTED_BY_OWNER, REJECTED_BY_CAPACITY, CANCELLED, EXPIRED, LOCKED}`
- `Participant.state ∈ {KICKED, LEFT, ENDED, OFFLINE}`

**Enforcement**:
- Subscribe request → server check scope → reject với ERROR frame nếu không hợp lệ
- State change → server **CHỦ ĐỘNG unsubscribe** session

### 1.6. Common Headers

Mỗi STOMP message từ client kèm:
- `userId`: UUID (từ JWT)
- `roomId`: UUID (trong destination)
- `requestId`: UUID (per request, dùng để tracing)

Server broadcast message kèm:
- `timestamp`: ISO 8601 UTC
- `sequenceNumber`: long (cho ordering, R-MUSIC-10)
- `actorUserId`: UUID (người gây ra event)

### 1.7. Message Format

Tất cả event payload đều là JSON object:

```json
{
  "eventType": "PARTICIPANT_JOINED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 123,
  "data": { ... }
}
```

**Top-level fields**:
- `eventType` (string, required): enum event name
- `timestamp` (ISO 8601, required): server timestamp
- `sequenceNumber` (long, optional): monotonic cho ordering
- `data` (object, required): event-specific payload

### 1.8. Error Frame

Khi server reject (e.g., permission denied):

```
ERROR
content-type: application/json
message: Subscribe scope invalid

{
  "code": "WS_UNAUTHORIZED",
  "message": "You are not authorized to subscribe this topic"
}
```

---

## 2. Connect / Disconnect Lifecycle

### 2.1. CONNECT Frame

**Client gửi**:
```
CONNECT
accept-version:1.2
heart-beat:10000,30000
Authorization:Bearer <jwt>
```

**Server xử lý** (interceptor):
1. Validate JWT
2. Parse `userId`, `role`, `email`
3. Attach vào session attributes
4. CONNECTED frame success

### 2.2. DISCONNECT Frame

**Trigger**:
- Client close tab
- Network timeout (> 60s không heartbeat)
- Server force unsubscribe (R-WS-SCOPE-01)

**Server xử lý**:
- Set `Participant.state = RECONNECTING` (nếu user là participant)
- Schedule cleanup job (60s timeout → OFFLINE + remove participant)

### 2.3. Reconnect Logic

**Client side**:
- Auto-reconnect với exponential backoff (1s, 2s, 4s, 8s, max 30s)
- Re-subscribe topic sau khi reconnect
- Nếu reconnect fail > 5 lần → show "Mất kết nối" UI

**Server side**:
- Nếu session DISCONNECT rồi CONNECT lại với cùng userId → restore subscription

---

## 3. Event Catalog (Server → Client)

Format định nghĩa:
- **Event name**: string
- **Trigger**: khi nào server broadcast
- **Topic**: destination
- **Payload**: JSON schema
- **Receiver**: ai được nhận
- **i18n key**: message key cho client toast/notification

### 3.1. Room Lifecycle Events

#### 3.1.1. `ROOM_MANUAL_ENDED`

| Field | Value |
|---|---|
| Trigger | Owner click "End room" (R-END-01) |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` |
| Receiver | Tất cả ACTIVE + lobby user (PENDING/APPROVED) |
| i18n | LIVEROOM_ROOM_ENDED |

**Payload**:
```json
{
  "eventType": "ROOM_MANUAL_ENDED",
  "timestamp": "2026-07-26T11:00:00Z",
  "sequenceNumber": 100,
  "data": {
    "roomId": "uuid",
    "endedAt": "2026-07-26T11:00:00Z",
    "endedReason": "manual",
    "undoAvailableUntil": "2026-07-26T11:00:05Z"
  }
}
```

#### 3.1.2. `ROOM_REVIVED` (R-END-12 v1.8)

| Field | Value |
|---|---|
| Trigger | Owner click "Undo" trong 5s |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` |
| Receiver | Tất cả (ACTIVE + lobby) |
| i18n | LIVEROOM_ROOM_REVIVED |

**Payload**:
```json
{
  "eventType": "ROOM_REVIVED",
  "timestamp": "2026-07-26T11:00:03Z",
  "sequenceNumber": 101,
  "data": {
    "roomId": "uuid",
    "revivedAt": "2026-07-26T11:00:03Z",
    "revokedEndedAt": "2026-07-26T11:00:00Z"
  }
}
```

#### 3.1.3. `ROOM_AUTO_ENDED`

| Field | Value |
|---|---|
| Trigger | Grace expired hoặc empty timeout (R-END-08/09) |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` |
| Receiver | Tất cả (ACTIVE + lobby) |
| i18n | LIVEROOM_AUTO_ENDED_GRACE / LIVEROOM_AUTO_ENDED_EMPTY |

**Payload**:
```json
{
  "eventType": "ROOM_AUTO_ENDED",
  "timestamp": "2026-07-26T11:05:00Z",
  "sequenceNumber": 102,
  "data": {
    "roomId": "uuid",
    "endedAt": "2026-07-26T11:05:00Z",
    "endedReason": "owner_grace_expired | empty_timeout"
  }
}
```

#### 3.1.4. `ROOM_REOPENED`

| Field | Value |
|---|---|
| Trigger | Owner reopen (R-REOPEN-01) |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/{roomId}/private` |
| Receiver | Owner + participant cũ (was_approved) |
| i18n | LIVEROOM_ROOM_REOPENED |

**Payload**:
```json
{
  "eventType": "ROOM_REOPENED",
  "timestamp": "2026-07-26T12:00:00Z",
  "sequenceNumber": 103,
  "data": {
    "roomId": "uuid",
    "reopenedCount": 2,
    "previousEndedAt": "2026-07-25T10:00:00Z",
    "startedAt": "2026-07-26T12:00:00Z",
    "currentSessionCycleId": "uuid"
  }
}
```

### 3.2. Participant Events

#### 3.2.1. `PARTICIPANT_JOINED`

| Field | Value |
|---|---|
| Trigger | User vào phòng ACTIVE |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE participant |
| i18n | (no toast, just update UI) |

**Payload**:
```json
{
  "eventType": "PARTICIPANT_JOINED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 50,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "userEmail": "binh@congty.com",
    "isOwner": false,
    "joinedAt": "2026-07-26T10:00:00Z"
  }
}
```

#### 3.2.2. `PARTICIPANT_LEFT`

| Field | Value |
|---|---|
| Trigger | User tự leave (R-LEAVE-01) |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE participant |
| i18n | — |

**Payload**:
```json
{
  "eventType": "PARTICIPANT_LEFT",
  "timestamp": "2026-07-26T10:30:00Z",
  "sequenceNumber": 60,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "leftAt": "2026-07-26T10:30:00Z"
  }
}
```

#### 3.2.3. `PARTICIPANT_KICKED` (R-ADMIN-04 v1.8)

| Field | Value |
|---|---|
| Trigger | Owner kick user |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` cho user bị kick |
| Receiver | User bị kick + tất cả ACTIVE |
| i18n | LIVEROOM_PARTICIPANT_KICKED (cho participants), LIVEROOM_KICKED_BY_OWNER (cho user bị kick) |

**Payload** (cho room):
```json
{
  "eventType": "PARTICIPANT_KICKED",
  "timestamp": "2026-07-26T10:30:00Z",
  "sequenceNumber": 65,
  "actorUserId": "owner-uuid",
  "data": {
    "roomId": "uuid",
    "userId": "kicked-user-uuid",
    "kickedBy": "owner-uuid",
    "reason": "spam"
  }
}
```

**Payload** (cho user bị kick, gửi qua private queue):
```json
{
  "eventType": "PARTICIPANT_KICKED",
  "timestamp": "2026-07-26T10:30:00Z",
  "sequenceNumber": 65,
  "data": {
    "roomId": "uuid",
    "kickedAt": "2026-07-26T10:30:00Z",
    "kickedCooldownUntil": "2026-07-26T10:35:00Z",
    "remainingSeconds": 300,
    "reason": "spam"
  }
}
```

#### 3.2.4. `PARTICIPANT_MIC_MUTED_BY_OWNER` (R-ADMIN-05 v1.8)

| Field | Value |
|---|---|
| Trigger | Owner remote-mute user |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` cho user bị mute |
| Receiver | User bị mute + tất cả ACTIVE |
| i18n | LIVEROOM_MIC_MUTED_BY_OWNER |

**Payload**:
```json
{
  "eventType": "PARTICIPANT_MIC_MUTED_BY_OWNER",
  "timestamp": "2026-07-26T10:30:00Z",
  "sequenceNumber": 70,
  "actorUserId": "owner-uuid",
  "data": {
    "roomId": "uuid",
    "userId": "muted-user-uuid",
    "mutedBy": "owner-uuid",
    "mutedAt": "2026-07-26T10:30:00Z",
    "cooldownUntil": "2026-07-26T10:30:30Z",
    "cooldownRemainingSeconds": 30
  }
}
```

#### 3.2.5. `PARTICIPANT_MIC_UNMUTED` (R-MEDIA-10 v1.8)

| Field | Value |
|---|---|
| Trigger | User tự bật mic sau cooldown |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE |
| i18n | — |

**Payload**:
```json
{
  "eventType": "PARTICIPANT_MIC_UNMUTED",
  "timestamp": "2026-07-26T10:30:30Z",
  "sequenceNumber": 75,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "unmutedAt": "2026-07-26T10:30:30Z"
  }
}
```

#### 3.2.6. `PARTICIPANT_CONNECTION_CHANGED`

| Field | Value |
|---|---|
| Trigger | WS connect/disconnect |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE |
| i18n | — |

**Payload**:
```json
{
  "eventType": "PARTICIPANT_CONNECTION_CHANGED",
  "timestamp": "2026-07-26T10:35:00Z",
  "sequenceNumber": 80,
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "status": "RECONNECTING | OFFLINE",
    "timestamp": "2026-07-26T10:35:00Z"
  }
}
```

### 3.3. Owner Events

#### 3.3.1. `OWNER_LEFT` (R-LEAVE-04 + R-LEAVE-04.1 v1.8 — debounce 3s)

| Field | Value |
|---|---|
| Trigger | Owner leave (sau 3s debounce) |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` cho lobby |
| Receiver | Tất cả ACTIVE + lobby user |
| i18n | LIVEROOM_OWNER_LEFT (banner) |

**Payload**:
```json
{
  "eventType": "OWNER_LEFT",
  "timestamp": "2026-07-26T10:30:00Z",
  "sequenceNumber": 55,
  "actorUserId": "owner-uuid",
  "data": {
    "roomId": "uuid",
    "ownerId": "owner-uuid",
    "ownerLeftAt": "2026-07-26T10:30:00Z",
    "graceExpiresAt": "2026-07-26T10:31:00Z",
    "graceRemainingSeconds": 60
  }
}
```

#### 3.3.2. `OWNER_REJOINED`

| Field | Value |
|---|---|
| Trigger | Owner rejoin trong grace |
| Topic | `/topic/liveroom/{roomId}` + `/user/queue/liveroom/{roomId}/private` |
| Receiver | Tất cả ACTIVE + lobby |
| i18n | — |

**Payload**:
```json
{
  "eventType": "OWNER_REJOINED",
  "timestamp": "2026-07-26T10:30:30Z",
  "sequenceNumber": 56,
  "actorUserId": "owner-uuid",
  "data": {
    "roomId": "uuid",
    "ownerId": "owner-uuid",
    "rejoinedAt": "2026-07-26T10:30:30Z"
  }
}
```

#### 3.3.3. `OWNER_GRACE_EXPIRING`

| Field | Value |
|---|---|
| Trigger | 10s trước grace expiry |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE |
| i18n | LIVEROOM_OWNER_GRACE_EXPIRING |

**Payload**:
```json
{
  "eventType": "OWNER_GRACE_EXPIRING",
  "timestamp": "2026-07-26T10:30:50Z",
  "sequenceNumber": 58,
  "data": {
    "roomId": "uuid",
    "remainingSeconds": 10
  }
}
```

### 3.4. Capacity Events

#### 3.4.1. `ROOM_CAPACITY_CHANGED`

| Field | Value |
|---|---|
| Trigger | Participant join/leave/timeout/reopen |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | ACTIVE + lobby (PENDING/APPROVED) WO NOT REJECTED (R-WS-SCOPE-01) |
| i18n | — |

**Payload**:
```json
{
  "eventType": "ROOM_CAPACITY_CHANGED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 51,
  "data": {
    "roomId": "uuid",
    "currentCount": 5,
    "maxParticipants": 7,
    "reservedOwnerSlot": false,
    "effectiveMaxParticipants": 7
  }
}
```

#### 3.4.2. `CAPACITY_REACHED`

| Field | Value |
|---|---|
| Trigger | currentCount >= maxParticipants |
| Topic | `/topic/liveroom/{roomId}` (filtered: chỉ lobby user PENDING) |
| Receiver | Lobby user (PENDING) |
| i18n | LIVEROOM_ROOM_FULL |

**Payload**:
```json
{
  "eventType": "CAPACITY_REACHED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 52,
  "data": {
    "roomId": "uuid",
    "currentCount": 7,
    "maxParticipants": 7
  }
}
```

### 3.5. Join Request Events

#### 3.5.1. `JOIN_REQUEST_CREATED`

| Field | Value |
|---|---|
| Trigger | User gửi JoinRequest mới |
| Topic | `/user/queue/liveroom/{roomId}/private` (owner only) |
| Receiver | Owner |
| i18n | LIVEROOM_REQUEST_CREATED_NOTIFY (toast cho owner) |

**Payload**:
```json
{
  "eventType": "JOIN_REQUEST_CREATED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 40,
  "data": {
    "roomId": "uuid",
    "requestId": "uuid",
    "userId": "uuid",
    "userEmail": "binh@congty.com",
    "createdAt": "2026-07-26T10:00:00Z"
  }
}
```

#### 3.5.2. `JOIN_REQUEST_CANCELLED`

| Field | Value |
|---|---|
| Trigger | User tự hủy |
| Topic | `/user/queue/liveroom/{roomId}/private` (owner only) |
| Receiver | Owner |
| i18n | — |

**Payload**:
```json
{
  "eventType": "JOIN_REQUEST_CANCELLED",
  "timestamp": "2026-07-26T10:05:00Z",
  "sequenceNumber": 42,
  "data": {
    "roomId": "uuid",
    "requestId": "uuid",
    "userId": "uuid",
    "cancelledAt": "2026-07-26T10:05:00Z"
  }
}
```

#### 3.5.3. `REQUEST_APPROVED`

| Field | Value |
|---|---|
| Trigger | Owner approve |
| Topic | `/user/queue/liveroom/{roomId}/private` (user được approve) |
| Receiver | User đó |
| i18n | LIVEROOM_REQUEST_APPROVED |

**Payload**:
```json
{
  "eventType": "REQUEST_APPROVED",
  "timestamp": "2026-07-26T10:10:00Z",
  "sequenceNumber": 45,
  "data": {
    "roomId": "uuid",
    "requestId": "uuid",
    "autoJoin": true,
    "participantId": "uuid",
    "roomState": {
      "id": "uuid",
      "roomCode": "ABC123",
      "roomName": "Daily Sync",
      "ownerId": "uuid"
    }
  }
}
```

#### 3.5.4. `REQUEST_REJECTED_BY_OWNER` (R-JOIN-11 v1.8)

| Field | Value |
|---|---|
| Trigger | Owner reject user |
| Topic | `/user/queue/liveroom/{roomId}/private` (user bị reject) |
| Receiver | User đó |
| i18n | LIVEROOM_REJECTED_BY_OWNER |

**Payload**:
```json
{
  "eventType": "REQUEST_REJECTED_BY_OWNER",
  "timestamp": "2026-07-26T10:10:00Z",
  "sequenceNumber": 46,
  "data": {
    "roomId": "uuid",
    "requestId": "uuid",
    "reason": "OWNER_REJECT",
    "rejectCountByOwner": 1,
    "remainingBeforeLock": 2
  }
}
```

#### 3.5.5. `REQUEST_REJECTED_BY_CAPACITY` (R-JOIN-11 v1.8)

| Field | Value |
|---|---|
| Trigger | Phòng đầy khi approve (R-APPROVE-05) |
| Topic | `/user/queue/liveroom/{roomId}/private` (user bị reject) |
| Receiver | User đó |
| i18n | LIVEROOM_ROOM_FULL |

**Payload**:
```json
{
  "eventType": "REQUEST_REJECTED_BY_CAPACITY",
  "timestamp": "2026-07-26T10:10:00Z",
  "sequenceNumber": 47,
  "data": {
    "roomId": "uuid",
    "requestId": "uuid",
    "reason": "CAPACITY_FULL",
    "rejectCountByCapacity": 1
  }
}
```

#### 3.5.6. `REQUEST_LOCKED`

| Field | Value |
|---|---|
| Trigger | User chạm 3 lần rejectCountByOwner |
| Topic | `/user/queue/liveroom/{roomId}/private` |
| Receiver | User |
| i18n | LIVEROOM_REQUEST_LOCKED |

**Payload**:
```json
{
  "eventType": "REQUEST_LOCKED",
  "timestamp": "2026-07-26T10:10:00Z",
  "sequenceNumber": 48,
  "data": {
    "roomId": "uuid",
    "rejectCountByOwner": 3,
    "reason": "RATE_LIMIT"
  }
}
```

### 3.6. Media Events

#### 3.6.1. `MEDIA_STATE_CHANGED`

| Field | Value |
|---|---|
| Trigger | User bật/tắt camera/mic |
| Topic | `/topic/liveroom/{roomId}` |
| Receiver | Tất cả ACTIVE |
| i18n | — |

**Payload**:
```json
{
  "eventType": "MEDIA_STATE_CHANGED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 110,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "cameraOn": false,
    "micOn": true,
    "micState": "UNMUTED | SELF_MUTED | MUTED_BY_OWNER",
    "timestamp": "2026-07-26T10:00:00Z"
  }
}
```

### 3.7. Chat Events

#### 3.7.1. `CHAT_HISTORY_SNAPSHOT` (R-CHAT-05 v1.8)

| Field | Value |
|---|---|
| Trigger | User mới join ACTIVE |
| Topic | `/user/queue/liveroom/{roomId}/private` |
| Receiver | User join ACTIVE |
| i18n | — |

**Payload**:
```json
{
  "eventType": "CHAT_HISTORY_SNAPSHOT",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 1,
  "data": {
    "roomId": "uuid",
    "messages": [
      {
        "id": "uuid",
        "userId": "uuid",
        "userEmail": "binh@congty.com",
        "content": "Hello",
        "sentAt": "2026-07-26T10:00:00Z"
      }
    ],
    "hasMore": true,
    "nextCursor": "uuid",
    "totalInCycle": 250
  }
}
```

**Lưu ý**: 200 messages mới nhất (R-CHAT-05 v1.8). Pagination qua REST API.

#### 3.7.2. `CHAT_MESSAGE_RECEIVED`

| Field | Value |
|---|---|
| Trigger | User gửi chat message |
| Topic | `/topic/liveroom/{roomId}/chat` |
| Receiver | Tất cả ACTIVE (trừ user gửi) |
| i18n | — |

**Payload**:
```json
{
  "eventType": "CHAT_MESSAGE_RECEIVED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 200,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "messageId": "uuid",
    "userId": "uuid",
    "userEmail": "binh@congty.com",
    "content": "Hello everyone",
    "sentAt": "2026-07-26T10:00:00Z"
  }
}
```

### 3.8. Music Events (R-MUSIC-09, R-MUSIC-10 v1.8)

#### 3.8.1. `MUSIC_SONG_CHANGED`

| Field | Value |
|---|---|
| Trigger | User chọn bài mới |
| Topic | `/topic/liveroom/{roomId}/music` |
| Receiver | Tất cả ACTIVE |
| i18n | — |

**Payload**:
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

#### 3.8.2. `MUSIC_PLAYBACK_STATE_CHANGED`

| Field | Value |
|---|---|
| Trigger | Play/pause/seek/volume thay đổi |
| Topic | `/topic/liveroom/{roomId}/music` |
| Receiver | Tất cả ACTIVE |
| i18n | LIVEROOM_MUSIC_OWNER_PAUSED (khi owner leave) |

**Payload**:
```json
{
  "eventType": "MUSIC_PLAYBACK_STATE_CHANGED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 301,
  "actorUserId": "uuid",
  "data": {
    "roomId": "uuid",
    "status": "PLAYING | PAUSED",
    "currentPositionSeconds": 120.5,
    "volumePercent": 80,
    "lastUpdatedBy": "uuid",
    "lastUpdatedAt": "2026-07-26T10:00:00Z",
    "sequenceNumber": 301,
    "version": 42
  }
}
```

**Ordering rule** (R-MUSIC-10 v1.8):
- Client theo dõi `lastSequenceNumber` đã apply
- Event với `sequenceNumber <= lastSequenceNumber` → discard (out-of-order)
- Nếu 2 event cùng `sequenceNumber` → giữ event sau (last write wins)

### 3.9. Annotation Events

#### 3.9.1. `ANNOTATION_CREATED`

| Field | Value |
|---|---|
| Trigger | User tạo annotation |
| Topic | `/topic/liveroom/{roomId}/annotations` |
| Receiver | ACTIVE user was_approved trong current cycle |
| i18n | — |

**Payload**:
```json
{
  "eventType": "ANNOTATION_CREATED",
  "timestamp": "2026-07-26T10:00:00Z",
  "sequenceNumber": 400,
  "actorUserId": "uuid",
  "data": {
    "annotationId": "uuid",
    "roomId": "uuid",
    "roomSessionCycleId": "uuid",
    "songId": "uuid",
    "userId": "uuid",
    "userEmail": "an@congty.com",
    "positionSeconds": 120.5,
    "content": "đoạn này nhạc to quá",
    "createdAt": "2026-07-26T10:00:00Z"
  }
}
```

---

## 4. Client → Server Commands (STOMP Send)

### 4.1. Music Play

**Send destination**: `/app/liveroom/{roomId}/music/play`

**Headers**:
```
userId: uuid
roomId: uuid
requestId: uuid
```

**Body**:
```json
{
  "songId": "uuid"
}
```

**Server response**: 
- Success → broadcast `MUSIC_SONG_CHANGED` + `MUSIC_PLAYBACK_STATE_CHANGED` qua topic
- Failure → STOMP ERROR frame với code

**Error codes**:
- `WS_MUSIC_NOT_OWN_SONG`
- `WS_MUSIC_NOT_READY`
- `WS_MUSIC_OWNER_ABSENT` (R-MUSIC-11 v1.8)
- `WS_MUSIC_STATE_CONFLICT` (R-MUSIC-10 v1.8 — optimistic lock fail)
- `WS_NOT_IN_SESSION`

### 4.2. Music Pause

**Send destination**: `/app/liveroom/{roomId}/music/pause`

**Body**: `{}`

**Response**: broadcast `MUSIC_PLAYBACK_STATE_CHANGED` (status=PAUSED)

### 4.3. Music Seek

**Send destination**: `/app/liveroom/{roomId}/music/seek`

**Body**:
```json
{ "positionSeconds": 120.5 }
```

**Response**: broadcast `MUSIC_PLAYBACK_STATE_CHANGED`

**Validation**: `0 ≤ positionSeconds ≤ song.duration`

### 4.4. Music Volume

**Send destination**: `/app/liveroom/{roomId}/music/volume`

**Body**:
```json
{ "volumePercent": 80 }
```

**Response**: broadcast `MUSIC_PLAYBACK_STATE_CHANGED`

**Validation**: `0 ≤ volumePercent ≤ 100`

### 4.5. Music Get State

**Send destination**: `/app/liveroom/{roomId}/music/get-state`

**Body**: `{}`

**Response**: server gửi `MUSIC_PLAYBACK_STATE_CHANGED` qua topic `/topic/liveroom/{roomId}/music` (private cho user request)

**Use case**: Client nhận 409 MUSIC_STATE_CONFLICT → re-fetch state qua này

---

## 5. Reconnection & State Recovery

### 5.1. State Sync Flow

```
1. Client connect WS
2. Server CONNECTED, attach userId
3. Client subscribe topic + private queue
4. Client (optional) GET /api/v1/liverooms/{roomId}/state để init UI
5. Server broadcast events realtime
```

### 5.2. Reconnect Sync Flow

```
1. WS disconnect (network issue)
2. Client auto-reconnect sau 1s (exponential backoff)
3. Server restore session nếu cùng userId
4. Client re-subscribe topic
5. Client GET /api/v1/liverooms/{roomId}/state (full state) để catch up
6. Server broadcast events tiếp tục
```

**Quan trọng**: WS chỉ push incremental events. Khi reconnect, client **BẮT BUỘC** gọi REST state để ensure consistency.

### 5.3. Sequence Number Tracking

Mỗi event có `sequenceNumber` (R-MUSIC-10 v1.8). Client theo dõi `lastSequenceNumber` cho music events:
- Lưu `lastSequenceNumber` vào memory + localStorage (cho user rejoin)
- Event với `sequenceNumber <= lastSequenceNumber` → discard
- Event với `sequenceNumber > lastSequenceNumber` → apply + update lastSequenceNumber

---

## 6. Acknowledgment & Retry

### 6.1. Client ACK

STOMP có 2 chế độ:
- **AUTO ACK** (default): server gửi ngay khi broadcast
- **CLIENT ACK**: client phải gửi ACK frame

**Recommendation**: AUTO ACK cho MVP. Client retry qua REST API khi WS fail.

### 6.2. Retry Strategy

Client KHÔNG tự retry khi nhận event fail (vì event là 1 chiều). Còn với send (music control):
- Nếu ERROR với `WS_MUSIC_STATE_CONFLICT` → re-fetch state, KHÔNG retry
- Nếu timeout (no response) → giữ state optimistic, server sẽ broadcast event

---

## 7. Multi-Tab Conflict (R-JOIN-10 v1.8)

### 7.1. Detection

- Frontend dùng `BroadcastChannel API` (cùng origin) + `localStorage`
- Tab 1 active → lưu `localStorage.activeRoomSession = {roomId, sessionId}`
- Tab 2 mở cùng room → check `localStorage.activeRoomSession` → conflict
- Tab 2 ping qua `BroadcastChannel` → Tab 1 response pong

### 7.2. UI

- Tab 2 thấy warning screen "Bạn đang ở phòng này ở tab khác"
- 2 button: "Chuyển sang tab kia" + "Đóng tab này"
- KHÔNG cho phép auto-kick

### 7.3. Server-side

- Backend KHÔNG tự unsubscribe tab cũ
- WS scope (R-WS-SCOPE-01) áp dụng cho từng session (mỗi tab 1 session)

---

## 8. Security

### 8.1. Authentication

- JWT token validate ở CONNECT frame
- Token expire → server send ERROR + disconnect
- Frontend phải refresh token TRƯỚC khi WS expire

### 8.2. Authorization

- Subscribe: check scope (R-WS-SCOPE-01)
- Send: check participant.state = ACTIVE (cho music control, chat)
- Private queue: chỉ user tương ứng nhận

### 8.3. Rate Limiting

- Music send: per-user max 10 messages/giây
- Subscribe: max 10 topics/giây
- Reconnect: max 5 lần/phút

### 8.4. Content Sanitization

- Chat content: HTML escape trước khi broadcast (XSS prevention)
- Annotation: HTML escape
- User email: validate format trước khi echo

---

## 9. Monitoring & Metrics

### 9.1. Metrics cần track

- **Active connections**: số WS session đang active
- **Messages/giây**: throughput
- **Disconnect rate**: % disconnect/giờ
- **Subscribe scope rejection**: % ERROR frame do scope
- **Music state conflict**: số conflict/giây (R-MUSIC-10)
- **Sequence number gap**: max gap trong 1 session

### 9.2. Logging

- Log mỗi CONNECT/DISCONNECT (with userId, IP)
- Log mỗi SUBSCRIBE (validate scope)
- Log mỗi send MUSIC_* (audit)
- KHÔNG log chat content (privacy)

### 9.3. Alerting

- Nếu `active_connections > 10K` → alert
- Nếu `disconnect_rate > 5%` → alert
- Nếu `MUSIC_STATE_CONFLICT > 10/giây` → alert (quá nhiều race)

---

## 10. Compatibility & Versioning

### 10.1. Backward compatibility

- Mỗi event có version field (optional, thêm từ v1.1)
- Field mới được THÊM vào payload (không xoá field cũ)
- Breaking change → version bump event name

### 10.2. Client version

- Header `X-Client-Version` ở CONNECT
- Server reject client quá cũ (nếu cần)

---

## 11. Edge Cases

### 11.1. Owner leave + rejoin trong 3s (R-LEAVE-04.1 v1.8)

- Server buffer `OWNER_LEFT` event trong 3s
- Nếu owner rejoin trong 3s → discard buffer
- Sau 3s mới broadcast

### 11.2. 2 music control events trong 100ms (R-MUSIC-10 v1.8)

- Server apply optimistic lock
- 1 success, 1 fail với 409 MUSIC_STATE_CONFLICT
- Client nhận 409 → re-fetch state

### 11.3. Client disconnect giữa session

- Server set participant.state = RECONNECTING
- Schedule cleanup job 60s
- Nếu reconnect trong 60s → restore
- Nếu không → set OFFLINE + remove slot

### 11.4. Owner force-end vì downgrade (R-ROLE-06)

- Server broadcast `ROOM_AUTO_ENDED` với `reason: "force_role_change"`
- Lưu ownership history (R-ROLE-13 v1.8)

---

## 12. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **Data model**: `liveroom-data-model.md` v1.0
- **API**: `liveroom-api-spec.md` v1.0
- **State machines**: `liveroom-state-machines.md` (sẽ tạo)
- **Concurrency**: `liveroom-concurrency.md` (sẽ tạo)
- **i18n keys**: `liveroom-i18n-keys.md` (sẽ tạo)

---

**Người viết**: Senior Dev Team
**Reviewer**: Realtime Gateway Lead, Frontend Lead, QA Lead
**Ngày review**: Pending
