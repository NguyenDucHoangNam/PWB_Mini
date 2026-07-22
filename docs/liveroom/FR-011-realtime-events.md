# FR-011: Realtime Events (WebSocket/STOMP)

## 1. Mô tả

Tài liệu này mô tả tất cả WebSocket events được sử dụng trong Liveroom module để sync realtime giữa clients và server.

---

## 2. UI Layout - Realtime Updates

### Toast Notifications

```
┌─────────────────────────────────────┐
│  🔔 New participant                 │
│     John joined the room             │
│                                     │
│  [View] [Dismiss]                   │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  🔔 New join request               │
│     Jane wants to join              │
│                                     │
│  [View] [Dismiss]                   │
└─────────────────────────────────────┘
```

### Participants List - Live Update

```
┌────────────────────────────────────────┐
│  Participants (3)                       │
│  ──────────────────────────────────── │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ 👤 John (Host)          Joined 2m │ │
│  └──────────────────────────────────┘ │
│  ┌──────────────────────────────────┐ │
│  │ 👤 Alice                 Joined 1m │ │
│  └──────────────────────────────────┘ │
│  ┌──────────────────────────────────┐ │
│  │ 👤 You                       Joined │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ← Animates when participant joins/leaves │
└────────────────────────────────────────┘
```

### Media State - Live Update

```
┌────────────────────────────────────────┐
│  MediaTile John                        │
│  ┌────────────────────────────────┐  │
│  │                                │  │
│  │      [Video Stream]           │  │
│  │                                │  │
│  │  ┌──────────────────────────┐  │  │
│  │  │ 👤 John         🎤 📹    │  │  │
│  │  │              (mic off)   │  │  │
│  │  └──────────────────────────┘  │  │
│  └────────────────────────────────┘  │
│                                        │
│  ← Icon changes when John toggles mic  │
└────────────────────────────────────────┘
```

### Join Request Queue - Live Update

```
┌────────────────────────────────────────┐
│  Join Requests (2)                     │
│  ──────────────────────────────────── │
│                                        │
│  ┌────────────────────────────────┐  │
│  │ 🟡 Jane - "Can I join?"        │  │
│  │ [✓ Approve]    [✗ Decline]   │  │
│  └────────────────────────────────┘  │
│                                        │
│  ← New request animates in from top   │
│  ← When approved, slides out left     │
└────────────────────────────────────────┘
```

---

## 3. WebSocket Configuration

### Backend (Spring)

```java
// LiveRoomWebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/liveroom")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

### Frontend (STOMP + SockJS)

```typescript
// frontend/src/features/liveroom/api/ws.ts
const client = new Client({
  webSocketFactory: () => new SockJS(WS_URL),
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  reconnectDelay: 5000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
});
```

---

## 4. Event Topics Summary

| Topic/Queue | Type | Mô tả |
|-------------|------|--------|
| `/topic/room/{roomCode}/participants` | Broadcast | Participant join/left/media changed |
| `/topic/room/{roomCode}/peers` | Broadcast | Peer join/left (for WebRTC) |
| `/topic/room/{roomCode}/join-requests` | Broadcast | New join request (for host) |
| `/user/queue/join-requests` | Push to user | Request decision (approved/rejected) |
| `/user/queue/room/{roomCode}/signal/offer` | Push to user | WebRTC offer |
| `/user/queue/room/{roomCode}/signal/answer` | Push to user | WebRTC answer |
| `/user/queue/room/{roomCode}/signal/ice` | Push to user | ICE candidate |

---

## 5. Broadcast Events (Topic)

### 5.1 PARTICIPANT_JOINED

```typescript
interface ParticipantJoinedWsEvent {
  type: "PARTICIPANT_JOINED";
  roomCode: string;
  userId: string;
  displayName: string;
  roleAtJoin: string;
  currentCount: number;
  maxParticipants: number;
  availableSlots: number;
  timestamp: string;
}
```

**Subscribers**: Tất cả participants trong room

### 5.2 PARTICIPANT_LEFT

```typescript
interface ParticipantLeftWsEvent {
  type: "PARTICIPANT_LEFT";
  roomCode: string;
  userId: string;
  displayName: string;
  currentCount: number;
  maxParticipants: number;
  availableSlots: number;
  timestamp: string;
}
```

**Subscribers**: Tất cả participants trong room

### 5.3 MEDIA_STATE_CHANGED

```typescript
interface MediaStateChangedWsEvent {
  type: "MEDIA_STATE_CHANGED";
  roomCode: string;
  userId: string;
  displayName: string;
  micMuted: boolean;
  cameraOff: boolean;
  timestamp: string;
}
```

**Subscribers**: Tất cả participants trong room

### 5.4 JOIN_REQUEST_CREATED

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

**Subscribers**: Host của phòng

---

## 6. Push Events (User Queue)

### 6.1 JOIN_REQUEST_DECIDED

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

**Subscribers**: User đã gửi join request

### 6.2 PEER_JOINED / PEER_LEFT

```typescript
interface PeerJoinedWsEvent {
  type: "PEER_JOINED";
  roomCode: string;
  userId: string;
  displayName: string;
  timestamp: string;
}

