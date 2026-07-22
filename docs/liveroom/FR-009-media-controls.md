# FR-009: Điều Khiển Media (Mic/Camera)

## 1. Mô tả

Cho phép participant toggle microphone và camera trong phòng. Trạng thái media được sync với các participants khác qua WebSocket.

---

## 2. UI Layout

### Media Controls Bar

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
│  │   ┌────────┐  ┌────────┐  ┌────────┐              │   │
│  │   │  🎤    │  │  📹    │  │  🔊    │  [Device▼] │   │
│  │   │  Mic   │  │  Cam   │  │ Spkr  │              │   │
│  │   │  ON    │  │  OFF   │  │  80%  │              │   │
│  │   │(green) │  │ (red)  │  │       │              │   │
│  │   └────────┘  └────────┘  └────────┘              │   │
│  │                                                      │   │
│  │                                    [Leave Room]      │   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Control Button States

| Control | Default | Muted/Off | Hover | Active |
|---------|--------|-----------|-------|--------|
| Mic | Gray bg, white icon | Red bg, white icon, slash | Darker shade | Scale down |
| Camera | Gray bg, white icon | Red bg, white icon, slash | Darker shade | Scale down |
| Speaker | Gray bg, white icon | N/A | Darker shade | N/A |

### Device Picker Dropdown

```
┌────────────────────────────────┐
│  🎤 Microphone                 │
│  ─────────────────────────────│
│  ● Built-in Microphone         │ ← selected
│  ○ External USB Mic            │
│  ○ AirPods Pro                 │
└────────────────────────────────┘
┌────────────────────────────────┐
│  📹 Camera                     │
│  ─────────────────────────────│
│  ○ FaceTime HD Camera         │
│  ● Logitech Webcam            │ ← selected
└────────────────────────────────┘

         [Apply]
```

### Media Tile States

```
┌─────────────────────────────────────────┐
│                                          │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │     [Video Stream]              │   │
│  │        or Avatar                │   │
│  │                                 │   │
│  │                                 │   │
│  │                                 │   │
│  │  ┌──────────────────────────┐  │   │
│  │  │ 👤 John         🎤 📹    │  │   │
│  │  │             (you)        │  │   │
│  │  └──────────────────────────┘  │   │
│  └─────────────────────────────────┘   │
│                                          │
│  Icons Legend:                           │
│  🎤 = Mic ON (green)                    │
│  🎤̸ = Mic OFF (red with slash)          │
│  📹 = Camera ON (green)                  │
│  📹̸ = Camera OFF (red with slash)        │
│                                          │
└─────────────────────────────────────────┘
```

### Media Tile Combinations

| Camera | Mic | Visual |
|--------|-----|--------|
| ON | ON | Video + green mic icon + green camera icon |
| ON | OFF | Video + red mic icon (slash) + green camera icon |
| OFF | ON | Avatar + green mic icon + red camera icon (slash) |
| OFF | OFF | Avatar + red mic icon (slash) + red camera icon (slash) |

---

## 3. API Endpoints (Backend)

### PATCH `/api/v1/live-rooms/{roomCode}/participants/me/media`

**Authorization**: Role `USER`, `PRO`, hoặc `ADMIN`

**Request Body** (`MediaStateUpdateRequest`):
```json
{
  "micMuted": true,      // true = muted, false = unmuted
  "cameraOff": true      // true = off, false = on
}
```

**Response** (`ParticipantSummaryResponse`):
```json
{
  "success": true,
  "data": {
    "participantId": "uuid",
    "userId": "uuid",
    "displayName": "John Doe",
    "roleAtJoin": "USER",
    "joinedAt": "2026-07-22T10:00:00Z",
    "micMuted": true,
    "cameraOff": true,
    "lastSeenAt": "2026-07-22T10:05:00Z"
  },
  "message": "Media state updated"
}
```

---

## 4. Backend Flow

```java
// LiveRoomParticipantServiceImpl.updateMediaState()
@Transactional
public LiveRoomParticipant updateMediaState(
    UUID userId, String roomCode, boolean micMuted, boolean cameraOff) {

    // 1. Find active participant
    LiveRoomParticipantJpaEntity entity =
        participantJpaRepository.findActiveByRoomAndUser(roomCode, userId)
            .orElseThrow(LIVEROOM_NOT_JOINED);

    // 2. Update state
    LiveRoomParticipant domain = participantMapper.toDomain(entity);
    domain.updateMediaState(micMuted, cameraOff);

    // 3. Save
    participantJpaRepository.save(participantMapper.toEntity(domain, entity));

    // 4. Publish event → Broadcast MEDIA_STATE_CHANGED
    eventPublisher.publishEvent(new MediaStateChangedEvent(...));

    return participantMapper.toDomain(entity);
}
```

---

## 5. Frontend Implementation

### Files liên quan

