# Video Conference Module - Requirements Specification

## 1. Overview

### 1.1 Module Name
**VideoCall** - Real-time Video Conferencing Module

### 1.2 Purpose
Cung cấp tính năng gọi video nhóm nhỏ (2-4 người) với khả năng giao tiếp trực tiếp qua video/audio và chat trong thời gian thực.

### 1.3 Scope

| Attribute | Value |
|-----------|-------|
| **Số người tối đa** | 4 người/phòng |
| **Kiểu cuộc gọi** | 1-1 và nhóm nhỏ (không phân biệt) |
| **Data persistence** | Session only - không lưu trữ sau khi kết thúc |
| **Signaling** | Tự xây WebSocket server |

---

## 2. Functional Requirements

### 2.1 Room Management

#### FR-001: Tạo phòng họp
- **Mô tả**: User có thể tạo một phòng họp mới
- **Trigger**: User nhấn nút "Create Room" hoặc "Start Video Call"
- **Behavior**:
  - Hệ thống tạo unique room ID (UUID format)
  - User tạo được assign làm **Host** của phòng
  - Sinh ra **Invite Link** dạng: `https://domain.com/video/{roomId}`
- **Output**: Room ID, Invite Link

#### FR-002: Tham gia phòng
- **Mô tả**: User có thể tham gia phòng qua 2 cách
- **Trigger**: User truy cập invite link HOẶC nhập mã phòng

**Cách 1: Qua Invite Link**
- **Behavior**:
  - User truy cập `/video/{roomId}`
  - Validate room tồn tại và chưa kết thúc
  - Check số người trong phòng < 4
  - User join với vai trò **Participant**

**Cách 2: Nhập Mã phòng**
- **Behavior**:
  - User vào trang join `/video`
  - Nhập Room ID / Code (VD: `ABC-123`)
  - Validate và join tương tự cách 1
  - Nếu không có display name → yêu cầu nhập tên

- **Error cases**:
  - Room không tồn tại → Hiển thị "Room not found"
  - Room đã đầy (4 người) → Hiển thị "Room is full"
  - Room đã kết thúc → Hiển thị "This room has ended"

#### FR-003: Invite Link
- **Mô tả**: Share link để mời người khác vào phòng
- **Behavior**:
  - Invite link format: `/video/{roomId}`
  - Copy to clipboard button
  - Link có thể share qua bất kỳ kênh nào

### 2.2 Media Controls

#### FR-004: Video Call (Camera)
- **Mô tả**: Truyền video real-time giữa các participants
- **Behavior**:
  - Sử dụng WebRTC getUserMedia API
  - Camera stream được broadcast đến tất cả participants
  - Hiển thị video của tất cả người trong phòng (Gallery View)
- **States**:
  - `enabled`: Camera bật, stream đang gửi
  - `disabled`: Camera tắt, hiển thị avatar fallback

#### FR-005: Audio Call (Microphone)
- **Mô tả**: Truyền audio real-time giữa các participants
- **Behavior**:
  - Sử dụng WebRTC getUserMedia API
  - Audio stream được broadcast đến tất cả participants
- **States**:
  - `enabled`: Mic bật, audio đang gửi
  - `disabled`: Mic tắt, user không nghe thấy audio của người này

#### FR-006: Toggle Camera
- **Trigger**: User nhấn nút toggle camera
- **Behavior**: Bật/tắt camera, thay đổi stream
- **UI Feedback**: Icon thay đổi (camera on/off), video hoặc avatar hiển thị

#### FR-007: Toggle Microphone
- **Trigger**: User nhấn nút toggle mic
- **Behavior**: Bật/tắt mic, thay đổi stream
- **UI Feedback**: Icon thay đổi (mic on/off)

### 2.3 Chat

#### FR-008: In-Room Chat
- **Mô tả**: Gửi tin nhắn text trong phòng họp
- **Behavior**:
  - Tin nhắn gửi đến tất cả participants trong phòng
  - Hiển thị sender name + timestamp
  - Tin nhắn **không** được lưu sau khi phòng kết thúc
- **Constraints**:
  - Text only (không hỗ trợ emoji/image/file)
  - Max length: 500 characters
  - Không có chat history khi reload

### 2.4 Host Controls

#### FR-009: Kick Participant
- **Mô tả**: Host có thể mời participant ra khỏi phòng
- **Trigger**: Host nhấn nút kick trên participant tile
- **Behavior**:
  - Participant bị kick được chuyển về landing page
  - WebRTC connection bị terminate
  - Participant nhìn thấy thông báo "You have been removed from the room"
- **Authorization**: Chỉ Host mới có quyền kick

