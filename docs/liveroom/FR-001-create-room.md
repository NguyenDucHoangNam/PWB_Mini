# FR-001: Tạo Phòng Live

## 1. Mô tả

Cho phép user có role **PRO** tạo một phòng live mới. Sau khi tạo thành công, user trở thành **Host** của phòng và nhận được room code 6 ký tự để share.

---

## 2. UI Layout

### Page: `/dashboard/live-rooms/new`

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Create Live Room"                          │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │  CreateRoomForm                             │   │   │
│  │  │  ┌─────────────────────────────────────┐   │   │   │
│  │  │  │ Title *                              │   │   │   │
│  │  │  │ [________________________]            │   │   │   │
│  │  │  │ (1-200 characters)                  │   │   │   │
│  │  │  └─────────────────────────────────────┘   │   │   │
│  │  │                                             │   │   │
│  │  │  ┌─────────────────────────────────────┐   │   │   │
│  │  │  │ Description                         │   │   │   │
│  │  │  │ [________________________]          │   │   │   │
│  │  │  │ [________________________]          │   │   │   │
│  │  │  │ [________________________]          │   │   │   │
│  │  │  │ (max 1000 characters)               │   │   │   │
│  │  │  └─────────────────────────────────────┘   │   │   │
│  │  │                                             │   │   │
│  │  │  ┌─────────────────────────────────────┐   │   │   │
│  │  │  │ Room Mode *                         │   │   │   │
│  │  │  │ ┌───────────┐ ┌───────────┐       │   │   │   │
│  │  │  │ │  PUBLIC   │ │  PRIVATE  │       │   │   │   │
│  │  │  │ │   ( )     │ │   (•)     │       │   │   │   │
│  │  │  │ │ Join free │ │ Need aproval│      │   │   │   │
│  │  │  │ └───────────┘ └───────────┘       │   │   │   │
│  │  │  └─────────────────────────────────────┘   │   │   │
│  │  │                                             │   │   │
│  │  │  ┌─────────────────────────────────────┐   │   │   │
│  │  │  │ Max Participants                     │   │   │   │
│  │  │  │ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐   │   │   │   │
│  │  │  │ │  2  │ │  3  │ │  4  │ │  5  │   │   │   │   │
│  │  │  │ │ (•) │ │ ( ) │ │ ( ) │ │ ( ) │   │   │   │   │
│  │  │  │ └─────┘ └─────┘ └─────┘ └─────┘   │   │   │   │
│  │  │  └─────────────────────────────────────┘   │   │   │
│  │  │                                             │   │   │
│  │  │              [ Create Room ]                │   │   │
│  │  │                                             │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Component: `CreateRoomForm`

```tsx
// Validation
const schema = z.object({
  title: z.string().min(1, "Title is required").max(200),
  description: z.string().max(1000).optional(),
  mode: z.enum(["PUBLIC", "PRIVATE"]),
  maxParticipants: z.number().min(2).max(5).optional(),
});

// Form fields
interface FormValues {
  title: string;
  description?: string;
  mode: "PUBLIC" | "PRIVATE";
  maxParticipants: number;
}

// On success
const onSuccess = (room: LiveRoom) => {
  router.push(`/dashboard/live-rooms/${room.roomCode}`);
};
```

### Field States

| Field | Required | Default | Validation |
|-------|----------|---------|------------|
| Title | Yes | - | 1-200 chars |
| Description | No | null | max 1000 chars |
| Mode | Yes | PUBLIC | PUBLIC / PRIVATE |
| Max Participants | No | 5 | 2-5 |

### Mode Selection UI

```
┌────────────────────────────────────────┐
│  PUBLIC                                │
│  ○ Globe icon                          │
│  Anyone can join directly               │
│                                        │
│  PRIVATE                               │
│  ● Lock icon                           │
│  Requires host approval to join         │
└────────────────────────────────────────┘
```

### Capacity Selection UI

```
┌────────────────────────────────────────┐
│  How many people can join?             │
│                                        │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
│  │ 2  │ │ 3  │ │ 4  │ │ 5  │        │
│  │    │ │    │ │    │ │ •  │        │
│  └────┘ └────┘ └────┘ └────┘        │
│                                        │
│  Selected: 5 people                    │
└────────────────────────────────────────┘
```

---

## 3. API Endpoints (Backend)

### POST `/api/v1/live-rooms`

**Authorization**: Role `PRO` bắt buộc

**Request Body** (`CreateLiveRoomRequest`):
```json
{
  "title": "Meeting with team",           // required, 1-200 chars
  "description": "Weekly sync",           // optional, max 1000 chars
  "mode": "PUBLIC",                        // required: PUBLIC | PRIVATE
  "maxParticipants": 5                      // optional: 2-5, default 5
}
```

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
    "currentParticipantCount": 0,
    "availableSlots": 5,
    "status": "ACTIVE",
    "scheduledStartAt": null,
    "startedAt": null,
    "endedAt": null,
    "createdAt": "2026-07-22T10:00:00Z"
  },
  "message": "Live room created successfully"
}
```

**Error Codes**:
| Code | HTTP | Mô tả |
|------|------|--------|
| `LIVEROOM_HOST_ALREADY_ACTIVE` | 409 | Host đã có phòng ACTIVE |
| `LIVEROOM_INVALID_CAPACITY` | 400 | Capacity không hợp lệ |

---

## 4. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/rooms.ts` | `createRoom()`, `useCreateRoom()` |
| `components/create-room-form.tsx` | Form UI |
| `schemas/room-schema.ts` | Zod validation |
| `app/(dashboard)/dashboard/live-rooms/new/page.tsx` | Page route |

### Form Flow

```
User fills form
    ↓
react-hook-form validates
    ↓
useCreateRoom mutation
    ↓
POST /api/v1/live-rooms
    ↓
Success → redirect to /dashboard/live-rooms/{roomCode}
Error → show error message
```

---

## 5. Business Rules

| Rule | Chi tiết |
|------|----------|
| **Role** | Chỉ `PRO` user được tạo phòng |
| **Room Code** | Tự động sinh 6 ký tự (A-Z, 2-9), unique |
| **Host limit** | Mỗi host chỉ có 1 phòng ACTIVE tại thời điểm |
| **Capacity** | 2-5 participants, default 5 |
| **Mode PUBLIC** | User khác có thể join trực tiếp |
| **Mode PRIVATE** | User khác cần gửi request, host approve |

---

## 6. Domain Model

```java
// LiveRoom.java - create factory method
public static LiveRoom create(
    UUID hostUserId,
    String roomCode,        // 6 chars
    String title,
    String description,
    LiveRoomMode mode,
    int maxParticipants     // 2-5
) {
    validateTitle(title);
    validateCapacity(maxParticipants);
    validateRoomCode(roomCode);
    // ...
}

// LiveRoomMode enum
public enum LiveRoomMode {
    PUBLIC,    // direct join
    PRIVATE;   // requires approval
}
```

---

## 7. Error Handling

| Scenario | Behavior |
|----------|----------|
| User không phải PRO | API trả 403, FE hiển thị `ProUpgradePrompt` |
| Host đã có phòng ACTIVE | API trả 409, FE hiển thị toast error |
| Title rỗng | Validation error trên form |
| Capacity < 2 hoặc > 5 | Validation error trên form |

---

## 8. Related Documentation

- [FR-003: Get Room Details](./FR-003-get-room.md)
- [FR-004: End Room](./FR-004-end-room.md)
- [FR-006: Join Private Room](./FR-006-join-private-room.md)