interface PeerLeftWsEvent {
  type: "PEER_LEFT";
  roomCode: string;
  userId: string;
  timestamp: string;
}
```

**Subscribers**: Tất cả participants trong room

---

## 7. Signaling Events (P2P via Queue)

### 7.1 OFFER

```typescript
interface PeerSignalEnvelope {
  type: "OFFER";
  roomCode: string;
  fromUserId: string;
  toUserId: string;
  payload: {
    sdp: string;
  };
}
```

### 7.2 ANSWER

```typescript
interface PeerSignalEnvelope {
  type: "ANSWER";
  roomCode: string;
  fromUserId: string;
  toUserId: string;
  payload: {
    sdp: string;
  };
}
```

### 7.3 ICE

```typescript
interface PeerSignalEnvelope {
  type: "ICE";
  roomCode: string;
  fromUserId: string;
  toUserId: string;
  payload: {
    candidate: string;
    sdpMid: string | null;
    sdpMLineIndex: number | null;
  };
}
```

---

## 8. Backend Broadcaster

```java
@Component
@RequiredArgsConstructor
public class LiveRoomRealtimeBroadcaster {

    // Broadcast to topic
    public void broadcastParticipantJoined(...) {
        String destination = "/topic/room/" + roomCode + "/participants";
        messagingTemplate.convertAndSend(destination, payload);
    }

    // Push to specific user queue
    public void pushJoinRequestDecided(...) {
        String destination = "/queue/user/" + userId + "/join-requests";
        messagingTemplate.convertAndSendToUser(userId, destination, payload);
    }
}
```

### Event Publishing Pattern

```java
// Service publishes event after transaction commit
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onJoinRequestCreated(JoinRequestCreatedEvent event) {
    broadcaster.broadcastJoinRequestCreated(...);
}
```

---

## 9. Frontend Subscription

```typescript
// Subscribe to topic
const sub = subscribeRoomParticipants(roomCode, (event) => {
  if (event.type === "PARTICIPANT_JOINED") {
    handleParticipantJoined(event);
  } else if (event.type === "PARTICIPANT_LEFT") {
    handleParticipantLeft(event);
  } else if (event.type === "MEDIA_STATE_CHANGED") {
    handleMediaStateChanged(event);
  }
});

return () => sub.unsubscribe();
```

---

## 10. Event Flow Diagrams

### Participant Join Flow

```
User clicks "Join"
    ↓
API: POST /join-requests or direct join
    ↓
Service: Create participant record
    ↓
After Commit: Publish ParticipantJoinedEvent
    ↓
Broadcaster: Send to /topic/room/{roomCode}/participants
    ↓
All clients receive PARTICIPANT_JOINED
    ↓
WebRTCPeerManager.addPeer(remoteUserId, localStream)
    ↓
New peer connection established
```

### Join Request Flow (Private Room)

```
Guest sends join request
    ↓
API: POST /join-requests
    ↓
Service: Create request + publish JoinRequestCreatedEvent
    ↓
Broadcaster: Send to /topic/room/{roomCode}/join-requests
    ↓
Host receives notification
    ↓
Host clicks Approve
    ↓
API: POST /join-requests/{id}/approve
    ↓
Service: Approve request + promote to participant
    ↓
Publish JoinRequestDecidedEvent (to guest queue)
    ↓
Publish ParticipantJoinedEvent (to room topic)
    ↓
Guest receives APPROVED → navigate to room
All participants receive PARTICIPANT_JOINED
```

---

## 11. Related Documentation

- [FR-006: Join Private Room](./FR-006-join-private-room.md)
- [FR-007: Approve Join Request](./FR-007-approve-join-request.md)
- [FR-009: Media Controls](./FR-009-media-controls.md)
- [FR-010: WebRTC Signaling](./FR-010-webrtc-signaling.md)