#### FR-010: Mute Participant (Remote)
- **Mô tả**: Host có thể tắt mic của participant khác
- **Trigger**: Host nhấn nút mute trên participant tile
- **Behavior**:
  - Participant bị mute từ xa
  - Participant không thể unmute cho đến khi host unmute
  - UI của participant hiển thị trạng thái bị mute
- **Authorization**: Chỉ Host mới có quyền mute người khác

### 2.5 Session Management

#### FR-011: Leave Room
- **Trigger**: User nhấn nút "Leave"
- **Behavior**:
  - User rời phòng, WebRTC connection terminate
  - Phòng vẫn tiếp tục với những người còn lại
  - Nếu là Host rời đi → chuyển quyền Host cho người khác hoặc kết thúc phòng

#### FR-012: End Room
- **Trigger**: Host nhấn nút "End Room"
- **Behavior**:
  - Tất cả participants bị disconnect
  - Mọi người được chuyển về landing page
  - Room state bị destroy

---

## 3. Non-Functional Requirements

### 3.1 Performance
- **Video quality**: 480p default, có option 720p
- **Frame rate**: 30fps target
- **Latency**: < 200ms cho signaling
- **Connection time**: < 5s để join và thấy video đầu tiên

### 3.2 Reliability
- **Reconnection**: Không support trong MVP (Phase 2)
- **Fallback**: Nếu WebRTC fail → hiển thị lỗi rõ ràng

### 3.3 Security
- **Room ID**: UUID, không đoán được
- **Host Authorization**: Server-side validation cho host actions
- **No persistence**: Không lưu session data

### 3.4 Browser Support
- Chrome 80+
- Firefox 75+
- Safari 14+
- Edge 80+

---

## 4. UI/UX Specifications

### 4.1 Layout: Gallery View

```
┌─────────────────────────────────────────────────────────────┐
│  [Logo]          Room: ABC-123           [Leave] [End]      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────────┐  ┌─────────────┐                         │
│   │  Video 1    │  │  Video 2    │                         │
│   │  (You)      │  │             │                         │
│   │  [Name]     │  │  [Name]     │                         │
│   └─────────────┘  └─────────────┘                         │
│                                                             │
│   ┌─────────────┐  ┌─────────────┐                         │
│   │  Video 3    │  │  Video 4    │  (empty if < 4 people)  │
│   │             │  │             │                         │
│   │  [Name]     │  │  [Name]     │                         │
│   └─────────────┘  └─────────────┘                         │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  [Mic] [Cam] [Share] [Chat] [Invite]           [Settings]  │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Component States

#### Video Tile
| State | Visual |
|-------|--------|
| Video ON | Hiển thị video stream |
| Video OFF | Hiển thị avatar + tên |
| Speaking | Border highlight (green) |
| Muted (remote) | Mic icon overlay (red) |
| Muted (self) | Mic icon overlay (red) |

#### Control Buttons
| Button | ON State | OFF State |
|--------|----------|-----------|
| Mic | Mic icon (white) | Mic off (red) |
| Cam | Camera icon (white) | Camera off (red) |
| Share | Screen icon | - |

### 4.3 Responsive
- **Desktop (>1024px)**: 2x2 grid
- **Tablet (768-1024px)**: 2x2 grid, smaller tiles
- **Mobile (<768px)**: 1 column, swipeable

---

## 5. Technical Architecture

### 5.1 Technology Stack

#### Backend
- **Framework**: Spring Boot (existing)
- **Signaling**: WebSocket (STOMP protocol)
- **WebRTC**: SFU/Mesh topology (Mesh for 2-4 users)
- **STUN**: Google STUN (`stun:stun.l.google.com:19302`)
- **TURN**: Phase 2 (Coturn deployment)

#### Frontend
- **Framework**: Next.js (existing)
- **WebRTC**: Native WebRTC API
- **State Management**: React Context + Hooks
- **Styling**: Tailwind CSS (existing)

### 5.2 Infrastructure Requirements

#### STUN Server (Bắt buộc - Miễn phí)
STUN server giúp WebRTC discover public IP của user để thiết lập P2P connection.

| Provider | Address | Chi phí | Notes |
|----------|---------|---------|-------|
| Google (Default) | `stun:stun.l.google.com:19302` | Miễn phí | Đủ cho MVP |

**Config:**
```typescript
const iceServers = [
  { urls: 'stun:stun.l.google.com:19302' }
];
```

#### TURN Server (Production - Tùy chọn)
TURN server là relay khi P2P không thể kết nối (corporate firewall, CGNAT).

| Phase | Cần TURN? | Chi phí |
|-------|-----------|---------|
| MVP / Demo | ❌ Không | $0 |
| Production (<100 users) | Tùy chọn | ~$5-10/tháng |
| Production (>100 users) | ✅ Cần | Server mạnh hơn |

**Coturn deployment (Phase 2):**
```bash
# Install
sudo apt install coturn

