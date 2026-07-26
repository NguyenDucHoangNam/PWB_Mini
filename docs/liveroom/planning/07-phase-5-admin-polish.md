# Phase 5: Admin + Polish

**Thời gian**: 5-7 ngày
**Mục tiêu**: Kick/remote mute, audit log, GDPR, monitoring
**Doc tham chiếu chính**: `liveroom-api-spec.md` §2.7-2.9, `liveroom-audit-logging.md`, `liveroom-jobs.md` §9

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

## PHASE 5 LÀ GÌ:
Admin + Polish - Kick user (5min cooldown) + Remote mute mic (30s cooldown) + 3 audit tables + audit logging service + email masking (GDPR) + IdempotencyCleanupJob + OpenTelemetry tracing + Prometheus metrics + 3 FE components + i18n ~15 keys + final E2E test.

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md
2. docs/liveroom/planning/00-reading-guide.md
3. docs/liveroom/planning/07-phase-5-admin-polish.md (FILE NÀY - chi tiết 14 tasks)
4. docs/liveroom/planning/10-codebase-templates.md
5. docs/liveroom/planning/08-self-review-checklist.md
6. docs/liveroom/api-spec.md §2.7 (kick), §2.8 (mute-mic), §2.9 (privacy)
7. docs/liveroom/audit-logging.md §2-6 (3 tables + service + tracing)
8. docs/liveroom/data-model.md §4-6 (RoomAdminAction, RoomOwnershipHistory, UserPrivacyAction)
9. docs/liveroom/jobs.md §9 (IdempotencyCleanup)
10. docs/liveroom/screen-inventory.md SC-06 (admin context menu), SC-13 (kicked landing)
11. docs/liveroom/business-requirements.md R-ADMIN-01..05, R-KICK-01..04, R-DISPLAY-01..06, R-AUDIT-01..05
12. docs/liveroom/i18n-keys.md (15 keys mới)

## 14 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 5.1 - POST /participants/{userId}/kick
DTO KickRequest (reason max 200 chars). Validate owner + target state=ACTIVE + target != owner. Set state=KICKED + kickedAt=now + kickedReason + leftAt=now. count-1. Audit log. WS broadcast PARTICIPANT_KICKED + sendToUser YOU_WERE_KICKED.

### Task 5.2 - POST /participants/{userId}/mute-mic
Validate owner + target ACTIVE. Check 30s cooldown (mutedByOwnerAt + 30s). Set micEnabled=false + mutedByOwnerAt=now + mutedByOwnerId=owner. Audit log. WS broadcast PARTICIPANT_MIC_MUTED_BY_OWNER.

### Task 5.3 - 3 Audit tables (Flyway migration V3)
liveroom_admin_actions (room_id, actor_id, target_user_id, action_type, reason, created_at), liveroom_ownership_history (room_id, previous_owner_id, new_owner_id, reason, transferred_at), liveroom_privacy_actions (user_id, room_id, action_type, target_user_id, created_at) + indexes.

### Task 5.4 - Audit logging service
AuditService với 3 methods: logAdminAction, logOwnershipTransfer, logPrivacyAction. Tất cả @Transactional + log info có context.

### Task 5.5 - Email masking helper
Class EmailMasker.mask(email): an@congty.com → an***@congty.com, john.doe@example.com → jo***@example.com.

### Task 5.6 - PATCH /privacy/email
DTO UpdateEmailPrivacyRequest (hideEmail boolean). Update participant.hideEmail. Audit log. WS broadcast PARTICIPANT_PRIVACY_CHANGED.

### Task 5.7 - IdempotencyCleanupJob
@Scheduled(cron="0 0 2 * * *") + @SchedulerLock. Delete idempotency keys > 24h.

### Task 5.8 - OpenTelemetry tracing
Config OpenTelemetry tracer. Thêm span cho mỗi endpoint quan trọng (create, approve, leave, end, music/play). Trace ID trong log.

### Task 5.9 - Prometheus metrics
MeterRegistry + counter (liveroom.rooms.created, liveroom.rooms.ended), gauge (liveroom.rooms.active). Expose /actuator/prometheus.

### Task 5.10 - FE: Admin context menu
File: components/AdminContextMenu.tsx. Right-click participant → menu (chỉ owner thấy) → Kick / Mute mic.

