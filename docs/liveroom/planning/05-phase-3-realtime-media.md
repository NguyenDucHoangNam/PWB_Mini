# Phase 3: Realtime + Media

**Thời gian**: 7-10 ngày
**Mục tiêu**: WebRTC mic/cam, chat real-time, WS ổn định
**Doc tham chiếu chính**: `liveroom-api-spec.md` §2.4-2.5, `liveroom-ws-protocol.md` §4.2-4.4, `liveroom-jobs.md` §4-5

---

## 🤖 AGENT BRIEFING

> **Copy toàn bộ khối code dưới đây → paste cho AI agent (Cursor).**
> Agent sẽ tự đọc docs và làm theo đúng quy trình.

```
Bạn là Senior Backend Dev + Frontend Dev cho dự án PWB MiNi (Spring Boot multi-module + Next.js 14 + TypeScript).

Project: d:\Learning\Project\PWB_MiNi\
Module: liveroom (Backend/modules/liveroom/, FE: frontend/src/features/liveroom/)

## 8 WORKSPACE RULES (BẮT BUỘC TUÂN THỦ):
1. senior-dev-coding-standards.mdc (SOLID, Clean Code)
2. prefer-lombok-backend.mdc (@Slf4j, @RequiredArgsConstructor, @Builder)
3. no-code-comments-backend.mdc
4. no-code-comments-frontend.mdc
5. no-hardcoded-messages-backend.mdc
6. no-auto-create-tests-backend.mdc
7. no-auto-commit-push-backend.mdc
8. respond-in-vietnamese.mdc

## PHASE 3 LÀ GÌ:
Realtime - WebRTC peer-to-peer voice/video + chat messages real-time + WS scope enforcement + reconnect + multi-tab detection. WebRTC signaling + Chat REST + 3 WS events + 2 jobs + 5 FE components + i18n ~20 keys.

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md
2. docs/liveroom/planning/00-reading-guide.md
3. docs/liveroom/planning/05-phase-3-realtime-media.md (FILE NÀY - chi tiết 14 tasks)
4. docs/liveroom/planning/10-codebase-templates.md
5. docs/liveroom/planning/08-self-review-checklist.md
6. docs/liveroom/api-spec.md §2.4 (WebRTC signaling), §2.5 (chat POST/GET)
7. docs/liveroom/ws-protocol.md §4.2-4.4 (chat events + WebRTC signal)
8. docs/liveroom/jobs.md §4 (ParticipantReconnectTimeout), §5 (IdleGhostIndicator)
9. docs/liveroom/screen-inventory.md SC-06 (In-Room với media)
10. docs/liveroom/business-requirements.md R-MEDIA-01..12, R-CHAT-01..08, R-WS-SCOPE-01..02
11. docs/liveroom/state-ui-mapping.md §2 (state ↔ UI cho media)
12. docs/liveroom/i18n-keys.md (20 keys mới)

## 14 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 3.1 - WebRTC signaling endpoint
DTO SignalRequest, SignalingService (chỉ forward payload, không xử lý media), SignalingController (POST /signal với targetUserId). Forward qua STOMP /user/{userId}/queue/liveroom. Validate ACTIVE only.

### Task 3.2 - POST /chat/messages
DTO SendChatMessageRequest, ChatService.send. Validate ACTIVE + room ACTIVE + content 1-500 chars. Escape HTML (XSS prevention). Persist ChatMessage với sessionCycleId. WS broadcast CHAT_MESSAGE_RECEIVED.

### Task 3.3 - GET /chat/messages
Load 200 messages gần nhất (DESC by sentAt) của cycle hiện tại. R-CHAT-06 v1.8: chỉ load cycle hiện tại.

### Task 3.4 - WS: 3 chat events
CHAT_MESSAGE_RECEIVED, CHAT_HISTORY_SNAPSHOT (user mới join), CHAT_USER_TYPING (optional).

### Task 3.5 - WS scope enforcement
ChannelInterceptor: subscribe /topic/liveroom/{roomId} → check user có ACTIVE/PENDING/APPROVED mới được subscribe.

### Task 3.6 - ParticipantReconnectTimeoutJob
@Scheduled(30s) + @SchedulerLock. State RECONNECTING + left_at > 60s → ENDED + count-1 + broadcast PARTICIPANT_LEFT.

### Task 3.7 - IdleGhostIndicatorJob
@Scheduled(60s) + @SchedulerLock. State ACTIVE + last_activity_at > 5min → isIdleGhost=true + broadcast PARTICIPANT_IDLE.

### Task 3.8 - FE: WebRTC peer manager
File: lib/webrtc-peer-manager.ts. Class WebRTCPeerManager với Map<userId, RTCPeerConnection>, methods: createPeer (STUN: stun.l.google.com:19302), createOffer/handleAnswer/handleOffer/handleCandidate, closeAll. File: hooks/use-peer-signaling.ts để gửi/nhận signal qua STOMP.

### Task 3.9 - FE: Media tile
File: components/MediaTile.tsx. Video element + mic toggle + camera toggle + name label + idle indicator (zZz) + connection quality indicator.

### Task 3.10 - FE: Chat panel
File: components/ChatPanel.tsx. Message list infinite scroll + input (max 500 chars) + auto-scroll bottom + render HTML-escaped content.

### Task 3.11 - FE: WS reconnect
File: lib/stomp-client.ts. Client @stomp/stompjs với reconnectDelay (exponential backoff) + heartbeat + Authorization header + onConnect onWebSocketClose.

### Task 3.12 - FE: Multi-tab detection
File: hooks/use-multi-tab-detection.ts. BroadcastChannel kiểm tra 2 tab cùng user.

### Task 3.13 - i18n keys (~20 keys)
liveroom.chat.placeholder, liveroom.chat.send, liveroom.media.mic_on, liveroom.media.mic_off, liveroom.media.camera_on, liveroom.media.camera_off, liveroom.media.idle_indicator, liveroom.signal.connecting, ... (20 keys).

### Task 3.14 - E2E test 2 browsers voice chat
2 browsers vào phòng, enable mic+cam, voice chat OK + chat realtime OK + reconnect after network drop OK + 2 tab warning.

## YÊU CẦU ĐẶC BIỆT:
- KHÔNG thêm comment
- KHÔNG hardcode message
- Dùng Lombok đúng rule
- Frontend: "use client", useTranslations, type-safe TypeScript
- HTML escape cho chat content (XSS prevention)
- WebRTC: STUN server fallback cho firewall, không cần TURN cho MVP
- Không commit tự động
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ 14 tasks
- E2E test pass
- Tự check 08-self-review-checklist.md
- Báo cáo file + checklist + warning

## BẮT ĐẦU ĐỌC 12 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 3

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 3.1 WebRTC signaling endpoint | 6 giờ | ⭐⭐⭐⭐ | ⏳ |
| 3.2 POST /chat/messages | 3 giờ | ⭐⭐⭐ | ⏳ |
| 3.3 GET /chat/messages (200 history) | 2 giờ | ⭐⭐ | ⏳ |
| 3.4 WS: 3 chat events | 4 giờ | ⭐⭐⭐ | ⏳ |
| 3.5 WS scope enforcement | 3 giờ | ⭐⭐⭐ | ⏳ |
| 3.6 ParticipantReconnectTimeoutJob | 3 giờ | ⭐⭐ | ⏳ |
| 3.7 IdleGhostIndicatorJob | 3 giờ | ⭐⭐ | ⏳ |
| 3.8 FE: WebRTC peer manager | 8 giờ | ⭐⭐⭐⭐⭐ | ⏳ |
| 3.9 FE: Media tile component | 5 giờ | ⭐⭐⭐ | ⏳ |
| 3.10 FE: Chat panel | 4 giờ | ⭐⭐⭐ | ⏳ |
| 3.11 FE: WS reconnect | 4 giờ | ⭐⭐⭐ | ⏳ |
| 3.12 FE: Multi-tab detection | 2 giờ | ⭐⭐ | ⏳ |
| 3.13 i18n keys (~20 keys) | 1 giờ | ⭐ | ⏳ |
| 3.14 E2E test (2 browsers voice chat) | 3 giờ | ⭐⭐ | ⏳ |

---

## Task 3.1: WebRTC Signaling Endpoint

**Mục tiêu**: Signaling server cho WebRTC peer connection.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.4 (media signaling)
- `liveroom-ws-protocol.md` §4.4
- WebRTC standard (external knowledge)

**Flow**:
```
Client A                    Server                   Client B
   |  POST /signal (offer)       |                          |
   |---------------------------->|                          |
   |                             |------WS signal---->------>|
   |                             |                          |
   |<-----WS signal (answer)----<|<-----POST /signal-------|
   |                             |                          |
   |<---WS ICE candidate-------→|←---WS ICE candidate-----|
   |                             |                          |
   |<===========P2P connection (audio/video)===============>|
