# Live Room — Data Model Specification

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8
**Đối tượng đọc**: Backend Dev, DBA, QA
**Mục đích**: Quy chuẩn schema JPA entity, index, unique constraint cho module Live Room. Code backend **BẮT BUỘC** tuân theo spec này.

---

## 1. Quy ước chung

### 1.1. Naming convention

| Thành phần | Convention | Ví dụ |
|---|---|---|
| Bảng | `liveroom_<entity>` (snake_case, plural) | `liveroom_rooms`, `liveroom_participants` |
| Cột | `snake_case` | `room_code`, `current_count` |
| Primary key | `id` (UUID v4) | `id UUID` |
| Foreign key | `<entity_singular>_id` | `owner_id`, `room_id` |
| Timestamp | `_at` suffix | `created_at`, `joined_at` |
| Flag | `is_<state>` hoặc `_<state>` suffix | `is_active`, `mic_on` |
| Enum | `<table>_<state>` hoặc `<table>_<reason>` | `room_status`, `rejection_reason` |
| Index | `idx_<table>_<columns>` | `idx_liveroom_rooms_owner_id` |
| Unique constraint | `uq_<table>_<columns>` | `uq_liveroom_rooms_owner_name` |

### 1.2. Timestamp & UUID

- **UUID v4** cho mọi primary key (`@GeneratedValue(strategy = GenerationType.UUID)`)
- **UTC timestamp** cho mọi cột `*_at` (database `TIMESTAMP WITH TIME ZONE`)
- **Soft delete** KHÔNG dùng (entity bị xoá cứng khi cleanup job)
- **Audit columns** mandatory: `created_at`, `updated_at` (auto-managed by JPA `@PrePersist`/`@PreUpdate`)

### 1.3. Optimistic lock

- Field `@Version` (JPA) chỉ áp dụng cho entity **không thể dùng pessimistic lock** (vì update pattern không đồng nhất hoặc cần tránh lock contention):
  - **`PlaybackState`** (R-MUSIC-10 v1.8 race condition cho music control — play/pause/seek/volume từ nhiều user cùng lúc)
- Field `Integer version` (KHÔNG dùng `Long` để tiết kiệm storage, sequence không vượt `Integer.MAX_VALUE`)
- **LiveRoom KHÔNG dùng `@Version`** — tất cả update đều qua `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) (xem `liveroom-concurrency.md` §2.1). Lý do: LiveRoom update luôn đi kèm capacity-sensitive operations (approve/rejoin/leave/kick/end/reopen) cần serialize qua transaction, pessimistic lock hiệu quả hơn optimistic retry.

---

## 2. Entity Overview

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│ liveroom_rooms              │ 1───N   │ liveroom_room_session_cycles│
│ (LiveRoom)                  │─────────│ (RoomSessionCycle)          │
└─────────────────────────────┘         └─────────────────────────────┘
       │ 1                                         │ 1
       │                                           │
       │ N                                         │ N
       ▼                                           ▼
┌─────────────────────────────┐         ┌─────────────────────────────┐
│ liveroom_participants       │         │ liveroom_join_requests      │
│ (Participant)               │         │ (JoinRequest)               │
└─────────────────────────────┘         └─────────────────────────────┘
       │ 1                                         │ N
       │                                           │
       │ N                                         │ 1
       ▼                                           ▼
┌─────────────────────────────┐         ┌─────────────────────────────┐
│ liveroom_reject_counters    │         │ liveroom_chat_messages      │
│ (RejectCounter)             │         │ (ChatMessage)               │
└─────────────────────────────┘         └─────────────────────────────┘
                                               │
                                               │ 1
                                               ▼
                                      ┌─────────────────────────────┐
                                      │ liveroom_playback_states    │
                                      │ (PlaybackState)             │
                                      └─────────────────────────────┘
                                               │
                                               │ 1
                                               ▼
                                      ┌─────────────────────────────┐
                                      │ liveroom_annotations        │
                                      │ (Annotation)                │
                                      └─────────────────────────────┘

(Audit tables phụ — không bắt buộc FK constraint)
┌─────────────────────────────┐  ┌─────────────────────────────┐  ┌─────────────────────────────┐
│ liveroom_admin_actions      │  │ liveroom_ownership_history  │  │ liveroom_privacy_actions    │
│ (RoomAdminAction)           │  │ (RoomOwnershipHistory)      │  │ (UserPrivacyAction)         │
└─────────────────────────────┘  └─────────────────────────────┘  └─────────────────────────────┘
```

