# FR-006: Request Tham Gia Phòng Private

## 1. Mô tả

Cho phép user gửi request để tham gia phòng **PRIVATE**. Host sẽ nhận được notification và có thể approve/reject.

---

## 2. UI Layout

### Ask To Join Form

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Join Live Room"              [← Back]     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  🔒 PRIVATE ROOM                │       │   │
│  │              │                                   │       │   │
│  │              │  "Meeting with Team"              │       │   │
│  │              │  Host: John                       │       │   │
│  │              │  🔓 PUBLIC | 2/5 people          │       │   │
│  │              │                                   │       │   │
│  │              │  ─────────────────────────────── │       │   │
│  │              │                                   │       │   │
│  │              │  ┌─────────────────────────────┐ │       │   │
│  │              │  │ Display Name *             │ │       │   │
│  │              │  │ [John Doe___________]      │ │       │   │
│  │              │  └─────────────────────────────┘ │       │   │
│  │              │                                   │       │   │
│  │              │  ┌─────────────────────────────┐ │       │   │
│  │              │  │ Message (optional)           │ │       │   │
│  │              │  │ [Hi, I'd like to join___]  │ │       │   │
│  │              │  │ [_________________________] │ │       │   │
│  │              │  │                             │ │       │   │
│  │              │  │ max 500 characters          │ │       │   │
│  │              │  └─────────────────────────────┘ │       │   │
│  │              │                                   │       │   │
│  │              │       [Ask to Join]              │       │   │
│  │              │                                   │       │   │
│  │              └───────────────────────────────┘       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Waiting State

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Waiting for Approval"          [← Back]     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  ⏳                             │       │   │
│  │              │                                   │       │   │
│  │              │  Waiting for approval...         │       │   │
│  │              │                                   │       │   │
│  │              │  Your request is being reviewed  │       │   │
│  │              │  by the host.                   │       │   │
│  │              │                                   │       │   │
│  │              │  ─────────────────────────────── │       │   │
│  │              │                                   │       │   │
│  │              │  Waiting: 0:45                    │       │   │
│  │              │  ████████░░░░░░░░░░░░          │       │   │
│  │              │                                   │       │   │
│  │              │       [Cancel Request]            │       │   │
│  │              │                                   │       │   │
│  │              └───────────────────────────────┘       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Rejected State

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Request Declined"            [← Back]       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  ❌                             │       │   │
│  │              │                                   │       │   │
│  │              │  Request Declined                │       │   │
│  │              │                                   │       │   │
│  │              │  Sorry, the host has declined   │       │   │
│  │              │  your request.                  │       │   │
│  │              │                                   │       │   │
│  │              │  ─────────────────────────────── │       │   │
│  │              │                                   │       │   │
│  │              │  Reason:                         │       │   │
│  │              │  "Room is full for now"          │       │   │
│  │              │                                   │       │   │
│  │              │       [Ask Again]                │       │   │
│  │              │       [Back to Home]             │       │   │
│  │              │                                   │       │   │
│  │              └───────────────────────────────┘       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Timer Component

```
┌────────────────────────────────────────┐
│  ⏱ Waiting: 02:34                      │
│                                         │
│  Progress bar (optional):               │
│  [████████████░░░░░░░░░░] 45%          │
│                                         │
│  Auto-refresh every second              │
│  Realtime update via WebSocket          │
└────────────────────────────────────────┘
```

---

## 3. API Endpoints (Backend)

### POST `/api/v1/live-rooms/{roomCode}/join-requests`

**Authorization**: Role `USER`, `PRO`, hoặc `ADMIN`

**Request Body** (`CreateJoinRequestRequest`):
```json
{
  "displayName": "John Doe",     // optional, 1-100 chars, default: "guest-{userId_prefix}"
  "message": "Hi, I'd like to join" // optional, max 500 chars
}
```

**Response** (`LiveRoomJoinRequestResponse`):
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "roomCode": "ABC123",
    "userId": "user-uuid",
    "displayName": "John Doe",
    "message": "Hi, I'd like to join",
    "status": "PENDING",
    "decisionReason": null,
    "decidedByUserId": null,
    "decidedAt": null,
    "createdAt": "2026-07-22T10:00:00Z"
  },
  "message": "Join request created"
}
```

**Error Codes**:
| Code | HTTP | Mô tả |
|------|------|--------|
| `LIVEROOM_NOT_FOUND` | 404 | Phòng không tồn tại |
| `LIVEROOM_ALREADY_ENDED` | 409 | Phòng đã kết thúc |
| `LIVEROOM_JOIN_REQUEST_INVALID_DECISION` | 409 | Host không thể tự join phòng mình |

---

## 4. Backend Flow

```java
// LiveRoomJoinRequestServiceImpl.createOrReturnPending()
@Transactional
public LiveRoomJoinRequest createOrReturnPending(
    UUID userId, String roomCode, String displayName, String message) {

    // 1. Load room
    LiveRoomJpaEntity room = liveRoomJpaRepository
        .findByRoomCodeForUpdate(roomCode)
        .orElseThrow(LIVEROOM_NOT_FOUND);

    // 2. Verify room active
    if (room.getStatus() != LiveRoomStatus.ACTIVE) {
        throw LIVEROOM_ALREADY_ENDED;
    }

    // 3. Verify not host
    if (room.getHostUserId().equals(userId)) {
        throw LIVEROOM_JOIN_REQUEST_INVALID_DECISION;
    }

    // 4. Check existing pending request
    Optional<existing> = joinRequestJpaRepository
        .findActiveByRoomAndUser(roomCode, userId, PENDING);
    if (existing.isPresent()) {
        return existing.get(); // Return existing instead of creating new
    }

    // 5. Create new request
    LiveRoomJoinRequest request = LiveRoomJoinRequest.create(...);
    joinRequestJpaRepository.save(request);

    // 6. Publish event → Broadcast to host
    eventPublisher.publishEvent(new JoinRequestCreatedEvent(...));
}
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/join-requests.ts` | `createJoinRequest()`, `useCreateJoinRequest()` |
| `components/ask-to-join-card.tsx` | Form UI |
| `components/waiting-room-card.tsx` | Waiting state UI |
| `components/rejected-card.tsx` | Rejected state UI |
| `schemas/room-schema.ts` | `askToJoinFormSchema` |

### Component: `AskToJoinCard`

```tsx
const AskToJoinCard = ({ roomCode, roomMode }: Props) => {
  const createRequest = useCreateJoinRequest();

  const onSubmit = (values: AskToJoinFormValues) => {
    createRequest.mutate(
      { roomCode, data: values },
      {
        onSuccess: () => {
          setPhase("WAITING");
        },
      }
    );
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register("displayName")} />
      <textarea {...register("message")} />
      <button type="submit">Ask to Join</button>
    </form>
  );
};
```

### Validation Schema

```typescript
const askToJoinFormSchema = z.object({
  displayName: z.string().min(1).max(100),
  message: z.string().max(500).optional(),
});
```

---

## 6. Join Request Status Flow

```
                    ┌─ status=PENDING ──→ WaitingRoomCard
                    │
