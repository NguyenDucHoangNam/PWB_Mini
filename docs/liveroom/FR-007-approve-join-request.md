# FR-007: Duyệt/Từ Chối Join Request

## 1. Mô tả

Cho phép **Host** duyệt hoặc từ chối request tham gia phòng PRIVATE từ user khác.

---

## 2. UI Layout

### Host Page: `/dashboard/live-rooms/{roomCode}`

```
┌─────────────────────────────────────────────────────────────────────┐
│  DashboardLayout                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ Header: "Meeting with Team"                    [End Session]  │ │
│  │ Room Code: ABC123 [Copy]                      [Share Link]   │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ┌─────────────────────────────────────┐ ┌───────────────────────┐ │
│  │                                     │ │ Sidebar                  │ │
│  │  MediaStage                         │ │ ┌─────────────────────┐│ │
│  │                                     │ │ │ Join Requests (2)   ││ │
│  │  ┌─────────┐ ┌─────────┐         │ │ │ ┌─────────────────┐ ││ │
│  │  │ Local   │ │ Remote  │         │ │ │ │ 🟡 John        │ ││ │
│  │  │ Video   │ │ Video   │         │ │ │ │ "Hi, join me!" │ ││ │
│  │  │ [👤]   │ │ [👤]   │         │ │ │ │ 2 mins ago    │ ││ │
│  │  └─────────┘ └─────────┘         │ │ │ │ [✓] [✗]       │ ││ │
│  │                                     │ │ │ └─────────────────┘ ││ │
│  │  ┌─────────────────────────────────┐│ │ │ ┌─────────────────┐ ││ │
│  │  │ [🎤] [📹] [🔊] [Device]      ││ │ │ │ 🟡 Jane        │ ││ │
│  │  │               [Leave]          ││ │ │ │ "Can I join?"  │ ││ │
│  │  └─────────────────────────────────┘│ │ │ │ 5 mins ago    │ ││ │
│  │                                     │ │ │ │ [✓] [✗]       │ ││ │
│  │                                     │ │ │ └─────────────────┘ ││ │
│  └─────────────────────────────────────┘ │ └─────────────────────┘│ │
│                                          │                          │ │
│                                          │ ┌─────────────────────┐│ │
│                                          │ │ Participants (3)    ││ │
│                                          │ │ • You (Host)         ││ │
│                                          │ │ • Alice              ││ │
│                                          │ │ • Bob                ││ │
│                                          │ └─────────────────────┘│ │
│                                          └───────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### Join Request Queue Panel

```
┌────────────────────────────────────────┐
│  Join Requests (2)                     │
│  ──────────────────────────────────── │
│                                        │
│  ┌────────────────────────────────┐  │
│  │ 🟡 John                         │  │
│  │ ─────────────────────────────── │  │
│  │ "Hi, I'd like to join the      │  │
│  │ meeting. Looking forward to it!" │  │
│  │                                  │  │
│  │ Requested: 2 mins ago           │  │
│  │                                  │  │
│  │  [✓ Approve]    [✗ Decline]   │  │
│  └────────────────────────────────┘  │
│                                        │
│  ┌────────────────────────────────┐  │
│  │ 🟡 Jane                         │  │
│  │ ─────────────────────────────── │  │
│  │ "Can I join?"                  │  │
│  │                                  │  │
│  │ Requested: 5 mins ago           │  │
│  │                                  │  │
│  │  [✓ Approve]    [✗ Decline]   │  │
│  └────────────────────────────────┘  │
│                                        │
└────────────────────────────────────────┘
```

### Decline Request Dialog

```
┌─────────────────────────────────────┐
│                                       │
│  Decline John's Request?             │
│                                       │
│  Optionally provide a reason:        │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │                                 │ │
│  │ [Room is currently full.        │ │
│  │ Try again later.]               │ │
│  │                                 │ │
│  └─────────────────────────────────┘ │
│  (max 500 characters)                │
│                                       │
│  ┌───────────────┐ ┌───────────────┐│
│  │    Cancel     │ │   Decline     ││
│  │    (gray)     │ │   (red)       ││
│  └───────────────┘ └───────────────┘│
│                                       │
└─────────────────────────────────────┘
```

### Toast Notification (New Request)

```
┌─────────────────────────────────────┐
│  🔔 New Join Request                │
│     John wants to join the room      │
│                                       │
│  [View] [Dismiss]                   │
└─────────────────────────────────────┘
```

### Empty State

```
┌────────────────────────────────────────┐
│  Join Requests (0)                     │
│  ──────────────────────────────────── │
│                                        │
│  ┌────────────────────────────────┐  │
│  │                                  │  │
│  │        📭 No pending requests   │  │
│  │                                  │  │
│  │   When someone asks to join,     │  │
│  │   you'll see their request here  │  │
│  │                                  │  │
│  └────────────────────────────────┘  │
│                                        │
└────────────────────────────────────────┘
```

---

## 3. API Endpoints (Backend)

### GET `/api/v1/live-rooms/{roomCode}/join-requests`

**Authorization**: Role `PRO` (host)

**Query Parameters**:
| Param | Type | Mô tả |
|-------|------|--------|
| `status` | `JoinRequestStatus` | Filter: PENDING, APPROVED, REJECTED, CANCELLED (optional, default: PENDING) |

**Response**: `List<LiveRoomJoinRequestResponse>`

### POST `/api/v1/live-rooms/{roomCode}/join-requests/{requestId}/approve`

**Request Body** (`JoinRequestDecisionRequest`):
```json
{
  "reason": "Welcome to the room!"  // optional, max 500 chars
}
```

### POST `/api/v1/live-rooms/{roomCode}/join-requests/{requestId}/reject`

**Request Body** (`JoinRequestDecisionRequest`):
```json
{
  "reason": "Room is full for now"  // optional
}
```

---

## 4. Backend Flow - Approve

```java
// LiveRoomJoinRequestServiceImpl.approve()
@Transactional
public LiveRoomJoinRequest approve(UUID hostUserId, UUID requestId, String reason) {

    // 1. Load request (verify host ownership)
    LiveRoomJoinRequestJpaEntity entity = loadRequestAsHost(hostUserId, requestId);
    LiveRoomJoinRequest domain = joinRequestMapper.toDomain(entity);

    // 2. Approve request
    domain.approve(hostUserId, reason);

    // 3. Save request status
    joinRequestJpaRepository.save(joinRequestMapper.toEntity(domain, entity));

    // 4. Promote to participant
    promoteToParticipant(hostUserId, domain);

    // 5. Publish event → Push to requester
    eventPublisher.publishEvent(new JoinRequestDecidedEvent(...));
}
```

### Promote to Participant

```java
private void promoteToParticipant(UUID hostUserId, LiveRoomJoinRequest domain) {
    // 1. Check not already participant
    Optional<existing> = participantJpaRepository
        .findActiveByRoomAndUser(roomCode, userId);

    // 2. Check room capacity
    LiveRoomJpaEntity room = liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode);
    if (room.getCurrentParticipantCount() >= room.getMaxParticipants()) {
        throw LIVEROOM_FULL;
    }

    // 3. Create participant
    LiveRoomParticipant participant = LiveRoomParticipant.join(...);
    participantJpaRepository.save(participant);

    // 4. Increment room count
    LiveRoom domain = liveRoomMapper.toDomain(room);
    domain.incrementParticipants();

    // 5. Publish event → Broadcast PARTICIPANT_JOINED
}
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/join-requests.ts` | `listJoinRequests()`, `approveJoinRequest()`, `rejectJoinRequest()` + hooks |
| `components/join-request-queue-panel.tsx` | Host panel UI |
| `components/decline-request-dialog.tsx` | Decline confirmation |

### Component: `JoinRequestQueuePanel`

```tsx
const JoinRequestQueuePanel = ({ roomCode }: { roomCode: string }) => {
  const listRequests = useListJoinRequests({ roomCode, status: "PENDING" });
  const approveRequest = useApproveJoinRequest();
  const rejectRequest = useRejectJoinRequest();

  const handleApprove = (requestId: string) => {
    approveRequest.mutate({ roomCode, requestId });
  };

  const handleReject = (requestId: string) => {
    showDeclineDialog(requestId);
  };

  return (
    <div>
      {listRequests.data?.data.map((req) => (
        <div key={req.id}>
          <span>{req.displayName}</span>
          <span>{req.message}</span>
          <button onClick={() => handleApprove(req.id)}>Approve</button>
          <button onClick={() => handleReject(req.id)}>Decline</button>
        </div>
      ))}
    </div>
  );
};
```

---

## 6. WebSocket Events

### Subscribe: `/topic/room/{roomCode}/join-requests`

```typescript
interface JoinRequestCreatedWsEvent {
  type: "JOIN_REQUEST_CREATED";
  roomCode: string;
  requestId: string;
  requesterUserId: string;
  displayName: string;
  message: string;
  timestamp: string;
}
```

**Host receives** → Toast notification + Update request list

---

## 7. Request Status Transitions

```
PENDING ──approve──→ APPROVED ──→ User promoted to participant
   │
   └──reject──→ REJECTED

PENDING ──cancel──→ CANCELLED (by requester)
```

---

## 8. Business Rules

| Rule | Chi tiết |
|------|----------|
| **Authorization** | Chỉ host mới duyệt/từ chối |
| **Capacity check** | Không approve nếu phòng đầy |
| **Idempotency** | Không approve request đã xử lý |
| **Notification** | Push event đến requester qua WebSocket |

---

## 9. Related Documentation

- [FR-006: Join Private Room](./FR-006-join-private-room.md)
- [FR-010: Participant Management](./FR-010-participant-management.md)
- [FR-011: Realtime Events](./FR-011-realtime-events.md)
