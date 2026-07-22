# FR-012: Data Models & Validation

## 1. Mô tả

Tài liệu này mô tả chi tiết domain models, entities, và validation rules trong Liveroom module.

---

## 2. Domain Models

### 2.1 LiveRoom

```java
@Getter
public final class LiveRoom {

    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 5;

    private final UUID id;
    private final UUID hostUserId;
    private final String roomCode;
    private final Instant createdAt;

    private String title;
    private String description;
    private LiveRoomMode mode;
    private int maxParticipants;
    private LiveRoomStatus status;
    private int currentParticipantCount;
    private Instant scheduledStartAt;
    private Instant startedAt;
    private Instant endedAt;

    // Factory methods
    public static LiveRoom create(...) { }
    public static LiveRoom rehydrate(...) { }

    // Business methods
    public void updateSettings(...) { }
    public void markStarted() { }
    public void markEnded() { }
    public void incrementParticipants() { }
    public void decrementParticipants() { }
    public boolean isHost(UUID userId) { }

    // Validation
    private static void validateTitle(String title) { }
    private static void validateCapacity(int maxParticipants) { }
    private static void validateRoomCode(String roomCode) { }
}
```

### 2.2 LiveRoomParticipant

```java
@Getter
public final class LiveRoomParticipant {

    private static final int MIN_DISPLAY_NAME_LENGTH = 1;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final boolean DEFAULT_MIC_MUTED = true;
    private static final boolean DEFAULT_CAMERA_OFF = true;

    private final UUID id;
    private final String roomCode;
    private final UUID userId;
    private final String displayName;
    private final String roleAtJoin;
    private final Instant joinedAt;

    private Instant leftAt;
    private boolean micMuted;
    private boolean cameraOff;
    private Instant lastSeenAt;

    // Factory methods
    public static LiveRoomParticipant join(...) { }
    public static LiveRoomParticipant rehydrate(...) { }

    // Business methods
    public void markLeft(Instant leftAt) { }
    public void updateMediaState(boolean micMuted, boolean cameraOff) { }
    public void touchLastSeen() { }
    public boolean isActive() { }
    public boolean belongsTo(UUID userId) { }
}
```

### 2.3 LiveRoomJoinRequest

```java
@Getter
public final class LiveRoomJoinRequest {

    private static final int MIN_DISPLAY_NAME_LENGTH = 1;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final String roomCode;
    private final UUID userId;
    private final String displayName;
    private final String message;
    private final Instant createdAt;

    private JoinRequestStatus status;
    private String decisionReason;
    private UUID decidedByUserId;
    private Instant decidedAt;

    // Factory methods
    public static LiveRoomJoinRequest create(...) { }
    public static LiveRoomJoinRequest rehydrate(...) { }

    // Business methods
    public void approve(UUID decidedBy, String reason) { }
    public void reject(UUID decidedBy, String reason) { }
    public void cancel(UUID ownerId) { }
    public boolean isOwnedBy(UUID candidate) { }
}
```

---

## 3. Enums

### 3.1 LiveRoomStatus

```java
public enum LiveRoomStatus {
    ACTIVE,   // Có thể join/participate
    PAUSED,   // Tạm dừng
    ENDED;    // Đã kết thúc

    public boolean isTerminal() { return this == ENDED; }
    public boolean isJoinable() { return this == ACTIVE; }
}
```

### 3.2 LiveRoomMode

```java
public enum LiveRoomMode {
    PUBLIC,   // Join trực tiếp
    PRIVATE;  // Cần approval

    public boolean requiresApproval() { return this == PRIVATE; }
    public boolean isPubliclyDiscoverable() { return this == PUBLIC; }
}
```

### 3.3 JoinRequestStatus

```java
public enum JoinRequestStatus {
    PENDING,    // Đang chờ duyệt
    APPROVED,   // Được duyệt → trở thành participant
    REJECTED,   // Bị từ chối
    CANCELLED;  // User hủy request
}
```

---

## 4. Validation Rules

### 4.1 Room Code