# Configure /etc/turnserver.conf
relay-ip=<your-vps-ip>
external-ip=<your-vps-public-ip>
lt-cred-mech

# Start
sudo systemctl enable coturn
```

#### VPS Requirements cho Production
| Scale | RAM | Bandwidth | Ví dụ VPS |
|-------|-----|-----------|-----------|
| < 50 concurrent users | 2GB | 1Gbps unmetered | Vultr/Culo $5 |
| 50-200 concurrent users | 4GB | 1Gbps unmetered | Vultr $20 |
| 200+ users | 8GB+ | 1Gbps unmetered | Dedicated hoặc SFU |

#### Summary
```
┌─────────────────────────────────────────────────────────────┐
│                    MVP / Demo                                │
│  STUN: Google Free ✅   TURN: Không cần ❌                  │
│  → App hoạt động cho ~70-80% users                          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Production                               │
│  STUN: Google Free hoặc Self-hosted                         │
│  TURN: Coturn trên VPS ($5-10/tháng) ✅                     │
│  → App hoạt động cho 100% users                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 WebSocket Events

#### Client → Server
| Event | Payload | Description |
|-------|---------|-------------|
| `JOIN_ROOM` | `{ roomId, userId, userName }` | Join a room |
| `LEAVE_ROOM` | `{ roomId }` | Leave a room |
| `OFFER` | `{ roomId, targetUserId, sdp }` | WebRTC offer |
| `ANSWER` | `{ roomId, targetUserId, sdp }` | WebRTC answer |
| `ICE_CANDIDATE` | `{ roomId, targetUserId, candidate }` | ICE candidate |
| `CHAT_MESSAGE` | `{ roomId, message }` | Send chat message |
| `TOGGLE_MEDIA` | `{ roomId, mediaType, enabled }` | Media state changed |
| `KICK_USER` | `{ roomId, targetUserId }` | Kick a participant |
| `MUTE_USER` | `{ roomId, targetUserId, muted }` | Mute a participant |

#### Server → Client
| Event | Payload | Description |
|-------|---------|-------------|
| `USER_JOINED` | `{ userId, userName, isHost }` | New user joined |
| `USER_LEFT` | `{ userId }` | User left |
| `ROOM_STATE` | `{ participants[], chat[] }` | Current room state |
| `OFFER` | `{ fromUserId, sdp }` | WebRTC offer received |
| `ANSWER` | `{ fromUserId, sdp }` | WebRTC answer received |
| `ICE_CANDIDATE` | `{ fromUserId, candidate }` | ICE candidate |
| `CHAT_MESSAGE` | `{ fromUserId, userName, message, timestamp }` | New chat message |
| `USER_KICKED` | `{ byUserId }` | You were kicked |
| `USER_MUTED` | `{ byUserId, muted }` | You were muted |
| `ERROR` | `{ code, message }` | Error occurred |

### 5.5 Database Schema (Minimal)

Room state được lưu in-memory (Redis) hoặc local cache, không cần persistent DB trong MVP.

```typescript
interface Room {
  id: string;           // UUID
  hostId: string;      // User ID của host
  participants: Map<string, Participant>;
  createdAt: Date;
  status: 'ACTIVE' | 'ENDED';
}

interface Participant {
  userId: string;
  userName: string;
  isHost: boolean;
  videoEnabled: boolean;
  audioEnabled: boolean;
  joinedAt: Date;
}
```

---

## 6. Out of Scope (Phase 1)

Những tính năng sau **không** có trong MVP:

- [ ] Screen share
- [ ] Recording
- [ ] Waiting room
- [ ] Room password
- [ ] Virtual background / Background blur
- [ ] Raised hand
- [ ] Reconnection handling
- [ ] Breakout room
- [ ] Pin video
- [ ] Layout switching
- [ ] 5+ người/phòng
- [ ] TURN server (Coturn)
- [ ] Persistent chat history

---

## 7. Milestones

| Phase | Features | Timeline |
|-------|----------|----------|
| **Phase 1: MVP** | Room creation, Join, Video, Audio, Chat, Kick, Mute, Gallery View | TBD |
| **Phase 2** | Screen share, Recording, Reconnection | TBD |

---

## 8. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-22 | AI Assistant | Initial draft |
| 1.1 | 2026-07-22 | AI Assistant | Add STUN/TURN infrastructure section |

---

*Document Status: Draft - Pending Approval*