---

## 3. Entity chi tiết

### 3.1. `liveroom_rooms` (LiveRoom)

**Java class**: `com.pwb.liveroom.core.model.LiveRoom`
**Annotation**: `@Entity @Table(name = "liveroom_rooms")`

| Column | Type | Null | Default | Mô tả | Liên kết BR |
|---|---|---|---|---|---|
| `id` | UUID | NO | — | Primary key | — |
| `room_code` | VARCHAR(6) | NO | — | Mã phòng chia sẻ (6 chars A-Z0-9) | R-CREATE-05 |
| `room_name` | VARCHAR(100) | NO | — | Tên phòng (giữ raw case) | R-CREATE-06 |
| `room_name_normalized` | VARCHAR(100) | NO | — | `LOWER(TRIM(room_name))` để unique | R-CREATE-06 v1.8 |
| `owner_id` | UUID | NO | — | FK → user | R-ROLE-01 |
| `max_participants` | SMALLINT | NO | 7 | Capacity tối đa (1-7) | R-CREATE-03/04 |
| `current_participant_count` | SMALLINT | NO | 0 | Đếm ACTIVE realtime | R-CAPACITY-01 |
| `status` | VARCHAR(20) | NO | 'ACTIVE' | Enum: ACTIVE/ENDED | R-END-03 |
| `ended_reason` | VARCHAR(30) | YES | NULL | Enum: manual/owner_grace_expired/empty_timeout/force_role_change | R-END-08/09/06 |
| `ended_at` | TIMESTAMPTZ | YES | NULL | Lúc ENDED | R-END-03/10 |
| `created_at` | TIMESTAMPTZ | NO | now() | Lúc tạo | audit |
| `updated_at` | TIMESTAMPTZ | NO | now() | Auto-update | audit |
| `started_at` | TIMESTAMPTZ | NO | now() | Reset khi reopen (R-REOPEN-04.1) | R-END-09 |
| `reopened_count` | SMALLINT | NO | 0 | Số lần reopen | R-REOPEN-05 |
| `last_reopened_at` | TIMESTAMPTZ | YES | NULL | Lần reopen gần nhất | R-REOPEN-06 |
| `owner_left_at` | TIMESTAMPTZ | YES | NULL | Lúc owner leave (start grace) | R-LEAVE-04 |
| `owner_grace_seconds` | SMALLINT | NO | 60 | Grace period (30-1800) | R-LEAVE-07, R-GRACE-01 |
| `reserved_owner_slot` | BOOLEAN | NO | FALSE | True khi owner absent | R-LEAVE-09 |
| `current_session_cycle_id` | UUID | YES | NULL | FK → liveroom_room_session_cycles | R-REOPEN-04.1 |

**Lưu ý**: `LiveRoom` KHÔNG có cột `version` và KHÔNG dùng `@Version`. Locking dùng `PESSIMISTIC_WRITE` (xem `liveroom-concurrency.md` §2.1).

**Unique constraints**:
- `uq_liveroom_rooms_room_code` (`room_code`) — 6 chars duy nhất
- `uq_liveroom_rooms_owner_name` (`owner_id`, `room_name_normalized`) — per-owner unique

**Indexes**:
- `idx_liveroom_rooms_owner_id` (`owner_id`)
- `idx_liveroom_rooms_status` (`status`) — WHERE status = 'ACTIVE'
- `idx_liveroom_rooms_owner_left_at` (`owner_left_at`) WHERE NOT NULL — cho grace expiry job
- `idx_liveroom_rooms_started_at` (`started_at`) — cho empty timeout job
- `idx_liveroom_rooms_current_session_cycle` (`current_session_cycle_id`)