### Task 5.11 - FE: SC-13 Kicked landing
File: components/KickedScreen.tsx. Hiển thị "Bạn đã bị kick khỏi phòng" + lý do + countdown "Thử lại sau X phút". Cache KICKED 1h sessionStorage.

### Task 5.12 - FE: Email display toggle
File: components/EmailPrivacyToggle.tsx. Switch "Ẩn email của tôi" → PATCH /privacy/email.

### Task 5.13 - i18n keys (~15 keys)
liveroom.admin.kick, liveroom.admin.mute_mic, liveroom.kicked.notice, liveroom.kicked.cooldown, liveroom.privacy.hide_email, liveroom.privacy.show_email, ... (15 keys).

### Task 5.14 - Final E2E test
Full flow: Create → Join multi-tab → Chat → Music → Annotation → Kick → Reopen. Test tất cả 32 edge cases (EC-01..EC-32). Test i18n EN+VI. Performance test (100 concurrent users).

## YÊU CẦU ĐẶC BIỆT:
- KHÔNG thêm comment
- KHÔNG hardcode message
- ⚠️ Audit log KHÔNG chứa sensitive data (chỉ log action + actor + target + reason)
- OpenTelemetry dependency cần add vào pom.xml
- micrometer-registry-prometheus dependency cần add vào pom.xml
- Email masking áp dụng cho tất cả chỗ hiển thị email
- Không commit tự động
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ 14 tasks
- Full E2E test pass (32 edge cases)
- Tự check 08-self-review-checklist.md
- Báo cáo file + checklist + warning
- Báo cáo tổng kết toàn project (85/85 tasks done)

## BẮT ĐẦU ĐỌC 12 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 5

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 5.1 POST /participants/{id}/kick + 5min cooldown | 4 giờ | ⭐⭐⭐ | ⏳ |
| 5.2 POST /participants/{id}/mute-mic + 30s cooldown | 3 giờ | ⭐⭐⭐ | ⏳ |
| 5.3 3 audit tables (Flyway migration) | 2 giờ | ⭐⭐ | ⏳ |
| 5.4 Audit logging service | 4 giờ | ⭐⭐⭐ | ⏳ |
| 5.5 Email masking helper | 2 giờ | ⭐⭐ | ⏳ |
| 5.6 PATCH /privacy/email | 3 giờ | ⭐⭐ | ⏳ |
| 5.7 IdempotencyCleanupJob | 2 giờ | ⭐⭐ | ⏳ |
| 5.8 OpenTelemetry tracing | 3 giờ | ⭐⭐⭐ | ⏳ |
| 5.9 Prometheus metrics | 3 giờ | ⭐⭐⭐ | ⏳ |
| 5.10 FE: Admin context menu | 4 giờ | ⭐⭐⭐ | ⏳ |
| 5.11 FE: SC-13 Kicked landing | 2 giờ | ⭐⭐ | ⏳ |
| 5.12 FE: Email display toggle | 2 giờ | ⭐⭐ | ⏳ |
| 5.13 i18n keys (~15 keys) | 1 giờ | ⭐ | ⏳ |
| 5.14 Final E2E test | 4 giờ | ⭐⭐ | ⏳ |

---

## Task 5.1: POST /participants/{id}/kick

**Mục tiêu**: Owner kick user khỏi phòng.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.7
- `liveroom-business-requirements.md` R-ADMIN-01..04, R-KICK-01..04

**Business rules**:
- R-ADMIN-01: Chỉ owner mới kick được
- R-ADMIN-02: Target user phải ACTIVE
- R-ADMIN-03: Set target state = KICKED, kickedAt = now
- R-ADMIN-04: 5 phút cooldown mới được join lại
- R-KICK-04: Frontend cache KICKED state 1 giờ (sessionStorage)

**Template**:

