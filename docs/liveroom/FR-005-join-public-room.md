# FR-005: Tham Gia Phòng Public

## 1. Mô tả

Cho phép user tham gia trực tiếp vào phòng **PUBLIC** mà không cần approval từ host. Với phòng **PRIVATE**, xem [FR-006](./FR-006-join-private-room.md).

---

## 2. UI Layout

### Join by Code Card

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Header: "Join a Live Room"                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │         ┌───────────────────────────────────────┐     │   │
│  │         │                                       │     │   │
│  │         │    Enter Room Code                    │     │   │
│  │         │                                       │     │   │
│  │         │    [ A ] [ B ] [ C ] [ 1 ] [ 2 ] [ 3 ]    │   │
│  │         │                                       │     │   │
│  │         │    ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐         │     │   │
│  │         │    │ │ │ │ │ │ │ │ │ │ │ │ │         │     │   │
│  │         │    └─┘ └─┘ └─┘ └─┘ └─┘ └─┘         │     │   │
│  │         │    ↑ typing indicator                 │     │   │
│  │         │                                       │     │   │
│  │         │    (6 characters required)            │     │   │
│  │         │                                       │     │   │
│  │         │                                       │     │   │
│  │         └───────────────────────────────────────┘     │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Input States

```
┌────────────────────────────────────────┐
│  ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐            │
│  │A│ │B│ │C│ │ │ │ │ │ │ ← empty box │
│  └─┘ └─┘ └─┘ └─┘ └─┘ └─┘            │
│       typing...                       │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│  ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐            │
│  │A│ │B│ │C│ │1│ │2│ │3│ │ ← complete │
│  └─┘ └─┘ └─┘ └─┘ └─┘ └─┘            │
│       auto-submit!                    │
└────────────────────────────────────────┘
```

### Behavior

| Action | Behavior |
|--------|---------|
| Keypress | Auto-uppercase, only A-Z, 2-9 |
| 6 chars entered | Auto-submit form |
| Invalid code | Show error "Room not found" |
| Room ended | Show error "Room has ended" |

---

## 3. API Endpoints (Backend)

### GET `/api/v1/live-rooms/{roomCode}/me/status`

**Authorization**: Role `USER`, `PRO`, hoặc `ADMIN`

**Mục đích**: Kiểm tra trạng thái user trong phòng trước khi join

**Response**:
```typescript
interface LiveRoomViewerStatus {
  viewerUserId: string;
  host: boolean;           // Là host?
  participant: boolean;    // Là participant?
  pendingRequest: boolean; // Có request đang chờ?
  pendingRequestId: string | null;
  pendingStatus: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | null;
  roomStatus: LiveRoomStatus;
  roomMode: LiveRoomMode;
  roomCode: string;
  hostUserId: string;
  createdAt: string;
}
```

### Decision Logic

```
mode=PUBLIC && participant=false && pendingRequest=false
    → User có thể join trực tiếp
```

---

## 4. Frontend Flow

### 4.1 Join by Code

**Component**: `JoinRoomByCodeCard`

```tsx
const JoinRoomByCodeCard = () => {
  const [code, setCode] = useState("");

  const handleComplete = async (code: string) => {
    // 1. Check room exists
    const { data: exists } = await checkRoomExists({ roomCode: code });
    if (!exists.exists || !exists.active) {
      return { valid: false, error: "Room not available" };
    }

    // 2. Get viewer status
    const { data: status } = await getViewerStatus({ roomCode: code });

    // 3. Navigate based on status
    if (status.mode === "PUBLIC") {
      router.push(`/live-rooms/${code}`);
    } else {
      router.push(`/live-rooms/${code}`); // Will show AskToJoin form
    }
  };
};
```

### 4.2 Join via Link

```
User clicks /live-room/{roomCode}
    ↓
Page loads → check room exists
    ↓
GET /live-rooms/{roomCode}/exists
    ↓
GET /live-rooms/{roomCode}/me/status
    ↓
mode=PUBLIC → Show room
mode=PRIVATE → Show AskToJoin form
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/rooms.ts` | `checkRoomExists()`, `useCheckRoomExists()`, `getViewerStatus()`, `useViewerStatus()` |
| `components/join-room-by-code-card.tsx` | Input form |
| `app/live-rooms/[roomCode]/page.tsx` | Guest join page |
| `app/(live-rooms)/live-rooms/[roomCode]/page.tsx` | Full guest page |

### Component: `JoinRoomByCodeCard`

```tsx
// Auto-uppercase, sanitize non-alphanumeric
const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
  const value = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "");
  setCode(value.slice(0, 6));
};

// Auto-submit when 6 chars
useEffect(() => {
  if (code.length === 6) {
    handleComplete(code);
  }
}, [code]);
```

---

## 6. Public vs Private Mode

| Aspect | PUBLIC | PRIVATE |
|--------|--------|---------|
| **Join** | Trực tiếp | Cần approval |
| **Flow** | `participant=true` sau khi join | `pendingRequest=true` → host approve |
| **UI** | Vào phòng ngay | Form "Ask to Join" |

---

## 7. Validation Rules

| Check | Error |
|-------|-------|
| Room exists | "Room not found" |
| Room active | "Room has ended" |
| Room not full | "Room is full" (checked when approving request) |

---

## 8. Related Documentation

- [FR-003: Get Room Details](./FR-003-get-room.md)
- [FR-006: Join Private Room](./FR-006-join-private-room.md)
- [FR-008: Leave Room](./FR-008-leave-room.md)