| Rule | Value |
|------|-------|
| Length | 6 ký tự |
| Charset | A-Z, 2-9 |
| Case | Chỉ uppercase |
| Example | `ABC123`, `XYZ789` |

```java
private static void validateRoomCode(String roomCode) {
    if (roomCode == null || roomCode.length() != 6) {
        throw new IllegalArgumentException("Room code must be exactly 6 characters");
    }
    for (int i = 0; i < roomCode.length(); i++) {
        char c = roomCode.charAt(i);
        boolean isUpperAlpha = c >= 'A' && c <= 'Z';
        boolean isDigit = c >= '2' && c <= '9';
        if (!isUpperAlpha && !isDigit) {
            throw new IllegalArgumentException(
                "Room code must contain only uppercase letters and digits (2-9)");
        }
    }
}
```

### 4.2 Title

| Rule | Value |
|------|-------|
| Required | Có |
| Min length | 1 |
| Max length | 200 chars |

### 4.3 Description

| Rule | Value |
|------|-------|
| Required | Không |
| Max length | 1000 chars |

### 4.4 Capacity

| Rule | Value |
|------|-------|
| Min | 2 |
| Max | 5 |
| Default | 5 |

### 4.5 Display Name

| Rule | Value |
|------|-------|
| Required | Có |
| Min length | 1 |
| Max length | 100 chars |

### 4.6 Message (Join Request)

| Rule | Value |
|------|-------|
| Required | Không |
| Max length | 500 chars |

### 4.7 Reason (Decision)

| Rule | Value |
|------|-------|
| Required | Không |
| Max length | 500 chars |

---

## 5. JPA Entities

### 5.1 LiveRoomJpaEntity

```java
@Entity
@Table(name = "live_rooms")
public class LiveRoomJpaEntity extends LiveRoomBaseEntity {

    @Column(name = "host_user_id", nullable = false)
    private UUID hostUserId;

    @Column(name = "room_code", nullable = false, unique = true, length = 6)
    private String roomCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private LiveRoomMode mode;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LiveRoomStatus status;

    @Column(name = "current_participant_count", nullable = false)
    private int currentParticipantCount;

    // ... timestamps
}
```

### 5.2 LiveRoomParticipantJpaEntity

```java
@Entity
@Table(name = "live_room_participants")
public class LiveRoomParticipantJpaEntity {

    @Column(name = "room_code", nullable = false, length = 6)
    private String roomCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "role_at_join", nullable = false)
    private String roleAtJoin;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "mic_muted", nullable = false)
    private boolean micMuted;

    @Column(name = "camera_off", nullable = false)
    private boolean cameraOff;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
```

### 5.3 LiveRoomJoinRequestJpaEntity

```java
@Entity
@Table(name = "live_room_join_requests")
public class LiveRoomJoinRequestJpaEntity {

    @Column(name = "room_code", nullable = false, length = 6)
    private String roomCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "message", length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JoinRequestStatus status;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

---

## 6. Database Constraints

### Live Rooms Table

```sql
ALTER TABLE live_rooms
  ADD CONSTRAINT chk_mode CHECK (mode IN ('PUBLIC', 'PRIVATE')),
  ADD CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
  ADD CONSTRAINT chk_capacity CHECK (max_participants BETWEEN 2 AND 5);
```

---

## 7. Indexes

```sql
-- Live Rooms
CREATE INDEX idx_live_rooms_host_user_id ON live_rooms(host_user_id);
CREATE UNIQUE INDEX idx_live_rooms_room_code ON live_rooms(room_code);

-- Participants
CREATE INDEX idx_participants_room_code ON live_room_participants(room_code);
CREATE INDEX idx_participants_room_user ON live_room_participants(room_code, user_id);
CREATE INDEX idx_participants_left_at ON live_room_participants(left_at);

-- Join Requests
CREATE INDEX idx_join_requests_room_code ON live_room_join_requests(room_code);
CREATE INDEX idx_join_requests_user_status ON live_room_join_requests(user_id, status);
```

---

## 8. Related Documentation

- [FR-001: Create Room](./FR-001-create-room.md)
- [FR-006: Join Private Room](./FR-006-join-private-room.md)
