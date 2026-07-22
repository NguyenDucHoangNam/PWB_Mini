# FR-002: Danh Sách Phòng Của Tôi

## 1. Mô tả

Cho phép **Host** (role PRO) xem danh sách các phòng live mà mình đã tạo. Hỗ trợ filter theo status và phân trang.

---

## 2. UI Layout

### Page: `/dashboard/live-rooms`

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "My Live Rooms"                            │   │
│  │                                         [+ Create] │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Filter Tabs                                         │   │
│  │ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐         │   │
│  │ │  ALL  │ │ ACTIVE│ │PAUSED │ │ ENDED │         │   │
│  │ │  (•)  │ │  ( )  │ │  ( )  │ │  ( )  │         │   │
│  │ └───────┘ └───────┘ └───────┘ └───────┘         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Room Cards Grid (2-3 columns)                       │   │
│  │ ┌───────────────────┐ ┌───────────────────┐         │   │
│  │ │ RoomCard          │ │ RoomCard          │         │   │
│  │ │ ┌───────────────┐ │ │ ┌───────────────┐ │         │   │
│  │ │ │ [🔴 ACTIVE ] │ │ │ │ [🟡 PAUSED] │ │         │   │
│  │ │ │               │ │ │ │               │ │         │   │
│  │ │ │ Meeting Team  │ │ │ │ Daily Standup │ │         │   │
│  │ │ │ 🔓 PUBLIC     │ │ │ │ 🔒 PRIVATE    │ │         │   │
│  │ │ │               │ │ │ │               │ │         │   │
│  │ │ │ 2/5 people   │ │ │ │ 0/3 people    │ │         │   │
│  │ │ │               │ │ │ │               │ │         │   │
│  │ │ │ Code: ABC123  │ │ │ │ Code: XYZ789  │ │         │   │
│  │ │ │               │ │ │ │               │ │         │   │
│  │ │ │ [Manage] [End]│ │ │ [Manage] [End]│ │         │   │
│  │ │ └───────────────┘ │ │ └───────────────┘ │         │   │
│  │ └───────────────────┘ └───────────────────┘         │   │
│  │ ┌───────────────────┐                               │   │
│  │ │ RoomCard          │                               │   │
│  │ │ ...               │                               │   │
│  │ └───────────────────┘                               │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Pagination                                           │   │
│  │ [< Prev]  Page 1 of 3  [Next >]                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Component: `RoomCard`

```
┌────────────────────────────────────────┐
│  ┌──────────┐  Room Title             │
│  │ [🔴]     │  ABC123                 │
│  └──────────┘  PUBLIC | 2/5 people    │
│                                         │
│  Created: Jul 22, 2026                 │
│                                         │
│  [Manage]            [End Room]         │
└────────────────────────────────────────┘
```

### Card States

| Status | Badge Color | Actions |
|--------|-------------|---------|
| ACTIVE | Green 🔴 | Manage, End |
| PAUSED | Yellow 🟡 | Manage, End |
| ENDED | Gray ⚫ | (View only) |

### Filter Tabs

```
┌────────────────────────────────────────┐
│  [ALL] [ACTIVE] [PAUSED] [ENDED]      │
│   (•)    ( )      ( )       ( )        │
└────────────────────────────────────────┘
```

---

## 3. API Endpoints (Backend)

### GET `/api/v1/live-rooms/me`

**Authorization**: Role `PRO` bắt buộc

**Query Parameters**:
| Param | Type | Mô tả |
|-------|------|--------|
| `status` | `LiveRoomStatus` | Filter: ACTIVE, PAUSED, ENDED (optional) |
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default 20) |

**Response** (`Page<LiveRoomSummaryResponse>`):
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "roomCode": "ABC123",
        "title": "Meeting with team",
        "mode": "PUBLIC",
        "status": "ACTIVE",
        "maxParticipants": 5,
        "currentParticipantCount": 2,
        "createdAt": "2026-07-22T10:00:00Z",
        "endedAt": null
      }
    ],
    "totalElements": 15,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": "Live rooms retrieved successfully"
}
```

---

## 4. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/rooms.ts` | `listMyRooms()`, `useMyRooms()` |
| `components/dashboard-live-rooms-tab.tsx` | Tab UI |
| `components/room-card.tsx` | Card cho mỗi room |
| `types/index.ts` | `LiveRoomSummary` |

### Component: `DashboardLiveRoomsTab`

```tsx
// Filter states
const [statusFilter, setStatusFilter] = useState<LiveRoomStatus | null>(null);

// Query
const { data, isLoading } = useMyRooms({
  page: 0,
  size: 20,
  status: statusFilter ?? undefined,
});

// Render
<RoomCard room={room} onEnd={handleEndRoom} />
```

### Room Card Actions

| Action | Trigger | Behavior |
|--------|---------|----------|
| **Manage** | Click card | Navigate to `/dashboard/live-rooms/{roomCode}` |
| **End** | Click button | Open `RoomEndDialog` → `useEndRoom()` |

---

## 5. Filter Options

| Tab | Status | Mô tả |
|-----|--------|--------|
| All | `null` | Tất cả rooms |
| Active | `ACTIVE` | Rooms đang hoạt động |
| Paused | `PAUSED` | Rooms tạm dừng |
| Ended | `ENDED` | Rooms đã kết thúc |

---

## 6. Pagination

- Default page size: 20
- Frontend tự động load thêm khi scroll (infinite scroll)
- Hoặc manual pagination với prev/next buttons

---

## 7. Related Documentation

- [FR-001: Create Room](./FR-001-create-room.md)
- [FR-004: End Room](./FR-004-end-room.md)
- [FR-003: Get Room Details](./FR-003-get-room.md)