**State transition** (xem `liveroom-state-machines.md` chi tiết):
```
ACTIVE ─[R-END-01 manual]→ ENDED (reason=manual)
ACTIVE ─[R-END-08 grace expired]→ ENDED (reason=owner_grace_expired)
ACTIVE ─[R-END-09 empty 5min]→ ENDED (reason=empty_timeout)
ACTIVE ─[R-ROLE-06 force end]→ ENDED (reason=force_role_change)
ENDED ─[R-REOPEN-01]→ ACTIVE (reopened_count++, new cycle)
```

---

### 3.2. `liveroom_room_session_cycles` (RoomSessionCycle)

**Java class**: `com.pwb.liveroom.core.model.RoomSessionCycle`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `room_id` | UUID | NO | FK → liveroom_rooms | R-REOPEN-04.1 |
| `cycle_number` | INTEGER | NO | 1, 2, 3,... (theo reopened_count + 1) | R-REOPEN-05 |
| `started_at` | TIMESTAMPTZ | NO | now() | R-REOPEN-04.1 |
| `ended_at` | TIMESTAMPTZ | YES | NULL | R-END-03 |
| `ended_reason` | VARCHAR(30) | YES | Mirror từ room.ended_reason | R-END-08/09/06 |
| `created_at` | TIMESTAMPTZ | NO | now() | audit |

**Unique constraints**:
- `uq_liveroom_cycles_room_cycle` (`room_id`, `cycle_number`)

**Indexes**:
- `idx_liveroom_cycles_room_id` (`room_id`)
- `idx_liveroom_cycles_started_at` (`started_at DESC`) — cho history view

**Lý do tách entity**: Annotation cross-session (R-ANNOT-08) cần phân biệt theo cycle. Khi reopen, cycle cũ vẫn còn DB để query history.

---

### 3.3. `liveroom_participants` (Participant)

**Java class**: `com.pwb.liveroom.core.model.Participant`

| Column | Type | Null | Default | Mô tả | Liên kết BR |
|---|---|---|---|---|---|
| `id` | UUID | NO | — | Primary key | — |
| `room_id` | UUID | NO | — | FK → liveroom_rooms | R-JOIN-01 |
| `user_id` | UUID | NO | — | FK → user | R-JOIN-02 |
| `room_session_cycle_id` | UUID | NO | — | FK → liveroom_room_session_cycles | R-RANK-01 |
| `state` | VARCHAR(20) | NO | 'JOINING' | ACTIVE/LEFT/KICKED/RECONNECTING/ENDED | R-JOIN-03 |
| `mic_state` | VARCHAR(20) | NO | 'UNMUTED' | UNMUTED/SELF_MUTED/MUTED_BY_OWNER | R-MEDIA-09 |
| `camera_on` | BOOLEAN | NO | FALSE | Camera bật/tắt | R-MEDIA-01 |
| `mic_on` | BOOLEAN | NO | FALSE | Mic bật/tắt | R-MEDIA-01 |
| `was_approved` | BOOLEAN | NO | FALSE | TRUE = đã từng ACTIVE (any cycle) | R-REOPEN-08 |
| `joined_at` | TIMESTAMPTZ | YES | NULL | Lúc join ACTIVE (cho R-RANK-01) | R-JOIN-04 |
| `left_at` | TIMESTAMPTZ | YES | NULL | Lúc leave/kick/ended | R-LEAVE-02 |
| `last_interaction_at` | TIMESTAMPTZ | YES | NULL | Auto-update khi tương tác | R-MEDIA-12 |
| `mic_muted_by_owner_at` | TIMESTAMPTZ | YES | NULL | Lúc owner mute (start cooldown) | R-MEDIA-10 |
| `mic_mute_cooldown_until` | TIMESTAMPTZ | YES | NULL | `muted_at + 30s` | R-MEDIA-10 |
| `created_at` | TIMESTAMPTZ | NO | now() | — | audit |
| `updated_at` | TIMESTAMPTZ | NO | now() | — | audit |

