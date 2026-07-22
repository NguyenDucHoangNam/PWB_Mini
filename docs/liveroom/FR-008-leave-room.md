# FR-008: Rời Phòng

## 1. Mô tả

Cho phép participant rời khỏi phòng live. Host rời phòng sẽ không kết thúc phòng (phòng vẫn tiếp tục với participants còn lại).

---

## 2. UI Layout

### Leave Confirmation (Optional)

```
┌─────────────────────────────────────┐
│                                       │
│  Leave the room?                    │
│                                       │
│  Are you sure you want to leave     │
│  "Meeting with Team"?                │
│                                       │
│  ┌───────────────┐ ┌───────────────┐│
│  │    Stay      │ │    Leave      ││
│  │    (gray)     │ │    (red)      ││
│  └───────────────┘ └───────────────┘│
│                                       │
└─────────────────────────────────────┘
```

### Media Controls - Leave Button

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                    MediaStage (Video Grid)                   │
│                                                             │
│                                                             │
│                                                             │
│                                                             │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                      │   │
│  │   [🎤]  [📹]  [🔊]  [🎛]           [Leave Room]  │   │
│  │   Mic     Cam    Spk    Devices         (red btn)   │   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Leave Button States

| State | Button Style |
|-------|--------------|
| Default | Gray outline |
| Hover | Red outline |
| Active/Loading | Red filled + spinner |
| Disabled | Gray, cursor not-allowed |

### Post-Leave Redirect

```
Leave clicked
    ↓
API: POST /leave
    ↓
Cleanup: Close WebRTC, stop stream, disconnect WS
    ↓
Redirect to /dashboard/live-rooms
```

---

## 3. API Endpoints (Backend)

### POST `/api/v1/live-rooms/{roomCode}/leave`

**Authorization**: Role `USER`, `PRO`, hoặc `ADMIN`

**Path Parameters**:
| Param | Type | Mô tả |
|-------|------|--------|
| `roomCode` | string | Mã phòng 6 ký tự |

**Response**:
```json
{
  "success": true,
  "data": null,
  "message": "Left the room successfully"
}
```

---

## 4. Backend Flow

```java
// LiveRoomParticipantServiceImpl.leaveRoom()
@Transactional
public Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode) {

    // 1. Find active participant
    Optional<LiveRoomParticipantJpaEntity> existing =
        participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);

    if (existing.isEmpty()) {
        return Optional.empty(); // Not joined
    }

    // 2. Mark left
    LiveRoomParticipantJpaEntity participant = existing.get();
    participant.setLeftAt(now);
    participantJpaRepository.save(participant);

    // 3. Decrement room participant count
    LiveRoomJpaEntity room = liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode);
    LiveRoom domain = liveRoomMapper.toDomain(room);
    domain.decrementParticipants();

    // 4. Publish event → Broadcast PARTICIPANT_LEFT
    eventPublisher.publishEvent(new ParticipantLeftEvent(...));

    return Optional.of(participantMapper.toDomain(participant));
}
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/participants.ts` | `leaveRoom()`, `useLeaveRoom()` |
| `components/media-controls.tsx` | Leave button |
| `hooks/use-live-room-media.ts` | Cleanup on leave |

### Component: `MediaControls`

```tsx
const MediaControls = ({ roomCode, isHost }: Props) => {
  const leaveRoom = useLeaveRoom();
  const endRoom = useEndRoom();
  const { cleanup } = useLiveRoomMedia();

  const handleLeave = () => {
    leaveRoom.mutate(
      { roomCode },
      {
        onSuccess: () => {
          cleanup();
          router.push("/dashboard/live-rooms");
        },
      }
    );
  };

  return (
    <button onClick={handleLeave} className="bg-red-600">
      Leave
    </button>
  );
};
```

---

## 6. Cleanup on Leave

```typescript
// use-live-room-media.ts
const cleanup = () => {
  // 1. Close WebRTC connections
  peerManager.close();

  // 2. Stop local stream
  localStream.getTracks().forEach(track => track.stop());

  // 3. Disconnect WebSocket
  disconnectStompClient();

  // 4. Reset store
  useLiveRoomMediaStore.getState().reset();
};
```

---

## 7. Host vs Participant Leave

| Aspect | Host Leave | Participant Leave |
|--------|-----------|-------------------|
| **Room status** | Still ACTIVE | Still ACTIVE |
| **Other participants** | Continue | Continue |
| **Redirect** | To dashboard | To dashboard |
| **WebRTC** | Disconnect all | Disconnect self |

---

## 8. Side Effects

| Effect | Description |
|--------|-------------|
| **Participant record** | `leftAt = now` |
| **Room count** | Decremented by 1 |
| **WebSocket** | Broadcast `PARTICIPANT_LEFT` to room topic |
| **WebRTC** | Peer connections closed |

---

## 9. Related Documentation

- [FR-003: Get Room Details](./FR-003-get-room.md)
- [FR-004: End Room](./FR-004-end-room.md)
- [FR-011: Realtime Events](./FR-011-realtime-events.md)