```java
@Data
@Builder
public class KickRequest {
    @NotBlank
    @Size(max = 200)
    private String reason;
}

@Transactional
public void kick(UUID roomId, UUID targetUserId, KickRequest request, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    if (!room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (room.getOwnerId().equals(targetUserId)) {
        throw new BusinessException("LIVEROOM_CANNOT_KICK_OWNER");
    }

    Participant target = participantRepository.findByRoomIdAndUserId(roomId, targetUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_USER_NOT_IN_SESSION"));

    if (target.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_USER_NOT_ACTIVE");
    }

    target.setState(ParticipantState.KICKED);
    target.setKickedAt(OffsetDateTime.now());
    target.setKickedReason(request.getReason());
    target.setLeftAt(OffsetDateTime.now());
    participantRepository.save(target);

    room.setCurrentParticipantCount(Math.max(0, room.getCurrentParticipantCount() - 1));
    liveRoomRepository.save(room);

    // Audit log
    auditService.logAdminAction(roomId, currentUserId, targetUserId, AdminActionType.KICK, request.getReason());

    realtimeBroadcaster.broadcast(roomId, "PARTICIPANT_KICKED", ParticipantKickedEvent.builder()
        .roomId(roomId)
        .userId(targetUserId)
        .kickedBy(currentUserId)
        .reason(request.getReason())
        .build());

    realtimeBroadcaster.sendToUser(targetUserId, "YOU_WERE_KICKED", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner kick user A → A state = KICKED, kickedAt = now
- [ ] A join lại trong 5 phút → 403 LIVEROOM_KICKED_COOLDOWN
- [ ] A join lại sau 5 phút → OK
- [ ] WS event broadcast
- [ ] Audit log recorded

---

## Task 5.2: POST /participants/{id}/mute-mic

**Mục tiêu**: Owner remote mute mic của participant.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.8
- `liveroom-business-requirements.md` R-ADMIN-05

**Business rules**:
- R-ADMIN-05: Owner có quyền mute mic (KHÔNG tắt camera)
- 30s cooldown để tránh spam
- User vẫn tự unmute được

**Template**:

```java
@Transactional
public void muteMic(UUID roomId, UUID targetUserId, UUID currentUserId) {
    // ... (similar validation)

    Optional<MediaState> mediaState = mediaStateRepository.findByRoomIdAndUserId(roomId, targetUserId);
    if (mediaState.isPresent()) {
        MediaState state = mediaState.get();
        if (state.getMutedByOwnerAt() != null) {
            long secondsSince = ChronoUnit.SECONDS.between(state.getMutedByOwnerAt(), OffsetDateTime.now());
            if (secondsSince < 30) {
                throw new BusinessException("LIVEROOM_REMOTE_MUTE_COOLDOWN");
            }
        }
        state.setMicEnabled(false);
        state.setMutedByOwnerAt(OffsetDateTime.now());
        state.setMutedByOwnerId(currentUserId);
        mediaStateRepository.save(state);
    }

    auditService.logAdminAction(roomId, currentUserId, targetUserId, AdminActionType.REMOTE_MUTE, "");

    realtimeBroadcaster.broadcast(roomId, "PARTICIPANT_MIC_MUTED_BY_OWNER", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner mute mic A → mic disabled
- [ ] A nhận WS event
- [ ] 30s cooldown

---

## Task 5.3: 3 Audit Tables (Flyway Migration)

**Mục tiêu**: Tạo 3 audit tables.

**Doc tham chiếu**:
- `liveroom-audit-logging.md` §2-4
- `liveroom-data-model.md` §4-6

**Migration file**: `V3__liveroom_audit_tables.sql`

```sql
-- Table 1: RoomAdminAction
CREATE TABLE liveroom_admin_actions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    target_user_id UUID NOT NULL,
    action_type VARCHAR(30) NOT NULL,  -- KICK | REMOTE_MUTE | BAN
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_liveroom_admin_actions_room ON liveroom_admin_actions(room_id);
CREATE INDEX idx_liveroom_admin_actions_target ON liveroom_admin_actions(target_user_id);

-- Table 2: RoomOwnershipHistory
CREATE TABLE liveroom_ownership_history (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    previous_owner_id UUID,
    new_owner_id UUID NOT NULL,
    reason VARCHAR(50),  -- MANUAL_TRANSFER | FORCE_ROLE_CHANGE | PLAN_DOWNGRADE
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_liveroom_ownership_history_room ON liveroom_ownership_history(room_id);

-- Table 3: UserPrivacyAction
CREATE TABLE liveroom_privacy_actions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    room_id UUID,
    action_type VARCHAR(30) NOT NULL,
    target_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_liveroom_privacy_actions_user ON liveroom_privacy_actions(user_id);
```

**Acceptance Criteria**:
- [ ] 3 tables created
- [ ] Indexes OK

---

## Task 5.4: Audit Logging Service

**Mục tiêu**: Service record audit actions.

**Doc tham chiếu**:
- `liveroom-audit-logging.md` §5

**Template**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RoomAdminActionRepository adminActionRepository;
    private final RoomOwnershipHistoryRepository ownershipHistoryRepository;
    private final UserPrivacyActionRepository privacyActionRepository;

    @Transactional
    public void logAdminAction(UUID roomId, UUID actorId, UUID targetUserId, AdminActionType actionType, String reason) {
        RoomAdminAction action = RoomAdminAction.builder()
            .roomId(roomId)
            .actorId(actorId)
            .targetUserId(targetUserId)
            .actionType(actionType)
            .reason(reason)
            .build();
        adminActionRepository.save(action);
        log.info("Audit: roomId={}, actor={}, target={}, action={}", roomId, actorId, targetUserId, actionType);
    }

    @Transactional
    public void logOwnershipTransfer(UUID roomId, UUID previousOwnerId, UUID newOwnerId, String reason) {
        // ... similar
    }

    @Transactional
    public void logPrivacyAction(UUID userId, UUID roomId, PrivacyActionType actionType, UUID targetUserId) {
        // ... similar
    }
}
```

**Acceptance Criteria**:
- [ ] 3 methods work
- [ ] Audit records được tạo
- [ ] Log có context

---

## Task 5.5: Email Masking Helper

**Mục tiêu**: Helper mask email theo GDPR.

**Doc tham chiếu**:
- `liveroom-business-requirements.md` R-DISPLAY-06

**Template**:

```java
public class EmailMasker {

    public static String mask(String email) {
        if (email == null || !email.contains("@")) return email;

        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 2) {
            return local.charAt(0) + "***@" + domain;
        }

        String maskedLocal = local.substring(0, 2) + "***";
        return maskedLocal + "@" + domain;
    }
}
```

**Examples**:
- `an@congty.com` → `an***@congty.com`
- `john.doe@example.com` → `jo***@example.com`

**Acceptance Criteria**:
- [ ] `an@congty.com` → `an***@congty.com`
- [ ] `john.doe@example.com` → `jo***@example.com`

---

## Task 5.6: PATCH /privacy/email

**Mục tiêu**: User toggle email masking.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.9

**Template**:

```java
@Data
@Builder
public class UpdateEmailPrivacyRequest {
    private boolean hideEmail;
}