**Unique constraints**:
- `uq_liveroom_participants_active` (`room_id`, `user_id`) WHERE `state IN ('ACTIVE', 'RECONNECTING')` — partial unique index, tránh duplicate ACTIVE (R-JOIN-10 multi-tab)
- `uq_liveroom_participants_cycle` (`room_id`, `user_id`, `room_session_cycle_id`) — 1 row per user per cycle

**Indexes**:
- `idx_liveroom_participants_room_state` (`room_id`, `state`) — query participants trong phòng
- `idx_liveroom_participants_user` (`user_id`) — query participation history
- `idx_liveroom_participants_idle` (`room_id`, `last_interaction_at`) — cho idle ghost job
- `idx_liveroom_participants_cycle_joined` (`room_session_cycle_id`, `joined_at`) — cho R-RANK-01 oldest

**State transition**:
```
JOINING → ACTIVE (sau khi WebRTC connect thành công)
ACTIVE → RECONNECTING (WS disconnect)
RECONNECTING → ACTIVE (WS reconnect)
ACTIVE → LEFT (user tự leave)
ACTIVE → KICKED (owner kick, R-ADMIN-04)
ACTIVE → ENDED (room ended)
RECONNECTING → ENDED (grace 60s reconnect timeout)
```

---

### 3.4. `liveroom_join_requests` (JoinRequest)

**Java class**: `com.pwb.liveroom.core.model.JoinRequest`

| Column | Type | Null | Default | Mô tả | Liên kết BR |
|---|---|---|---|---|---|
| `id` | UUID | NO | — | Primary key | — |
| `room_id` | UUID | NO | — | FK → liveroom_rooms | R-JOIN-01 |
| `user_id` | UUID | NO | — | FK → user | R-JOIN-02 |
| `room_session_cycle_id` | UUID | NO | — | FK → cycle | R-REOPEN-07 |
| `state` | VARCHAR(30) | NO | 'PENDING' | PENDING/APPROVED/REJECTED_BY_OWNER/REJECTED_BY_CAPACITY/CANCELLED/EXPIRED/LOCKED | R-JOIN-11 v1.8 |
| `rejection_reason` | VARCHAR(20) | YES | NULL | OWNER_REJECT/CAPACITY_FULL/ROOM_ENDED/USER_CANCELLED/RATE_LIMIT/NONE | R-JOIN-09 v1.8 |
| `idempotency_key` | UUID | YES | NULL | Client-generated, TTL 24h | R-JOIN-07 v1.8 |
| `created_at` | TIMESTAMPTZ | NO | now() | Lúc tạo | audit |
| `resolved_at` | TIMESTAMPTZ | YES | NULL | Lúc state → terminal | audit |
| `resolved_by` | UUID | YES | NULL | User đã resolve (owner/auto) | R-REJECT-04 |

**Unique constraints**:
- `uq_liveroom_join_requests_idempotency` (`idempotency_key`) WHERE NOT NULL — 24h TTL
- `uq_liveroom_join_requests_active` (`user_id`, `room_id`, `room_session_cycle_id`) WHERE `state IN ('PENDING', 'APPROVED')` — partial unique, tránh duplicate active request (R-JOIN-07)

**Indexes**:
- `idx_liveroom_join_requests_room_state` (`room_id`, `state`) — query pending requests
- `idx_liveroom_join_requests_user` (`user_id`)
- `idx_liveroom_join_requests_created_at` (`created_at`) — cho cleanup

**State transition** (xem state-machines.md):
```
PENDING ─[owner approve + còn slot]→ APPROVED
PENDING ─[owner reject]→ REJECTED_BY_OWNER
PENDING ─[capacity full]→ REJECTED_BY_CAPACITY
PENDING ─[user cancel]→ CANCELLED
PENDING ─[room ENDED]→ EXPIRED
PENDING ─[rejectCountByOwner ≥ 3]→ LOCKED
APPROVED → terminal (đã vào phòng, participant row ACTIVE)
Tất cả terminal states → không thể transition
```