```

**Files cần tạo**:
1. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/api/dto/request/SignalRequest.java`
2. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/core/service/SignalingService.java`
3. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/web/SignalingController.java`

**Business rules**:
- R-MEDIA-01: Chỉ ACTIVE participants mới signaling được
- R-MEDIA-02: Signal payload: {type, sdp, candidate}
- R-MEDIA-03: Server chỉ forward, KHÔNG xử lý media

**Template**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalingService {

    private final LiveRoomRealtimeBroadcaster broadcaster;

    public void signal(UUID roomId, UUID targetUserId, SignalRequest request, UUID currentUserId) {
        broadcaster.sendToUser(targetUserId, "WEBRTC_SIGNAL", new SignalPayload(
            request.getType(),
            request.getSdp(),
            request.getCandidate(),
            currentUserId
        ));
    }
}
```

**Acceptance Criteria**:
- [ ] POST /signal {type: "offer", sdp: "..."} → forward to target user
- [ ] POST /signal {type: "candidate", candidate: "..."} → forward
- [ ] Non-ACTIVE user → 403
- [ ] Target user disconnect → 404

---

## Task 3.2: POST /chat/messages

**Mục tiêu**: User gửi chat message.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.5
- `liveroom-business-requirements.md` R-CHAT-01..04
- `liveroom-state-machines.md` §4 (CHAT)

**Business rules**:
- R-CHAT-01: Chỉ ACTIVE participants
- R-CHAT-02: Room phải ACTIVE
- R-CHAT-03: Content max 500 chars, not blank
- R-CHAT-04: Escape HTML/Script (XSS prevention)
- R-CHAT-05: Persist DB (lưu vĩnh viễn, R-CHAT-06 v1.8)
- R-CHAT-07: Broadcast WS event

**Template**:

```java
@Data
@Builder
public class SendChatMessageRequest {
    @NotBlank
    @Size(min = 1, max = 500)
    private String content;
}

