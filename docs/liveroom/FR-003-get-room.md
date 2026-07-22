# FR-003: Lấy Thông Tin Phòng

## 1. Mô tả

Lấy chi tiết thông tin một phòng live theo `roomCode`. Dùng để hiển thị thông tin phòng hoặc kiểm tra trạng thái trước khi tham gia.

---

## 2. UI Layout

### Page: `/live-rooms/{roomCode}` (Guest - Full)

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Join Live Room"              [← Back]     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────┐ ┌────────────────────────────┐   │
│  │                      │ │ Sidebar                    │   │
│  │  MediaStage          │ │ ┌────────────────────────┐│   │
│  │  (when joined)      │ │ │ Room Info              ││   │
│  │                      │ │ │ Title: Meeting Team    ││   │
│  │  ┌────────┐         │ │ │ Code: ABC123    [Copy] ││   │
│  │  │ Local  │         │ │ │ Mode: 🔓 PUBLIC        ││   │
│  │  │ Video  │         │ │ │ Status: 🔴 ACTIVE      ││   │
│  │  └────────┘         │ │ │ 2/5 people            ││   │
│  │                      │ │ └────────────────────────┘│   │
│  │  ┌────────┐         │ │                            │   │
│  │  │ Remote │         │ │ ┌────────────────────────┐│   │
│  │  │ Video  │         │ │ │ Participants (2)       ││   │
│  │  └────────┘         │ │ │ • John (Host)          ││   │
│  │                      │ │ │ • You                  ││   │
│  │  ┌────────┐         │ │ └────────────────────────┘│   │
│  │  │ Remote │         │ │                            │   │
│  │  │ Video  │         │ └────────────────────────────┘   │
│  │  └────────┘         │                                  │
│  │                      │                                  │
│  │  ┌────────────────────────────────────┐               │
│  │  │ MediaControls                      │               │
│  │  │ [🎤] [📹] [🔊] [Device]  [Leave] │               │
│  │  └────────────────────────────────────┘               │
│  └──────────────────────┘                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Ask To Join Flow UI

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Join Live Room"              [← Back]       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  🔒 PRIVATE ROOM                │       │   │
│  │              │                                   │       │   │
│  │              │  "Meeting with Team"              │       │   │
│  │              │  Host: John                      │       │   │
│  │              │                                   │       │   │
│  │              │  ┌─────────────────────────────┐ │       │   │
│  │              │  │ Display Name *             │ │       │   │
│  │              │  │ [John Doe___________]      │ │       │   │
│  │              │  └─────────────────────────────┘ │       │   │
│  │              │                                   │       │   │
│  │              │  ┌─────────────────────────────┐ │       │   │
│  │              │  │ Message (optional)         │ │       │   │
│  │              │  │ [Hi, I'd like to join___]  │ │       │   │
│  │              │  │ [_________________________] │ │       │   │
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

### Waiting State UI

```
┌─────────────────────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  ⏳ Waiting for approval...      │       │   │
│  │              │                                   │       │   │
│  │              │  Your request is being reviewed  │       │   │
│  │              │  by the host.                   │       │   │
│  │              │                                   │       │   │
│  │              │  Waiting: 0:45                   │       │   │
│  │              │                                   │       │   │
│  │              │       [Cancel Request]           │       │   │
│  │              │                                   │       │   │
│  │              └───────────────────────────────┘       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Rejected State UI

```
┌─────────────────────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │              ┌───────────────────────────────┐       │   │
│  │              │  ❌ Request Declined           │       │   │
│  │              │                                   │       │   │
│  │              │  Sorry, the host has declined   │       │   │
│  │              │  your request.                  │       │   │
│  │              │                                   │       │   │
│  │              │  Reason: "Room is full for now" │       │   │
│  │              │                                   │       │   │
│  │              │  [Ask Again]      [Back]          │       │   │
│  │              │                                   │       │   │
│  │              └───────────────────────────────┘       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. API Endpoints (Backend)

### GET `/api/v1/live-rooms/{roomCode}`

**Authorization**: Role `USER`, `PRO`, hoặc `ADMIN`

**Path Parameters**:
| Param | Type | Mô tả |
|-------|------|--------|
| `roomCode` | string | Mã phòng 6 ký tự |

**Response** (`LiveRoomResponse`):
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "hostUserId": "uuid",
    "roomCode": "ABC123",
    "title": "Meeting with team",
    "description": "Weekly sync",
    "mode": "PUBLIC",
    "maxParticipants": 5,
    "currentParticipantCount": 2,
    "availableSlots": 3,
    "status": "ACTIVE",
    "scheduledStartAt": null,
    "startedAt": "2026-07-22T10:00:00Z",
    "endedAt": null,
    "createdAt": "2026-07-22T10:00:00Z"
  },
  "message": "Live room retrieved successfully"
}
```

**Error Codes**:
| Code | HTTP | Mô tả |
|------|------|--------|
| `LIVEROOM_NOT_FOUND` | 404 | Phòng không tồn tại |

---

## 4. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/rooms.ts` | `getRoom()`, `useRoom()` |
| `api/rooms.ts` | `checkRoomExists()`, `useCheckRoomExists()` |
| `api/rooms.ts` | `getViewerStatus()`, `useViewerStatus()` |
| `types/index.ts` | `LiveRoom`, `LiveRoomExistsResponse`, `LiveRoomViewerStatus` |