---

### 3.5. `liveroom_reject_counters` (RejectCounter)

**Java class**: `com.pwb.liveroom.core.model.RejectCounter`

| Column | Type | Null | Default | Mô tả | Liên kết BR |
|---|---|---|---|---|---|
| `id` | UUID | NO | — | Primary key | — |
| `user_id` | UUID | NO | — | FK → user | R-REJECT-04 |
| `room_id` | UUID | NO | — | FK → liveroom_rooms | R-REJECT-04 |
| `room_session_cycle_id` | UUID | NO | — | FK → cycle | R-REJECT-04.1 |
| `reject_count_by_owner` | SMALLINT | NO | 0 | Counter cho owner reject | R-REJECT-04 v1.8 |
| `reject_count_by_capacity` | SMALLINT | NO | 0 | Counter cho capacity reject | R-REJECT-04 v1.8 |
| `created_at` | TIMESTAMPTZ | NO | now() | — | audit |
| `updated_at` | TIMESTAMPTZ | NO | now() | — | audit |

**Unique constraints**:
- `uq_liveroom_reject_counters_triplet` (`user_id`, `room_id`, `room_session_cycle_id`)

**Indexes**:
- `idx_liveroom_reject_counters_user_room` (`user_id`, `room_id`)

**Trigger/check** (application-level, xem concurrency.md):
- Khi insert JoinRequest với state `REJECTED_BY_OWNER` → INSERT ON CONFLICT (`user_id`, `room_id`, `room_session_cycle_id`) DO UPDATE SET `reject_count_by_owner = reject_count_by_owner + 1`
- Khi insert JoinRequest với state `REJECTED_BY_CAPACITY` → tương tự với `reject_count_by_capacity`
- Khi reopen → xoá hết row theo `room_session_cycle_id` (R-REJECT-04.1)

---

### 3.6. `liveroom_chat_messages` (ChatMessage)

**Java class**: `com.pwb.liveroom.core.model.ChatMessage`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `room_id` | UUID | NO | FK → liveroom_rooms | R-CHAT-03 |
| `user_id` | UUID | NO | FK → user | R-CHAT-03 |
| `room_session_cycle_id` | UUID | NO | FK → cycle | R-CHAT-06 v1.8 |
| `content` | VARCHAR(500) | NO | Content (trim, non-empty) | R-CHAT-04 |
| `sent_at` | TIMESTAMPTZ | NO | now() | R-CHAT-03 |
| `created_at` | TIMESTAMPTZ | NO | now() | audit |

**Indexes**:
- `idx_liveroom_chat_messages_room_sent` (`room_id`, `sent_at DESC`) — cho load history (R-CHAT-05)
- `idx_liveroom_chat_messages_cycle` (`room_session_cycle_id`) — query theo cycle

**Cleanup job** (R-CHAT-06 v1.8):
- Chat **KHÔNG xoá** khi phòng ENDED
- Cleanup job xoá chat > 90 ngày (configurable POST-MVP)
- Lưu DB vĩnh viễn cho audit trail

---

### 3.7. `liveroom_playback_states` (PlaybackState)

**Java class**: `com.pwb.liveroom.core.model.PlaybackState`