| File | Mô tả |
|------|--------|
| `api/participants.ts` | `updateMyMedia()`, `useUpdateMyMedia()` |
| `components/media-controls.tsx` | Toggle buttons |
| `components/device-picker.tsx` | Device selection |
| `hooks/use-live-room-media.ts` | Toggle logic |
| `hooks/use-media-devices.ts` | Device access |
| `stores/use-live-room-media-store.ts` | State management |

### Component: `MediaControls`

```tsx
const MediaControls = () => {
  const { micMuted, cameraOff, toggleMic, toggleCamera } = useLiveRoomMedia();

  return (
    <div className="flex gap-2">
      {/* Mic toggle */}
      <button
        onClick={toggleMic}
        className={micMuted ? "bg-red-600" : "bg-gray-600"}
      >
        {micMuted ? "🎤̸" : "🎤"} {micMuted ? "Mic Off" : "Mic On"}
      </button>

      {/* Camera toggle */}
      <button
        onClick={toggleCamera}
        className={cameraOff ? "bg-red-600" : "bg-gray-600"}
      >
        {cameraOff ? "📹̸" : "📹"} {cameraOff ? "Cam Off" : "Cam On"}
      </button>
    </div>
  );
};
```

### Hook: `useLiveRoomMedia`

```typescript
const useLiveRoomMedia = () => {
  const store = useLiveRoomMediaStore();
  const { acquireStream, releaseStream } = useMediaDevices();
  const updateMedia = useUpdateMyMedia();

  const toggleMic = async () => {
    const newMuted = !store.micMuted;

    // 1. Update local track
    store.getStream()?.getAudioTracks().forEach(
      track => { track.enabled = !newMuted; }
    );

    // 2. Update store
    store.setMicMuted(newMuted);

    // 3. Sync to backend
    await updateMedia.mutateAsync({
      roomCode,
      body: { micMuted: newMuted, cameraOff: store.cameraOff }
    });
  };

  const toggleCamera = async () => {
    const newOff = !store.cameraOff;

    // 1. Update local track
    store.getStream()?.getVideoTracks().forEach(
      track => { track.enabled = !newOff; }
    );

    // 2. Update store
    store.setCameraOff(newOff);

    // 3. Sync to backend
    await updateMedia.mutateAsync({
      roomCode,
      body: { micMuted: store.micMuted, cameraOff: newOff }
    });
  };

  return { toggleMic, toggleCamera, ... };
};
```

---

## 6. Zustand Store

```typescript
interface LiveRoomMediaStore {
  // State
  localStream: MediaStream | null;
  micMuted: boolean;
  cameraOff: boolean;
  errorMessage: string | null;
  remotePeers: RemotePeerStream[];

  // Actions
  upsertRemotePeer: (peer: RemotePeerStream) => void;
  removeRemotePeer: (userId: string) => void;
  reset: () => void;

  // Helpers
  getStream: () => MediaStream | null;
}

// applyTrackMutedFlag - sync track state with store
const applyTrackMutedFlag = (stream: MediaStream, micMuted: boolean, cameraOff: boolean) => {
  stream.getAudioTracks().forEach(t => { t.enabled = !micMuted; });
  stream.getVideoTracks().forEach(t => { t.enabled = !cameraOff; });
};
```

---

## 7. WebSocket Events

### Subscribe: `/topic/room/{roomCode}/participants`

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

### Handle in MediaStage

```tsx
useEffect(() => {
  const sub = subscribeRoomMediaState(roomCode, (event) => {
    if (event.userId === localUserId) {
      // Update own state from server sync
      store.setMicMuted(event.micMuted);
      store.setCameraOff(event.cameraOff);
    } else {
      // Remote peer media change
      if (event.cameraOff && event.micMuted) {
        removeRemotePeer(event.userId);
      }
    }
  });
  return () => sub.unsubscribe();
}, [roomCode]);
```

---

## 8. Media Tile States

| State | Visual |
|-------|--------|
| Camera ON, Mic ON | Video stream + no overlay |
| Camera ON, Mic OFF | Video stream + red mic icon |
| Camera OFF, Mic ON | Avatar + green mic icon |
| Camera OFF, Mic OFF | Avatar + red mic icon |

---

## 9. Device Picker

**Component**: `DevicePicker`

```tsx
const DevicePicker = () => {
  const { devices, currentDeviceIds, setDevice } = useMediaDevices();

  return (
    <div>
      <select
        value={currentDeviceIds.audio}
        onChange={(e) => setDevice("audio", e.target.value)}
      >
        {devices.audio.map((d) => (
          <option key={d.deviceId} value={d.deviceId}>{d.label}</option>
        ))}
      </select>

      <select
        value={currentDeviceIds.video}
        onChange={(e) => setDevice("video", e.target.value)}
      >
        {devices.video.map((d) => (
          <option key={d.deviceId} value={d.deviceId}>{d.label}</option>
        ))}
      </select>
    </div>
  );
};
```

---

## 10. Related Documentation

- [FR-010: Participant Management](./FR-010-participant-management.md)
- [FR-011: Realtime Events](./FR-011-realtime-events.md)