@Transactional
public void updateEmailPrivacy(UUID roomId, UpdateEmailPrivacyRequest request, UUID currentUserId) {
    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    participant.setHideEmail(request.isHideEmail());
    participantRepository.save(participant);

    auditService.logPrivacyAction(currentUserId, roomId, PrivacyActionType.EMAIL_HIDE_TOGGLED, null);

    realtimeBroadcaster.broadcast(roomId, "PARTICIPANT_PRIVACY_CHANGED", ...);
}
```

**Acceptance Criteria**:
- [ ] User toggle hide email → broadcast
- [ ] Audit log

---

## Task 5.7: IdempotencyCleanupJob

**Mục tiêu**: Clean up old idempotency keys.

**Doc tham chiếu**:
- `liveroom-jobs.md` §9

**Template**:

```java
@Scheduled(cron = "0 0 2 * * *") // Daily 2AM
@SchedulerLock(name = "IdempotencyCleanupJob", lockAtLeastFor = "1m", lockAtMostFor = "30m")
public void cleanupIdempotencyKeys() {
    OffsetDateTime threshold = OffsetDateTime.now().minusHours(24);
    int deleted = idempotencyRepository.deleteByCreatedAtBefore(threshold);
    log.info("Idempotency cleanup: deleted {} keys", deleted);
}
```

**Acceptance Criteria**:
- [ ] Job chạy 2AM daily
- [ ] Xoá keys > 24h

---

## Task 5.8: OpenTelemetry Tracing

**Mục tiêu**: Setup tracing cho Liveroom.

**Doc tham chiếu**:
- `liveroom-audit-logging.md` §6

**Template**:

```java
@Configuration
public class OpenTelemetryConfig {
    @Bean
    public Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("liveroom");
    }
}

@Service
@RequiredArgsConstructor
public class LiveRoomFacadeImpl {
    private final Tracer tracer;