| Column | Type | Null | Default | Mô tả | Liên kết BR |
|---|---|---|---|---|---|
| `id` | UUID | NO | — | Primary key | — |
| `room_id` | UUID | NO | — | FK → liveroom_rooms (1:1) | R-MUSIC-04 |
| `song_id` | UUID | YES | NULL | FK → Song (Voice module) | R-MUSIC-04 |
| `song_owner_id` | UUID | YES | NULL | User đã chọn bài | R-MUSIC-04 |
| `song_title` | VARCHAR(200) | YES | NULL | Denormalized | R-MUSIC-04 |
| `song_artist` | VARCHAR(200) | YES | NULL | Denormalized | R-MUSIC-04 |
| `song_duration_seconds` | INTEGER | YES | NULL | — | R-MUSIC-04 |
| `status` | VARCHAR(20) | NO | 'PAUSED' | PLAYING/PAUSED | R-MUSIC-04 |
| `current_position_seconds` | DOUBLE | NO | 0.0 | Realtime position | R-MUSIC-04 |
| `volume_percent` | SMALLINT | NO | 80 | 0-100 (global) | R-MUSIC-04 |
| `started_at` | TIMESTAMPTZ | YES | NULL | Lúc PLAYING bắt đầu | R-MUSIC-04 |
| `last_updated_at` | TIMESTAMPTZ | NO | now() | Server timestamp | R-MUSIC-10 v1.8 |
| `sequence_number` | BIGINT | NO | 0 | Monotonic increment | R-MUSIC-10 v1.8 |
| `last_updated_by` | UUID | YES | NULL | User đã control cuối | R-MUSIC-04 |
| `version` | INTEGER | NO | 0 | `@Version` optimistic lock | R-MUSIC-10 v1.8 |
| `created_at` | TIMESTAMPTZ | NO | now() | audit |
| `updated_at` | TIMESTAMPTZ | NO | now() | audit |

**Unique constraints**:
- `uq_liveroom_playback_states_room` (`room_id`) — 1 playback state per room

**Indexes**:
- `idx_liveroom_playback_states_sequence` (`sequence_number`) — cho ordering

**Race condition protection** (R-MUSIC-10):
- JPA `@Version` → OptimisticLockException → HTTP 409 `LIVEROOM_MUSIC_STATE_CONFLICT`
- SQL update: `UPDATE ... SET version = version + 1 WHERE room_id = ? AND version = ?`
- Client nhận 409 → re-fetch state → apply

---

### 3.8. `liveroom_annotations` (Annotation)

**Java class**: `com.pwb.liveroom.core.model.Annotation`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `room_id` | UUID | NO | FK → liveroom_rooms | R-ANNOT-04 |
| `room_session_cycle_id` | UUID | NO | FK → cycle (cross-session) | R-ANNOT-04 v1.8 |
| `song_id` | UUID | NO | FK → Song | R-ANNOT-04 |
| `user_id` | UUID | NO | FK → user | R-ANNOT-04 |
| `user_email` | VARCHAR(255) | NO | Denormalized (display name) | R-ANNOT-04 |
| `position_seconds` | DOUBLE | NO | 0.0 → durationSeconds | R-ANNOT-04 |
| `content` | VARCHAR(200) | NO | Max 200 chars | R-ANNOT-04 |
| `created_at` | TIMESTAMPTZ | NO | now() | R-ANNOT-04 |

**Indexes**:
- `idx_liveroom_annotations_cycle_position` (`room_session_cycle_id`, `position_seconds`) — cho SC-07 history view
- `idx_liveroom_annotations_room_created` (`room_id`, `created_at DESC`)

**Lifecycle**:
- KHÔNG xoá khi reopen (R-ANNOT-05 v1.8) — archive theo cycle
- Lưu vĩnh viễn cho audit

---

### 3.9. `liveroom_admin_actions` (RoomAdminAction — Audit)

**Java class**: `com.pwb.liveroom.core.model.RoomAdminAction`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `room_id` | UUID | NO | FK → liveroom_rooms | R-ADMIN-06 v1.8 |
| `actor_user_id` | UUID | NO | Owner đã thực hiện action | R-ADMIN-06 |
| `target_user_id` | UUID | NO | User bị ảnh hưởng | R-ADMIN-06 |
| `action_type` | VARCHAR(20) | NO | KICK/REMOTE_MUTE | R-ADMIN-06 |
| `reason` | VARCHAR(200) | YES | Optional note | R-ADMIN-06 |
| `created_at` | TIMESTAMPTZ | NO | now() | audit |

