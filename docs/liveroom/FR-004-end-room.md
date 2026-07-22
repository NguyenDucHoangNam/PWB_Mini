# FR-004: Kết Thúc Phòng

## 1. Mô tả

Cho phép **Host** kết thúc một phòng live. Khi kết thúc:
- Tất cả participants bị disconnect
- Room status chuyển sang `ENDED`
- Tất cả participant records được mark `leftAt`

---

## 2. UI Layout

### Dialog: `RoomEndDialog`

```
┌─────────────────────────────────────┐
│                                     │
│   End Live Room?                    │
│                                     │
│   Are you sure you want to end     │
│   "Meeting with Team"?               │
│                                     │
│   This will remove all participants  │
│   and end the session.              │
│                                     │
│   ┌─────────────┐ ┌─────────────┐  │
│   │   Cancel    │ │  End Room   │  │
│   │   (gray)    │ │   (red)     │  │
│   └─────────────┘ └─────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

### Trigger Location

```
┌────────────────────────────────────────┐
│  RoomCard (Dashboard)                  │
│  ┌──────────────────────────────────┐ │
│  │ Title: Meeting Team    [Manage]   │ │
│  │ Status: ACTIVE         [End Room] │ │
│  └──────────────────────────────────┘ │
│                                        │
│  Host Page: /dashboard/live-rooms/:code│
│  ┌──────────────────────────────────┐ │
│  │ Header: [End Session] button      │ │
│  └──────────────────────────────────┘ │
└────────────────────────────────────────┘
```

### Confirmation Flow

```
Click "End Room"
    ↓
Show RoomEndDialog
    ↓
User clicks "Cancel" → Close dialog
User clicks "End Room" → Proceed
    ↓
API call endRoom()
    ↓
Success → Redirect to /dashboard/live-rooms
Error → Show error toast
```

---

## 3. API Endpoints (Backend)

### POST `/api/v1/live-rooms/{roomCode}/end`

**Authorization**: Role `PRO` (phải là host của phòng)

**Path Parameters**:
| Param | Type | Mô tả |
|-------|------|--------|
| `roomCode` | string | Mã phòng 6 ký tự |

**Response**:
```json
{
  "success": true,
  "data": null,
  "message": "Live room ended successfully"
}
```

**Error Codes**:
| Code | HTTP | Mô tả |
|------|------|--------|
| `LIVEROOM_NOT_FOUND` | 404 | Phòng không tồn tại |
| `LIVEROOM_NOT_HOST` | 403 | User không phải host |
| `LIVEROOM_ALREADY_ENDED` | 409 | Phòng đã kết thúc |

---

## 4. Backend Flow

```java
// LiveRoomServiceImpl.endRoom()
@Transactional
public void endRoom(UUID hostUserId, String roomCode) {
    // 1. Load room với pessimistic lock
    LiveRoomJpaEntity entity = liveRoomJpaRepository
        .findByRoomCodeForUpdate(roomCode)
        .orElseThrow(LIVEROOM_NOT_FOUND);

    // 2. Verify host
    if (!entity.getHostUserId().equals(hostUserId)) {
        throw LIVEROOM_NOT_HOST;
    }

    // 3. Verify not ended
    if (entity.getStatus() == LiveRoomStatus.ENDED) {
        throw LIVEROOM_ALREADY_ENDED;
    }

    // 4. Update status
    LiveRoom domain = liveRoomMapper.toDomain(entity);
    domain.markEnded();  // status=ENDED, endedAt=now, currentParticipantCount=0

    // 5. Mark all participants as left
    participantJpaRepository.markAllLeftByRoom(roomCode, Instant.now());
}
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/rooms.ts` | `endRoom()`, `useEndRoom()` |
| `components/room-delete-dialog.tsx` | Confirmation dialog |
| `components/room-card.tsx` | End button trigger |

### Dialog: `RoomEndDialog`

```tsx
const { mutate: endRoom, isPending } = useEndRoom();

const handleConfirm = () => {
  endRoom(
    { roomCode: room.roomCode },
    {
      onSuccess: () => {
        onClose();
        router.push("/dashboard/live-rooms");
      },
    }
  );
};
```

### UI Flow

```
Click "End" button
    ↓
Show confirmation dialog
    ↓
Confirm → useEndRoom()
    ↓
Success → redirect to /dashboard/live-rooms
```

---

## 6. Side Effects

| Effect | Description |
|--------|-------------|
| **Participants** | All participants marked as `leftAt = now` |
| **Room status** | Changed to `ENDED` |
| **Participant count** | Reset to 0 |
| **WebSocket** | Topic `/topic/room/{roomCode}/participants` broadcast `PARTICIPANT_LEFT` |
| **Redirect** | Participants redirected to landing page |

---

## 7. Business Rules

| Rule | Chi tiết |
|------|----------|
| **Authorization** | Chỉ host mới được kết thúc phòng |
| **Idempotency** | Gọi lại sau khi ended → trả 409 |
| **Participant cleanup** | Tất cả participants được mark left |
| **Data retention** | Room vẫn còn trong DB, chỉ status=ENDED |

---

## 8. Related Documentation

- [FR-001: Create Room](./FR-001-create-room.md)
- [FR-003: Get Room Details](./FR-003-get-room.md)
- [FR-008: Leave Room](./FR-008-leave-room.md)
