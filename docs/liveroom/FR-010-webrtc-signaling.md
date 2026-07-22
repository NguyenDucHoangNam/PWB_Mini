# FR-010: WebRTC Peer Connection Flow

## 1. Mô tả

WebRTC được sử dụng để truyền video/audio P2P giữa các participants trong phòng. Signaling (offer/answer/ICE) được relay qua WebSocket server.

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                           Network Topology                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌─────────┐      WebSocket       ┌─────────┐     WebSocket      ┌─────────┐
│   │  Peer A │ ◄──────────────────► │ Server │ ◄──────────────────► │  Peer B │
│   │         │      (signaling)      │         │     (signaling)     │         │
│   └────┬────┘                      └─────────┘                      └────┬────┘
│        │                                                                │
│        │◄─────────────────── P2P Data (Video/Audio) ──────────────────►│
│        │                         (Direct)                                │
│        │                                                                │
│        │                        ┌─────────┐                           │
│        └───────────────────────► │   STUN  │ ◄───────────────────────────┘
│                                │ Server  │   (NAT traversal / ICE)
│                                └─────────┘
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ Peer A  │                    │ Server  │                    │ Peer B  │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                                │                                │
     │──── WebSocket: OFFER ─────────►│──── WebSocket: OFFER ─────────►│
     │                                │                                │
     │◄──── WebSocket: ANSWER ◄──────│◄──── WebSocket: ANSWER ◄──────│
     │                                │                                │
     │──── ICE Candidates ───────────►│──── ICE Candidates ───────────►│
     │                                │                                │
     │◄══════════════ Direct P2P (Video/Audio) ═══════════════════════►│
     │                                │                                │
```

---

## 3. ICE Servers Config

```typescript
// frontend/src/features/liveroom/lib/webrtc-peer-manager.ts
const ICE_SERVERS: RTCIceServer[] = [
  { urls: "stun:stun.l.google.com:19302" },
];
```

### STUN Server Purpose

```
Local Network          Public Internet           Remote Network
┌─────────┐             ┌─────────┐               ┌─────────┐
│ Peer A │◄──STUN────►│  STUN  │◄──STUN──────►│ Peer B │
│  LAN   │   Request  │ Server  │   Request     │  LAN   │
│  IP    │───────────►│         │◄──────────────│        │
└─────────┘            └─────────┘               └─────────┘
      │                                              │
      │         Public IP:Port Discovery            │
      └──────────────────────────────────────────────┘
```

---

## 4. Signaling Flow (Step by Step)

### Phase 1: Connection Setup

```
Peer A (caller)                          Peer B (callee)
      │                                          │
      │  1. Create RTCPeerConnection             │
      │                                          │
      │  2. Add local media tracks               │
      │                                          │
      │  3. Create SDP Offer                     │
      │  ┌──────────────────────────┐            │
      │  │ v=0                      │            │
      │  │ o=- 0 0 IN IP4 ...      │            │
      │  │ m=video 9 UDP/TLS/RTP... │            │
      │  │ a=rtpmap:96 VP8/90000   │            │
      │  │ ...                      │            │
      │  └──────────────────────────┘            │
      │                                          │
      │  4. setLocalDescription(offer)           │
      │                                          │
      │  5. sendSignalOffer(toUserId, {sdp}) ────►│
      │                                          │
      │                                          │  6. Receive OFFER
      │                                          │
      │                                          │  7. setRemoteDescription(offer)
      │                                          │
      │                                          │  8. Create SDP Answer
      │                                          │
      │                                          │  9. setLocalDescription(answer)
      │                                          │
      │◄────────────────────────────────────────│  10. sendSignalAnswer(toUserId, {sdp})
      │                                          │
      │  11. Receive ANSWER                      │
      │                                          │
      │  12. setRemoteDescription(answer)        │
```

### Phase 2: ICE Candidate Exchange

```
Peer A                                    Peer B
    │                                        │
    │  ICE Candidate (host)                  │
    │  {candidate: "udp 211...</candidate>  │
    │   sdpMid: "0", sdpMLineIndex: 0}      │
    │                                        │
    │  sendSignalIce(toUserId, {...}) ───────►│
    │                                        │
    │                                        │  addIceCandidate(candidate)
    │                                        │
    │◄────────────────────────────────────────│  ICE Candidate (reflexive)
    │                                        │
    │  addIceCandidate(candidate)             │
    │                                        │
    │══════════ P2P Connection Established ══│
```

### Phase 3: Media Streaming

```
┌─────────────────────────────────────────────────────┐
│  RTCPeerConnection                                 │
│                                                     │
│  ┌─────────────┐      ICE        ┌─────────────┐│
│  │ Local Track │◄═══════════════►│Remote Track ││
│  │  (camera)   │    Channel      │  (display)   ││
│  └─────────────┘                  └─────────────┘│
│        │                               │            │
│        ▼                               ▼            │
│  ┌───────────────────────────────────────────┐     │
│  │ MediaStream                               │     │
│  │ Track: video (VP8)                        │     │
│  │                                           │     │
│  │ Track: audio (opus)                       │     │
│  └───────────────────────────────────────────┘     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 5. Backend WebSocket Endpoints