**Indexes**:
- `idx_liveroom_admin_actions_room_created` (`room_id`, `created_at DESC`)
- `idx_liveroom_admin_actions_actor` (`actor_user_id`)

**Retention**: 90 ngày (POST-MVP configurable)

---

### 3.10. `liveroom_ownership_history` (RoomOwnershipHistory — Audit)

**Java class**: `com.pwb.liveroom.core.model.RoomOwnershipHistory`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `room_id` | UUID | NO | FK → liveroom_rooms | R-ROLE-13 v1.8 |
| `owner_user_id` | UUID | NO | Owner tại thời điểm change | R-ROLE-13 |
| `change_type` | VARCHAR(20) | NO | INITIAL_CREATE/ROLE_DOWNGRADE/ROLE_UPGRADE/FORCE_END | R-ROLE-13 |
| `changed_at` | TIMESTAMPTZ | NO | now() | R-ROLE-13 |
| `reason` | VARCHAR(200) | YES | Optional | R-ROLE-13 |

**Indexes**:
- `idx_liveroom_ownership_history_room` (`room_id`, `changed_at DESC`)

---

### 3.11. `liveroom_privacy_actions` (UserPrivacyAction — Audit)

**Java class**: `com.pwb.liveroom.core.model.UserPrivacyAction`

| Column | Type | Null | Mô tả | Liên kết BR |
|---|---|---|---|---|
| `id` | UUID | NO | Primary key | — |
| `user_id` | UUID | NO | User đã toggle | R-DISPLAY-06 v1.8 |
| `room_id` | UUID | NO | Trong phòng nào | R-DISPLAY-06 |
| `action_type` | VARCHAR(20) | NO | HIDE_EMAIL/SHOW_EMAIL | R-DISPLAY-06 |
| `created_at` | TIMESTAMPTZ | NO | now() | audit |

**Indexes**:
- `idx_liveroom_privacy_actions_user` (`user_id`, `created_at DESC`)

---

## 4. Foreign Key & Cascade Rules

| FK | ON DELETE | ON UPDATE | Lý do |
|---|---|---|---|
| `liveroom_rooms.owner_id` → `users.id` | RESTRICT | CASCADE | Không cho xoá user khi còn phòng |
| `liveroom_participants.room_id` → `liveroom_rooms.id` | RESTRICT | CASCADE | Phòng không xoá cứng (giữ audit) |
| `liveroom_participants.user_id` → `users.id` | RESTRICT | CASCADE | — |
| `liveroom_join_requests.room_id` → `liveroom_rooms.id` | RESTRICT | CASCADE | — |
| `liveroom_chat_messages.room_id` → `liveroom_rooms.id` | RESTRICT | CASCADE | — |
| `liveroom_playback_states.room_id` → `liveroom_rooms.id` | CASCADE | CASCADE | Xoá phòng → xoá playback |
| `liveroom_annotations.room_id` → `liveroom_rooms.id` | RESTRICT | CASCADE | — |
| `liveroom_annotations.song_id` → `songs.id` (Voice module) | RESTRICT | CASCADE | — |
| `liveroom_playback_states.song_id` → `songs.id` | SET NULL | CASCADE | Song bị xoá → playback state giữ nullable |
| `liveroom_room_session_cycles.room_id` → `liveroom_rooms.id` | RESTRICT | CASCADE | — |
| Audit tables | RESTRICT | CASCADE | — |

**Lưu ý**: Backend KHÔNG xoá cứng `liveroom_rooms` (giữ audit trail). R-END chỉ set status = ENDED.

---

## 5. Migration Plan

### 5.1. Initial migration (V1.0)

Tạo tất cả 11 bảng trên với Flyway/Liquibase:
- `V1__liveroom_initial_schema.sql`
- Bao gồm: tables, indexes, unique constraints, FK constraints

### 5.2. Seed data (CHỈ cho dev/test)

- Không seed cho production
- Test data: 2-3 users, 1 phòng, 1-2 participants

### 5.3. Backward compatibility