@Transactional
public ChatMessageResponse sendChatMessage(UUID roomId, SendChatMessageRequest request, UUID currentUserId) {
    LiveRoom room = liveRoomRepository.findById(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    if (room.getStatus() != RoomStatus.ACTIVE) {
        throw new BusinessException("LIVEROOM_ROOM_ENDED");
    }

    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    if (participant.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_NOT_ACTIVE");
    }

    // Escape HTML
    String safeContent = StringEscapeUtils.escapeHtml4(request.getContent());

    ChatMessage message = ChatMessage.builder()
        .roomId(roomId)
        .userId(currentUserId)
        .userEmail(userService.getUserEmail(currentUserId))
        .content(safeContent)
        .sentAt(OffsetDateTime.now())
        .sessionCycleId(room.getCurrentSessionCycleId())
        .build();
    message = chatMessageRepository.save(message);

    realtimeBroadcaster.broadcast(roomId, "CHAT_MESSAGE_RECEIVED", ChatMessageDto.fromEntity(message));

    return ChatMessageResponse.fromEntity(message);
}
```

**Acceptance Criteria**:
- [ ] User gửi 1 char → 201 Created
- [ ] User gửi 500 chars → 201 Created
- [ ] User gửi 501 chars → 400 VALIDATION_FAILED
- [ ] Empty content → 400 VALIDATION_FAILED
- [ ] HTML escape: `<script>alert(1)</script>` → `&lt;script&gt;alert(1)&lt;/script&gt;`
- [ ] WS event broadcast

---

## Task 3.3: GET /chat/messages (200 history)

**Mục tiêu**: User mới join → load 200 messages gần nhất.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.5
- `liveroom-business-requirements.md` R-CHAT-05

**Template**:

```java
public List<ChatMessageDto> getRecentChatMessages(UUID roomId, UUID currentUserId) {
    LiveRoom room = liveRoomRepository.findById(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    if (participant.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_NOT_ACTIVE");
    }

    // R-CHAT-06 v1.8: chỉ load cycle hiện tại
    List<ChatMessage> messages = chatMessageRepository.findTop200ByRoomIdAndSessionCycleIdOrderBySentAtDesc(
        roomId, room.getCurrentSessionCycleId()
    );

    return messages.stream().map(ChatMessageDto::fromEntity).toList();
}
```

**Acceptance Criteria**:
- [ ] User mới join → nhận 200 messages gần nhất
- [ ] Chỉ load cycle hiện tại (R-CHAT-06)
- [ ] Sorted DESC by sentAt

---

## Task 3.4: WS Events (3 chat events)

**Mục tiêu**: Broadcast 3 chat events.

**3 events**:

| Event | Trigger | Payload |
|---|---|---|
| `CHAT_MESSAGE_RECEIVED` | User gửi message | `{messageId, userId, userEmail, content, sentAt}` |
| `CHAT_HISTORY_SNAPSHOT` | User mới join | `{messages: [...200...], cycleId}` |
| `CHAT_USER_TYPING` (Optional) | User đang gõ | `{userId, timestamp}` |

**Acceptance Criteria**:
- [ ] 3 events broadcast đúng trigger
- [ ] Payload match schema

---

## Task 3.5: WS Scope Enforcement

**Mục tiêu**: Chỉ user ACTIVE mới nhận WS events.

**Doc tham chiếu**:
- `liveroom-ws-protocol.md` §2
- `liveroom-business-requirements.md` R-WS-SCOPE-01

**Logic** (trong WebSocketConfig):

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/topic/liveroom/")) {
                    UUID roomId = extractRoomId(destination);
                    UUID userId = (UUID) accessor.getUser().getPrincipal();
                    // Check user có PENDING/APPROVED request
                    if (!wsScopeService.canSubscribe(roomId, userId)) {
                        throw new MessageDeliveryException("Not authorized");
                    }
                }
            }
            return message;
        }
    });
}
```

**Acceptance Criteria**:
- [ ] ACTIVE user subscribe → OK
- [ ] PENDING user subscribe → OK
- [ ] REJECTED user subscribe → 403
- [ ] EXPIRED/CANCELLED user subscribe → 403

---

## Task 3.6: ParticipantReconnectTimeoutJob

**Mục tiêu**: User disconnect → RECONNECTING → 60s timeout → ENDED.

**Doc tham chiếu**:
- `liveroom-jobs.md` §4

**Template**:

```java
@Scheduled(fixedDelay = 30_000)
@SchedulerLock(name = "ParticipantReconnectTimeoutJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
public void checkReconnectTimeout() {
    OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(60);
    List<Participant> stuck = participantRepository.findByStateAndLeftAtBefore(
        ParticipantState.RECONNECTING, threshold
    );
    for (Participant p : stuck) {
        p.setState(ParticipantState.ENDED);
        p.setLeftAt(OffsetDateTime.now());
        participantRepository.save(p);

        // Update room count
        LiveRoom room = liveRoomRepository.findById(p.getRoomId()).orElseThrow();
        room.setCurrentParticipantCount(Math.max(0, room.getCurrentParticipantCount() - 1));
        liveRoomRepository.save(room);

        realtimeBroadcaster.broadcast(p.getRoomId(), "PARTICIPANT_LEFT", ...);
    }
}
```

**Acceptance Criteria**:
- [ ] User disconnect → state = RECONNECTING
- [ ] Sau 60s không reconnect → state = ENDED
- [ ] Count giảm

---

## Task 3.7: IdleGhostIndicatorJob

**Mục tiêu**: User idle > 5min → tile hiển thị icon zZz.

**Doc tham chiếu**:
- `liveroom-jobs.md` §4
- `liveroom-business-requirements.md` R-MEDIA-12

**Template**:

```java
@Scheduled(fixedDelay = 60_000)
@SchedulerLock(name = "IdleGhostIndicatorJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
public void markIdleGhosts() {
    OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5);
    List<Participant> idle = participantRepository.findByStateAndLastActivityAtBefore(
        ParticipantState.ACTIVE, threshold
    );
    for (Participant p : idle) {
        p.setIsIdleGhost(true);
        participantRepository.save(p);
        realtimeBroadcaster.broadcast(p.getRoomId(), "PARTICIPANT_IDLE", ...);
    }
}
```

**Acceptance Criteria**:
- [ ] User idle > 5min → isIdleGhost = true
- [ ] WS event broadcast
- [ ] Frontend hiển thị icon zZz

---

## Task 3.8-3.10: FE Components

### Task 3.8: WebRTC Peer Manager

**Files**:
- `frontend/src/features/liveroom/lib/webrtc-peer-manager.ts`
- `frontend/src/features/liveroom/hooks/use-peer-signaling.ts`

**Template**:

```typescript
// frontend/src/features/liveroom/lib/webrtc-peer-manager.ts
export class WebRTCPeerManager {
  private peerConnections = new Map<string, RTCPeerConnection>();

  createPeer(targetUserId: string): RTCPeerConnection {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    // Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.sendSignal(targetUserId, {
          type: 'candidate',
          candidate: event.candidate
        });
      }
    };

    this.peerConnections.set(targetUserId, pc);
    return pc;
  }

  async createOffer(targetUserId: string): Promise<RTCSessionDescriptionInit> {
    const pc = this.peerConnections.get(targetUserId) || this.createPeer(targetUserId);
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    return offer;
  }

  // ... handleAnswer, handleCandidate, close, etc

  private sendSignal(targetUserId: string, payload: any) {
    // Send via STOMP /app/liveroom/{roomId}/signal
    // ...
  }
}
```

**Acceptance Criteria**:
- [ ] Peer connection tạo được
- [ ] Offer/answer exchange
- [ ] ICE candidates
- [ ] Audio/video stream establish

---

### Task 3.9: Media Tile Component

**Files**:
- `frontend/src/features/liveroom/components/MediaTile.tsx`

**Components**:
- Video element
- Mic toggle button
- Camera toggle button
- Name label
- Idle indicator (zZz)
- Connection quality indicator

**Acceptance Criteria**:
- [ ] Video render
- [ ] Mic toggle work (broadcast `PARTICIPANT_MEDIA_CHANGED`)
- [ ] Camera toggle work
- [ ] Idle indicator show after 5min

---

### Task 3.10: Chat Panel

**Files**:
- `frontend/src/features/liveroom/components/ChatPanel.tsx`

**Components**:
- Message list (infinite scroll)
- Input box (max 500 chars)
- Send button
- Auto-scroll to bottom

**Acceptance Criteria**:
- [ ] Load 200 messages khi vào
- [ ] Send message → broadcast
- [ ] Receive message → append
- [ ] XSS prevention (HTML escape)

---

## Task 3.11: WS Reconnect

**Mục tiêu**: Auto-reconnect WS khi network drop.

**Template**:

```typescript
// frontend/src/features/liveroom/lib/stomp-client.ts
import { Client } from '@stomp/stompjs';

export function createStompClient(roomId: string, onConnect: () => void) {
  const client = new Client({
    brokerURL: `${process.env.NEXT_PUBLIC_WS_URL}/ws/liveroom`,
    reconnectDelay: 1000, // exponential backoff
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    connectHeaders: {
      Authorization: `Bearer ${getToken()}`
    }
  });

  client.onConnect = () => {
    console.log('WS connected');
    onConnect();
  };

  client.onWebSocketClose = (event) => {
    console.warn('WS disconnected, reconnecting...', event);
  };

  return client;
}
```

**Acceptance Criteria**:
- [ ] Network drop → reconnect tự động
- [ ] Exponential backoff
- [ ] Re-subscribe sau reconnect

---

## Task 3.12: Multi-tab Detection (FE)

**Mục tiêu**: Phát hiện 2 tab cùng user.

**Template** (xem Task 2.11 ở Phase 2).

---

## Task 3.13: i18n Keys

**Mục tiêu**: Add ~20 keys mới.

**Keys**:
- `liveroom.chat.placeholder`
- `liveroom.chat.send`
- `liveroom.media.mic_on`
- `liveroom.media.mic_off`
- `liveroom.media.camera_on`
- `liveroom.media.camera_off`
- `liveroom.media.idle_indicator`
- `liveroom.signal.connecting`
- ... (~20 keys)

---

## Task 3.14: E2E Test

**Mục tiêu**: Test 2 browsers voice chat + chat.

**Test scenario**:
1. Browser A: Vào phòng, enable mic + camera
2. Browser B: Vào phòng, enable mic + camera
3. Browser A: Thấy video của B (và ngược lại)
4. Browser A: Gửi chat "Hello"
5. Browser B: Nhận chat realtime
6. Browser B: Gửi chat "Hi there"
7. Browser A: Nhận
8. Browser A: Tắt mic → B thấy mic icon off
9. Browser A: Disconnect → B thấy indicator "reconnecting"
10. Browser A: Không reconnect → B thấy A biến mất sau 60s

**Acceptance Criteria**:
- [ ] Voice chat 2 browsers OK
- [ ] Chat realtime OK
- [ ] Reconnect works

---

## 🚦 Definition of Done Phase 3

- [ ] Tất cả 14 tasks DONE
- [ ] WebRTC peer connection hoạt động
- [ ] Chat realtime + 200 history
- [ ] WS reconnect ổn định
- [ ] 2 jobs chạy đúng
- [ ] i18n ~20 keys
- [ ] E2E test pass

---

**Cập nhật**: 2026-07-26