### Hooks

```tsx
// Get full room details
const { data: room } = useRoom({ roomCode });

// Check if room exists and is active
const { data: exists } = useCheckRoomExists({ roomCode });

// Get current user's status in the room
const { data: viewerStatus } = useViewerStatus({ roomCode });
```

### Viewer Status Response

```typescript
interface LiveRoomViewerStatus {
  viewerUserId: string;
  host: boolean;           // user là host?
  participant: boolean;    // user là participant?
  pendingRequest: boolean; // có request đang chờ?
  pendingRequestId: string | null;
  pendingStatus: JoinRequestStatus | null;  // PENDING, APPROVED, REJECTED, CANCELLED
  roomStatus: LiveRoomStatus;
  roomMode: LiveRoomMode;
  roomCode: string;
  hostUserId: string;
  createdAt: string;
}
```

---

## 5. Use Cases

### 5.1 Join Flow - Kiểm tra trước khi join

```
User enters roomCode
    ↓
GET /live-rooms/{roomCode}/exists
    ↓
exists=true && active=true
    ↓
GET /live-rooms/{roomCode}/me/status
    ↓
Render appropriate UI based on status
```

### 5.2 Viewer Status Decision Tree

```
                    ┌─ host=true ──→ Host Page
                    │
room exists? ── No ─┴─→ Error: Room not found
    │
    └─ Yes ── status=ENDED ──→ Error: Room ended
                │
                ├─ mode=PUBLIC ──→ Join directly
                │
                └─ mode=PRIVATE ──→ Check pendingRequest
                                │
                                ├─ pending=true && status=PENDING
                                │       └─→ Waiting Room Card
                                │
                                ├─ pending=true && status=REJECTED
                                │       └─→ Rejected Card
                                │
                                └─ pending=false
                                        └─→ Ask to Join Card
```

---

## 6. Room Status Enum

```java
public enum LiveRoomStatus {
    ACTIVE,   // Có thể join
    PAUSED,   // Tạm dừng
    ENDED;    // Đã kết thúc

    public boolean isTerminal() { return this == ENDED; }
    public boolean isJoinable() { return this == ACTIVE; }
}
```

---

## 7. Related Documentation

- [FR-005: Join Public Room](./FR-005-join-public-room.md)
- [FR-006: Join Private Room](./FR-006-join-private-room.md)
- [FR-008: Leave Room](./FR-008-leave-room.md)