- Lần release đầu: tạo mới tất cả tables
- Migration sau: ADD COLUMN (không DROP) để giữ backward compat

---

## 6. JPA Entity Checklist

Mỗi entity class BẮT BUỘC có:

```java
@Entity
@Table(name = "liveroom_xxx")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Xxx {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // fields theo spec
}
```

**Lưu ý**:
- `@Data` KHÔNG dùng cho entity (gây vấn đề với `@EqualsAndHashCode` và `@ToString` cho FK)
- Dùng `@Getter @Setter @Builder` riêng
- `@Version` **CHỈ** thêm vào `PlaybackState` (R-MUSIC-10 v1.8). `LiveRoom` dùng `PESSIMISTIC_WRITE` không cần `@Version`
- `@PrePersist` cho logic default (vd: `currentParticipantCount = 0`)

---

## 7. Repository naming convention

- Interface extend `JpaRepository<Entity, UUID>`
- Tên: `XxxRepository` (e.g. `LiveRoomRepository`, `ParticipantRepository`)
- Query method naming:
  - `findByRoomIdAndState(UUID roomId, String state)` → Spring auto-generate
  - Custom query → `@Query` annotation với JPQL

---

## 8. Validation Rules (Bean Validation)

Sử dụng Jakarta Bean Validation (`@NotNull`, `@Size`, `@Pattern`, ...):

| Field | Validation | Message key |
|---|---|---|
| `room_code` | `@Pattern(regexp = "^[A-Z0-9]{6}$")` | validation.room.code.format |
| `room_name` | `@Size(min = 1, max = 100)` `@Pattern` | validation.room.name.length |
| `max_participants` | `@Min(1) @Max(7)` | validation.room.capacity.range |
| `owner_grace_seconds` | `@Min(30) @Max(1800)` | LIVEROOM_GRACE_INVALID |
| `chat.content` | `@Size(min = 1, max = 500) @NotBlank` | LIVEROOM_CHAT_EMPTY/TOO_LONG |
| `annotation.content` | `@Size(min = 1, max = 200) @NotBlank` | LIVEROOM_ANNOTATION_EMPTY/TOO_LONG |
| `annotation.position_seconds` | `@DecimalMin("0.0")` (custom check vs song.duration) | LIVEROOM_ANNOTATION_INVALID_POSITION |
| `playback.volume_percent` | `@Min(0) @Max(100)` | LIVEROOM_MUSIC_INVALID_VOLUME |

---

## 9. Data Integrity Rules (Application-level)

| Rule | Enforcement | Nơi check |
|---|---|---|
| Active participant unique per user per room (R-JOIN-10) | Partial unique index | Database |
| Reject counter monotonic increment | UPSERT pattern | Service layer |
| Room code 6 chars unique | Unique constraint + retry logic | Service layer |
| Active room status check | ENUM + validation | Service layer |
| Cycle tăng dần theo reopened_count | Compute trong service | Service layer |
| Music optimistic lock | `@Version` | JPA auto |
| Annotation position ≤ song duration | Custom validation | Service layer |

---

## 10. Out of Scope (POST-MVP)

- ❌ Partition table theo cycle (chỉ cần khi scale > 10K cycles)
- ❌ Read replica cho chat history
- ❌ Archive table cho audit (move > 1 year sang cold storage)
- ❌ Full-text search trên chat content
- ❌ Time-series index cho analytics

---

## 11. Tham chiếu chéo

- **Business rules**: `liveroom-business-requirements.md` v1.8
- **State machine**: `liveroom-state-machines.md` (sẽ tạo)
- **API**: `liveroom-api-spec.md` (sẽ tạo)
- **WebSocket**: `liveroom-ws-protocol.md` (sẽ tạo)
- **Concurrency**: `liveroom-concurrency.md` (sẽ tạo)
- **Jobs**: `liveroom-jobs.md` (sẽ tạo)

---

**Người viết**: Senior Dev Team
**Reviewer**: PO, DBA, Backend Lead
**Ngày review**: Pending