### Message Mapping (STOMP)

| Client Send | Server Action | Destination |
|------------|---------------|------------|
| `/app/room/{roomCode}/signal/offer` | Relay to target user | `/user/{userId}/queue/room/{roomCode}/signal/offer` |
| `/app/room/{roomCode}/signal/answer` | Relay to target user | `/user/{userId}/queue/room/{roomCode}/signal/answer` |
| `/app/room/{roomCode}/signal/ice` | Relay to target user | `/user/{userId}/queue/room/{roomCode}/signal/ice` |

### Controller Implementation

```java
@MessageMapping("/room/{roomCode}/signal/offer")
public void relayOffer(
    @DestinationVariable("roomCode") String roomCode,
    @AuthenticationPrincipal AuthenticatedUser user,
    Map<String, Object> payload) {

    String toUserId = payload.get("toUserId"); // target peer

    Map<String, Object> forwarded = Map.of(
        "type", "OFFER",
        "roomCode", roomCode,
        "fromUserId", user.getId(),
        "toUserId", toUserId,
        "payload", payload.get("payload")
    );

    messagingTemplate.convertAndSendToUser(
        toUserId,
        "/queue/room/" + roomCode + "/signal/offer",
        forwarded
    );
}
```

---

## 6. Frontend WebRTC Manager

### Class: `WebRTCPeerManager`

```typescript
export class WebRTCPeerManager {
  private readonly roomCode: string;
  private readonly localUserId: string;
  private readonly peers: Map<string, PeerEntry> = new Map();

  async addPeer(localStream: MediaStream, remoteUserId: string): Promise<void> {
    // 1. Create peer connection
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    // 2. Add local tracks
    localStream.getTracks().forEach(track => {
      pc.addTrack(track, localStream);
    });

    // 3. Handle remote tracks
    pc.ontrack = (event) => {
      const remoteStream = new MediaStream(event.streams[0].getTracks());
      this.onRemoteStream(remoteUserId, remoteStream);
    };

    // 4. Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignalIce(this.roomCode, remoteUserId, {
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
        });
      }
    };

    // 5. Create and send offer
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendSignalOffer(this.roomCode, remoteUserId, { sdp: offer.sdp });
  }

  async handleRemoteOffer(event: PeerSignalEnvelope): Promise<void> {
    // 1. Create peer connection if not exists
    // 2. setRemoteDescription(offer)
    // 3. Create answer
    // 4. setLocalDescription(answer)
    // 5. Send answer
  }

  async handleRemoteAnswer(event: PeerSignalEnvelope): Promise<void> {
    // setRemoteDescription(answer)
  }

  async handleRemoteIce(event: PeerSignalEnvelope): Promise<void> {
    // addIceCandidate(candidate)
  }
}
```

---

## 7. ICE Buffering

Khi nhận ICE candidate trước khi có remote description:

```typescript
private async handleRemoteIce(event: PeerSignalEnvelope): Promise<void> {
  const entry = this.peers.get(event.fromUserId);

  // Buffer if no remote description yet
  if (!entry.hasRemoteDescription) {
    entry.iceBuffer.push(candidate);
    return;
  }

  // Add immediately
  await entry.connection.addIceCandidate(candidate);
}

// Flush buffer when remote description is set
private flushIceBuffer(remoteUserId: string): void {
  const entry = this.peers.get(remoteUserId);
  for (const candidate of entry.iceBuffer) {
    entry.connection.addIceCandidate(candidate);
  }
  entry.iceBuffer = [];
}
```

---

## 8. Peer Events Flow

### Subscribe: `/topic/room/{roomCode}/peers`

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

### Hook: `usePeerSignaling`

```typescript
const usePeerSignaling = (manager: WebRTCPeerManager, localStream: MediaStream) => {
  useEffect(() => {
    const sub = subscribeRoomPeerEvents(roomCode, async (event) => {
      if (event.type === "PEER_JOINED") {
        await manager.addPeer(localStream, event.userId);
      } else if (event.type === "PEER_LEFT") {
        manager.removePeer(event.userId);
      }
    });
    return () => sub.unsubscribe();
  }, [manager, localStream]);
};
```

---

## 9. Media Switching

Thay đổi mic/camera không cần tạo lại connection:

```typescript
setLocalStreamForAllPeers(newStream: MediaStream): void {
  for (const entry of this.peers.values()) {
    // Replace track with same kind
    for (const track of newStream.getTracks()) {
      const sender = entry.connection.getSenders()
        .find(s => s.track?.kind === track.kind);
      if (sender) {
        sender.replaceTrack(track);
      }
    }
  }
}
```

---

## 10. Related Documentation

- [FR-009: Media Controls](./FR-009-media-controls.md)
- [FR-011: Realtime Events](./FR-011-realtime-events.md)
