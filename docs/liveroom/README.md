# Liveroom Module - Documentation Index

## Overview

Liveroom là module cho phép user tham gia các buổi live video/audio với real-time WebRTC và WebSocket signaling.

## UI Layout Overview

### Host Page: `/dashboard/live-rooms/{roomCode}`

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  DashboardLayout                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Header: "Meeting with Team"                      [End Session] [Share] │   │
│  │ Room Code: ABC123 [Copy]                        🔴 LIVE | 3/5        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌───────────────────────────────────┐ ┌─────────────────────────────────┐  │
│  │                                   │ │ Sidebar                         │  │
│  │  MediaStage (Video Grid)          │ │ ┌─────────────────────────────┐│  │
│  │                                   │ │ │ Join Requests (2)            ││  │
│  │  ┌─────────┐ ┌─────────┐       │ │ │ ┌───────────────────────┐ ││  │
│  │  │ Local   │ │ Remote  │       │ │ │ │ 🟡 John "Hi, join!" │ ││  │
│  │  │ Video   │ │ Video   │       │ │ │ │ [✓] [✗]              │ ││  │
│  │  │  👤    │ │  👤    │       │ │ │ └───────────────────────┘ ││  │
│  │  └─────────┘ └─────────┘       │ │ │ ┌───────────────────────┐ ││  │
│  │                                   │ │ │ │ 🟡 Jane "Can I?"     │ ││  │
│  │  ┌─────────┐                     │ │ │ │ [✓] [✗]              │ ││  │
│  │  │ Remote  │                     │ │ │ └───────────────────────┘ ││  │
│  │  │ Video   │                     │ │ └─────────────────────────┘│  │
│  │  │  👤    │                     │ │                                 │  │
│  │  └─────────┘                     │ │ ┌─────────────────────────────┐│  │
│  │                                   │ │ │ Participants (3)            ││  │
│  │                                   │ │ │ • You (Host)               ││  │
│  │                                   │ │ │ • Alice                     ││  │
│  │                                   │ │ │ • Bob                       ││  │
│  │                                   │ │ └─────────────────────────────┘│  │
│  │                                   │ └─────────────────────────────────┘  │
│  │  ┌─────────────────────────────────┐                                     │
│  │  │ [🎤] [📹] [🔊] [Device▼] [Leave] │                                     │
│  │  └─────────────────────────────────┘                                     │
│  └───────────────────────────────────┘                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Guest Page: `/live-rooms/{roomCode}`

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  DashboardLayout                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Header: "Join Live Room"                               [← Back]     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌───────────────────────────────────┐ ┌─────────────────────────────────┐  │
│  │                                   │ │ Room Info                       │  │
│  │  MediaStage (Video Grid)          │ │ Title: Meeting Team             │  │
│  │                                   │ │ Code: ABC123 [Copy]             │  │
│  │  ┌─────────┐ ┌─────────┐       │ │ Mode: 🔓 PUBLIC                 │  │
│  │  │ Local   │ │ Remote  │       │ │ Status: 🔴 ACTIVE               │  │
│  │  │ Video   │ │ Video   │       │ │ 2/5 people                     │  │
│  │  │  👤    │ │  👤    │       │ │                                 │  │
│  │  └─────────┘ └─────────┘       │ │ Participants                    │  │
│  │                                   │ │ • John (Host)                   │  │
│  │                                   │ │ • You                           │  │
│  │  ┌─────────────────────────────────┐│  │                                 │  │
│  │  │ [🎤] [📹] [🔊] [Device▼] [Leave] ││                                 │  │
│  │  └─────────────────────────────────┘│                                 │  │
│  └───────────────────────────────────┘ └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Ask To Join Form

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Header: "Join Live Room"                [← Back]     │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                                                         │ │
│  │              ┌───────────────────────────────┐         │ │
│  │              │  🔒 PRIVATE ROOM              │         │ │
│  │              │                                 │         │ │
│  │              │  "Meeting with Team"            │         │ │
│  │              │  Host: John                     │         │ │
│  │              │                                 │         │ │
│  │              │  ┌─────────────────────────────┐ │         │ │
│  │              │  │ Display Name *             │ │         │ │
│  │              │  │ [John Doe___________]     │ │         │ │
│  │              │  └─────────────────────────────┘ │         │ │
│  │              │                                 │         │ │
│  │              │  ┌─────────────────────────────┐ │         │ │
│  │              │  │ Message (optional)           │ │         │ │
│  │              │  │ [Hi, I'd like to join___] │ │         │ │
│  │              │  └─────────────────────────────┘ │         │ │
│  │              │                                 │         │ │
│  │              │       [Ask to Join]              │         │ │
│  │              └───────────────────────────────┘         │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Waiting State

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Header: "Waiting for Approval"          [← Back]     │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                                                         │ │
│  │              ┌───────────────────────────────┐         │ │
│  │              │  ⏳                             │         │ │
│  │              │                                 │         │ │
│  │              │  Waiting for approval...        │         │ │
│  │              │                                 │         │ │
│  │              │  Waiting: 0:45                  │         │ │
│  │              │  ██████████░░░░░░░░░          │         │ │
│  │              │                                 │         │ │
│  │              │       [Cancel Request]          │         │ │
│  │              └───────────────────────────────┘         │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Dashboard List

```
┌─────────────────────────────────────────────────────────────┐
│  DashboardLayout                                            │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Header: "My Live Rooms"                  [+ Create] │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ [ALL] [ACTIVE] [PAUSED] [ENDED]                    │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                                                         │ │
│  │  ┌─────────────────┐ ┌─────────────────┐           │ │
│  │  │ RoomCard        │ │ RoomCard        │           │ │
│  │  │ ┌─────────────┐ │ │ ┌─────────────┐ │           │ │
│  │  │ │ 🔴 ACTIVE  │ │ │ │ 🟡 PAUSED │ │           │ │
│  │  │ │ Meeting     │ │ │ │ Daily       │ │           │ │
│  │  │ │ 🔓 PUBLIC  │ │ │ │ 🔒 PRIVATE │ │           │ │
│  │  │ │ 2/5 people │ │ │ │ 0/3 people │ │           │ │
│  │  │ │ [Manage]   │ │ │ │ [Manage]   │ │           │ │
│  │  │ │ [End]      │ │ │ │ [End]      │ │           │ │
│  │  │ └─────────────┘ │ │ └─────────────┘ │           │ │
│  │  └─────────────────┘ └─────────────────┘           │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## Feature Documents

### Core Features

| # | Document | Mô tả |
|---|----------|--------|
| 1 | [FR-001: Create Room](./FR-001-create-room.md) | Tạo phòng Live (PRO only) |
| 2 | [FR-002: List My Rooms](./FR-002-list-my-rooms.md) | Danh sách phòng của tôi |
| 3 | [FR-003: Get Room Details](./FR-003-get-room.md) | Lấy thông tin phòng |
| 4 | [FR-004: End Room](./FR-004-end-room.md) | Kết thúc phòng |
| 5 | [FR-005: Join Public Room](./FR-005-join-public-room.md) | Tham gia phòng Public |
| 6 | [FR-006: Join Private Room](./FR-006-join-private-room.md) | Request tham gia phòng Private |
| 7 | [FR-007: Approve Join Request](./FR-007-approve-join-request.md) | Host approve/reject request |
| 8 | [FR-008: Leave Room](./FR-008-leave-room.md) | Rời phòng |
| 9 | [FR-009: Media Controls](./FR-009-media-controls.md) | Toggle mic/camera |
| 10 | [FR-010: WebRTC Signaling](./FR-010-webrtc-signaling.md) | WebRTC peer connection |
| 11 | [FR-011: Realtime Events](./FR-011-realtime-events.md) | WebSocket/STOMP events |
| 12 | [FR-012: Data Models](./FR-012-data-models.md) | Domain models & validation |

## Architecture

### Backend Structure

```
Backend/modules/liveroom/
├── api/                          # Facade interfaces + DTOs
│   ├── LiveRoomFacade.java
│   ├── LiveRoomJoinRequestFacade.java
│   ├── LiveRoomParticipantFacade.java
│   ├── dto/request/
│   └── dto/response/
├── core/
│   ├── model/                    # Domain models
│   │   ├── LiveRoom.java
│   │   ├── LiveRoomParticipant.java
│   │   └── LiveRoomJoinRequest.java
│   └── service/                 # Business logic
│       ├── LiveRoomServiceImpl.java
│       ├── LiveRoomJoinRequestServiceImpl.java
│       └── LiveRoomParticipantServiceImpl.java
└── infrastructure/
    ├── web/                     # Controllers
    │   ├── LiveRoomController.java
    │   ├── LiveRoomJoinRequestController.java
    │   ├── LiveRoomParticipantController.java
    │   └── LiveRoomWebSocketController.java
    └── persistence/             # JPA + Repositories
```

### Frontend Structure

```
frontend/src/features/liveroom/
├── api/                         # API clients
│   ├── rooms.ts
│   ├── participants.ts
│   ├── join-requests.ts
│   └── ws.ts                    # WebSocket/STOMP
├── components/                   # UI components
│   ├── create-room-form.tsx
│   ├── media-stage.tsx
│   ├── media-controls.tsx
│   ├── join-request-queue-panel.tsx
│   └── ...
├── hooks/                       # Business hooks
│   ├── use-live-room-media.ts
│   ├── use-peer-signaling.ts
│   └── use-live-room-realtime.ts
├── lib/
│   └── webrtc-peer-manager.ts   # WebRTC class
└── stores/
    └── use-live-room-media-store.ts
```

## User Flows

### Host Flow

```
1. /dashboard/live-rooms
       ↓
2. Click "Create Room" → /dashboard/live-rooms/new
       ↓
3. Fill form → POST /api/v1/live-rooms
       ↓
4. Redirect /dashboard/live-rooms/{roomCode}
       ↓
5. MediaStage + Sidebar (Requests/Participants) + Controls
```

### Guest Flow (Public Room)

```
1. Enter roomCode or click link
       ↓
2. GET /live-rooms/{roomCode}/exists
       ↓
3. GET /live-rooms/{roomCode}/me/status
       ↓
4. mode=PUBLIC → Redirect /live-rooms/{roomCode}
       ↓
5. MediaStage + Controls
```

### Guest Flow (Private Room)

```
1. Enter roomCode or click link
       ↓
2. GET /live-rooms/{roomCode}/me/status
       ↓
3. mode=PRIVATE → Show AskToJoin form
       ↓
4. POST /live-rooms/{roomCode}/join-requests
       ↓
5. Show WaitingRoomCard + subscribe decision event
       ↓
6. Host approves → JoinRequestDecided event received
       ↓
7. Navigate to room
```

## API Summary

### REST Endpoints

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/live-rooms` | PRO | Create room |
| GET | `/api/v1/live-rooms/me` | PRO | List my rooms |
| GET | `/api/v1/live-rooms/{roomCode}` | USER+ | Get room |
| POST | `/api/v1/live-rooms/{roomCode}/end` | PRO | End room |
| GET | `/api/v1/live-rooms/{roomCode}/exists` | USER+ | Check exists |
| GET | `/api/v1/live-rooms/{roomCode}/me/status` | USER+ | Viewer status |
| POST | `/api/v1/live-rooms/{roomCode}/join-requests` | USER+ | Create request |
| GET | `/api/v1/live-rooms/{roomCode}/join-requests` | PRO | List requests |
| POST | `/api/v1/live-rooms/{roomCode}/join-requests/{id}/approve` | PRO | Approve |
| POST | `/api/v1/live-rooms/{roomCode}/join-requests/{id}/reject` | PRO | Reject |
| DELETE | `/api/v1/live-rooms/{roomCode}/join-requests/{id}` | USER+ | Cancel |
| POST | `/api/v1/live-rooms/{roomCode}/leave` | USER+ | Leave room |
| GET | `/api/v1/live-rooms/{roomCode}/participants` | USER+ | List participants |
| PATCH | `/api/v1/live-rooms/{roomCode}/participants/me/media` | USER+ | Update media |

### WebSocket Topics

| Topic | Type | Description |
|-------|------|-------------|
| `/topic/room/{roomCode}/participants` | Broadcast | Participant events |
| `/topic/room/{roomCode}/peers` | Broadcast | Peer join/left |
| `/topic/room/{roomCode}/join-requests` | Broadcast | New request |
| `/user/queue/join-requests` | Push | Request decision |
| `/user/queue/room/{roomCode}/signal/*` | Push | WebRTC signaling |

## Key Business Rules

| Rule | Value |
|------|-------|
| Room Code | 6 chars (A-Z, 2-9), unique |
| Capacity | 2-5 participants |
| Host Limit | 1 active room per host |
| Roles | PRO required to create room |
| Mode | PUBLIC (direct join) / PRIVATE (approval) |
| Media Default | Mic ON, Camera OFF |

## External Dependencies

| Service | Purpose |
|---------|---------|
| STUN | WebRTC NAT traversal (Google: stun.l.google.com:19302) |
| TURN | Relay for restrictive firewalls (Phase 2) |
| SockJS | WebSocket fallback |
| STOMP | Message broker protocol |