    @Transactional
    public CreateRoomResponse createRoom(...) {
        Span span = tracer.spanBuilder("createRoom")
            .setAttribute("userId", currentUserId.toString())
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // ... logic
            return response;
        } finally {
            span.end();
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Trace ID có trong logs
- [ ] Span cho mỗi endpoint

---

## Task 5.9: Prometheus Metrics

**Mục tiêu**: Expose metrics.

**Doc tham chiếu**:
- `liveroom-audit-logging.md` §6

**Template**:

```java
@Component
@RequiredArgsConstructor
public class LiveroomMetrics {

    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.counter("liveroom.rooms.created", "type", "manual");
        meterRegistry.counter("liveroom.rooms.ended", "reason", "manual");
        meterRegistry.gauge("liveroom.rooms.active", () -> activeRoomCount);
    }

    public void incrementRoomCreated() {
        meterRegistry.counter("liveroom.rooms.created").increment();
    }

    public void incrementRoomEnded(String reason) {
        meterRegistry.counter("liveroom.rooms.ended", "reason", reason).increment();
    }
}
```

**Acceptance Criteria**:
- [ ] /actuator/prometheus expose metrics
- [ ] Counter increment đúng

---

## Task 5.10-5.12: FE Components

### Task 5.10: Admin Context Menu

**Files**:
- `frontend/src/features/liveroom/components/AdminContextMenu.tsx`

**Components**:
- Right-click participant → context menu
- "Kick" / "Mute mic" options (chỉ cho owner)

**Acceptance Criteria**:
- [ ] Owner right-click → menu
- [ ] Click "Kick" → gọi API
- [ ] Click "Mute mic" → gọi API

---

### Task 5.11: SC-13 Kicked Landing

**Files**:
- `frontend/src/features/liveroom/components/KickedScreen.tsx`

**Components**:
- Hiển thị "Bạn đã bị kick khỏi phòng"
- Lý do
- Nút "Về trang chủ"
- Countdown "Thử lại sau X phút"

**Acceptance Criteria**:
- [ ] WS event YOU_WERE_KICKED → show SC-13
- [ ] Cache KICKED 1h (sessionStorage)

---

### Task 5.12: Email Display Toggle

**Files**:
- `frontend/src/features/liveroom/components/EmailPrivacyToggle.tsx`

**Components**:
- Switch "Ẩn email của tôi"
- Call PATCH /privacy/email

**Acceptance Criteria**:
- [ ] Toggle broadcast
- [ ] Email masked khi bật

---

## Task 5.13: i18n Keys

**Keys**:
- `liveroom.admin.kick`
- `liveroom.admin.mute_mic`
- `liveroom.kicked.notice`
- `liveroom.kicked.cooldown`
- `liveroom.privacy.hide_email`
- `liveroom.privacy.show_email`
- ... (~15 keys)

---

## Task 5.14: Final E2E Test

**Mục tiêu**: Full flow test.

**Scenarios**:
1. Create room → join multi-tab → chat → music → annotation → kick → reopen
2. Race condition tests
3. Edge cases (EC-01..EC-32)
4. All i18n keys render correctly
5. Performance test (100 concurrent users)

**Acceptance Criteria**:
- [ ] Toàn bộ flow pass
- [ ] Performance OK

---

## 🚦 Definition of Done Phase 5

- [ ] Tất cả 14 tasks DONE
- [ ] Kick + 5min cooldown
- [ ] Remote mute + 30s cooldown
- [ ] 3 audit tables ghi log
- [ ] Email masking toggle
- [ ] OpenTelemetry + Prometheus
- [ ] 3 FE components
- [ ] i18n ~15 keys
- [ ] Full E2E test pass

---

## 🎯 FINAL — Definition of Done cho TOÀN BỘ PROJECT

- [ ] Tất cả 5 phase DONE
- [ ] 25+ REST endpoints
- [ ] 30+ WS events
- [ ] 13 screens
- [ ] 200+ i18n keys EN + VI
- [ ] 9 background jobs
- [ ] 3 audit tables
- [ ] 32 edge cases tested
- [ ] No hardcoded messages
- [ ] No code comments
- [ ] No @Version trên LiveRoom
- [ ] 8 workspace rules tuân thủ

---

**Cập nhật**: 2026-07-26