createJoinRequest ──┼─ status=APPROVED ──→ MediaStage (join room)
                    │
                    └─ status=REJECTED ──→ RejectedCard
```

### 6.1 Waiting State

**Component**: `WaitingRoomCard`

- Hiển thị timer đếm thời gian chờ
- Subscribe `JOIN_REQUEST_DECIDED` event qua WebSocket
- Nút Cancel → `useCancelJoinRequest()`

### 6.2 Rejected State

**Component**: `RejectedCard`

- Hiển thị reason (nếu có)
- Nút "Ask Again" → gửi request mới
- Nút "Back" → về trang trước

---

## 7. WebSocket Events

### Subscribe: `/user/queue/join-requests`

```typescript
interface JoinRequestDecidedWsEvent {
  type: "JOIN_REQUEST_DECIDED";
  roomCode: string;
  requestId: string;
  status: "APPROVED" | "REJECTED" | "CANCELLED";
  reason: string;
  timestamp: string;
}
```

---

## 8. Join Request Status Enum

```java
public enum JoinRequestStatus {
    PENDING,    // Đang chờ duyệt
    APPROVED,   // Được duyệt
    REJECTED,   // Bị từ chối
    CANCELLED;  // User hủy request
}
```

---

## 9. Related Documentation

- [FR-007: Approve/Reject Join Request](./FR-007-approve-join-request.md)
- [FR-005: Join Public Room](./FR-005-join-public-room.md)
- [FR-011: Realtime Events](./FR-011-realtime-events.md)
