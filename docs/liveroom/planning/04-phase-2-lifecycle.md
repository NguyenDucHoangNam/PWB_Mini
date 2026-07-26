# Phase 2: Lifecycle (Leave/End/Reopen)

**Thời gian**: 7-10 ngày
**Mục tiêu**: Reject, leave, end, reopen + edge cases (capacity, grace, multi-tab)
**Doc tham chiếu chính**: `liveroom-api-spec.md` §2.3-2.8, `liveroom-state-machines.md` §2-3, `liveroom-concurrency.md` §2, `liveroom-jobs.md` §2-4

---

## 🤖 AGENT BRIEFING

> **Copy toàn bộ khối code dưới đây → paste cho AI agent (Cursor).**
> Agent sẽ tự đọc docs và làm theo đúng quy trình.

```
Bạn là Senior Backend Dev + Frontend Dev cho dự án PWB MiNi (Spring Boot multi-module + Next.js 14 + TypeScript).

Project: d:\Learning\Project\PWB_MiNi\
Module: liveroom (Backend/modules/liveroom/, FE: frontend/src/features/liveroom/)

## 8 WORKSPACE RULES (BẮT BUỘC TUÂN THỦ - đọc file .cursor/rules/):
1. senior-dev-coding-standards.mdc (SOLID, Clean Code)
2. prefer-lombok-backend.mdc (@Slf4j, @RequiredArgsConstructor, @Builder)
3. no-code-comments-backend.mdc (KHÔNG comment trong Java)
4. no-code-comments-frontend.mdc (KHÔNG comment trong TS/TSX)
5. no-hardcoded-messages-backend.mdc (dùng MessageSource, i18n)
6. no-auto-create-tests-backend.mdc (KHÔNG tự viết test)
7. no-auto-commit-push-backend.mdc (KHÔNG tự git commit/push)
8. respond-in-vietnamese.mdc (trả lời user bằng tiếng Việt)

## PHASE 2 LÀ GÌ:
Lifecycle - Reject (OWNER_REJECT), Cancel, Rejoin, Leave (owner debounce 3s + grace 60s), End room + 5s undo, Reopen, Multi-tab conflict. 6 BE endpoints + 9 WS events + 3 background jobs + 4 FE screens + i18n ~25 keys.

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md
2. docs/liveroom/planning/00-reading-guide.md
3. docs/liveroom/planning/04-phase-2-lifecycle.md (FILE NÀY - chi tiết 18 tasks)
4. docs/liveroom/planning/10-codebase-templates.md
5. docs/liveroom/planning/08-self-review-checklist.md
6. docs/liveroom/api-spec.md §2.3-2.8 (reject, cancel, rejoin, leave, end, reopen)
7. docs/liveroom/state-machines.md §2 (leave flow) + §3 (reopen)
8. docs/liveroom/concurrency.md §2 (pessimistic lock), §2.3 (debounce), §2.4 (UPSERT), §2.5 (multi-tab)
9. docs/liveroom/jobs.md §2-4 (GraceExpiry, EmptyRoomTimeout, ParticipantReconnectTimeout)
10. docs/liveroom/screen-inventory.md SC-02 (Owner Waiting), SC-03b (Rejected)
11. docs/liveroom/ws-protocol.md §4.1-4.2 (9 events: REQUEST_REJECTED_BY_OWNER, REQUEST_REJECTED_BY_CAPACITY, PARTICIPANT_LEFT, OWNER_LEFT, OWNER_REJOINED, ROOM_MANUAL_ENDED, ROOM_AUTO_ENDED, ROOM_REVIVED, ROOM_REOPENED)
12. docs/liveroom/business-requirements.md R-LEAVE-01..09, R-END-01..12, R-REOPEN-01..08, R-CAPACITY-01..02, R-REJECT-01..04
13. docs/liveroom/i18n-keys.md (25 keys mới)

## 18 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 2.1 - POST /reject (OWNER_REJECT)
Owner reject JoinRequest PENDING. Set state=REJECTED, reason=OWNER_REJECT, incrementRejectCountByOwner (CHỈ cho OWNER_REJECT, không cho ROOM_FULL). WS event REQUEST_REJECTED_BY_OWNER + JOIN_REQUEST_RESOLVED.

### Task 2.2 - Capacity check atomic
⚠️ Luôn dùng entityManager.find(LiveRoom.class, roomId, PESSIMISTIC_WRITE) trước khi check capacity. Test 2 thread approve cùng lúc → 1 success, 1 fail.

### Task 2.3 - RejectCounter UPSERT
Native query INSERT ... ON CONFLICT (room_id, user_id) DO UPDATE SET count = count + 1. Atomic, race-safe.

### Task 2.4 - Lock after 3 rejects
Nếu rejectCountByOwner >= 3 → throw LIVEROOM_LOCKED_AFTER_3_REJECTS trong createJoinRequest.

### Task 2.5 - POST /cancel, /rejoin
cancelJoinRequest: chỉ PENDING mới cancel, không tăng counter. rejoin: was_approved=TRUE → ACTIVE, was_approved=FALSE → LIVEROOM_NEEDS_NEW_REQUEST, vượt capacity → LIVEROOM_CAPACITY_FULL.

### Task 2.6 - POST /leave (Owner Debounce 3s + Grace 60s)
⚠️ Owner leave: schedule task 3s sau (cancel được nếu rejoin trong 3s). Sau 3s broadcast OWNER_LEFT + set owner_left_at + reserved_owner_slot=TRUE + effective_max = max-1. Grace 60s (job tự end nếu owner không rejoin). Participant leave: state=ENDED, count-1. Last leave → endRoom với EMPTY_TIMEOUT.

### Task 2.7 - GraceExpiryJob
@Scheduled(fixedDelay=30s) + @SchedulerLock. Query rooms có owner_left_at > 60s → endRoom với reason OWNER_GRACE_EXPIRED.

### Task 2.8 - EmptyRoomTimeoutJob
@Scheduled(fixedDelay=60s) + @SchedulerLock. Query rooms ACTIVE + current_participant_count=0 + last_participant_left_at > 5min → endRoom với EMPTY_TIMEOUT.

### Task 2.9 - POST /end + 5s undo
endRoom: status=ENDED, ended_at=now, ended_reason=manual. Update cycle ended_at, tất cả ACTIVE participant → ENDED, tất cả PENDING → EXPIRED, delete PlaybackState. undoEnd: chỉ trong 5s, throw LIVEROOM_UNDO_EXPIRED sau 5s.

### Task 2.10 - POST /reopen
Tạo RoomSessionCycle mới (cycleNumber++), status=ACTIVE, current_participant_count=0, xoá RejectCounter (R-REOPEN-06), reset was_approved=FALSE.

### Task 2.11 - Multi-tab conflict
Flyway migration V2 với partial unique index: WHERE state IN ('ACTIVE', 'RECONNECTING'). FE: BroadcastChannel hiển thị warning khi 2 tab cùng user.

### Task 2.12 - WS: 9 events
RealtimeBroadcaster cho 9 events theo ws-protocol.md §4.1-4.2.

### Task 2.13 - FE: SC-02 Owner Waiting Room
components/OwnerWaitingRoom.tsx. List pending JoinRequest. Mỗi row: avatar, email, button Duyệt/Từ chối. Counter X/Y participants.

### Task 2.14 - FE: SC-03b Rejected/Expired
components/RejectedNotice.tsx. Hiển thị lý do (OWNER_REJECT/CAPACITY_FULL/ROOM_ENDED) + nút Về trang chủ.

### Task 2.15 - FE: Owner leave banner
components/OwnerLeftBanner.tsx. Hiển thị "Owner đã rời phòng - chờ owner quay lại (60s)" + countdown timer + music control disabled.

### Task 2.16 - FE: End room + undo toast
components/EndRoomUndoToast.tsx. Click "Kết thúc" → POST /end → toast 5s có button "Hoàn tác" → click → POST /end/undo.

### Task 2.17 - i18n keys (~25 keys)
liveroom.reject.success, liveroom.leave.owner.broadcast, liveroom.leave.owner.grace_seconds, liveroom.end.confirm, liveroom.end.undo, liveroom.reopen.success, liveroom.rejected.notice.owner_reject, ... (25 keys) vào 3 file BE + 2 file FE.

### Task 2.18 - E2E test 15+ edge cases
Test EC-03 (capacity full), EC-04 (idempotent), EC-05 (3 rejects → LOCKED), EC-06 (2 rejects → OK), EC-07 (owner rejoin trong 3s), EC-08 (grace expiry), EC-09 (owner rejoin huỷ grace), EC-10 (participant leave), EC-11 (last leave → end), EC-12 (end + undo), EC-13 (undo sau 5s fail), EC-14 (reopen), EC-15 (counters reset), EC-23 (race condition), EC-24 (multi-tab).

## YÊU CẦU ĐẶC BIỆT:
- KHÔNG thêm comment
- KHÔNG hardcode message
- ⚠️ PESSIMISTIC_WRITE trên LiveRoom khi update capacity, leave, end, reopen
- @SchedulerLock cho mọi background job
- Owner leave debounce: TaskScheduler + cancelable future
- ShedLock dependency cần được add vào pom.xml
- Không commit tự động
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ 18 tasks
- 15+ edge cases test pass
- Tự check 08-self-review-checklist.md
- Báo cáo file đã tạo + checklist pass/fail + warning

## BẮT ĐẦU ĐỌC 13 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 2

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 2.1 POST /reject (OWNER_REJECT) | 4 giờ | ⭐⭐⭐ | ⏳ |
| 2.2 Capacity check atomic | 2 giờ | ⭐⭐⭐ | ⏳ |
| 2.3 RejectCounter UPSERT | 3 giờ | ⭐⭐⭐ | ⏳ |
| 2.4 Lock after 3 rejects | 2 giờ | ⭐⭐ | ⏳ |
| 2.5 POST /cancel, /rejoin | 4 giờ | ⭐⭐⭐ | ⏳ |
| 2.6 POST /leave (owner debounce 3s + grace 60s) | 6 giờ | ⭐⭐⭐⭐ | ⏳ |
| 2.7 GraceExpiryJob | 4 giờ | ⭐⭐⭐ | ⏳ |
| 2.8 EmptyRoomTimeoutJob | 3 giờ | ⭐⭐ | ⏳ |
| 2.9 POST /end + 5s undo | 5 giờ | ⭐⭐⭐⭐ | ⏳ |
| 2.10 POST /reopen | 4 giờ | ⭐⭐⭐ | ⏳ |
| 2.11 Multi-tab conflict detection | 4 giờ | ⭐⭐⭐ | ⏳ |
| 2.12 WS events (9 events) | 6 giờ | ⭐⭐⭐ | ⏳ |
| 2.13 FE: SC-02 Owner Waiting Room | 4 giờ | ⭐⭐ | ⏳ |
| 2.14 FE: SC-03b Rejected/Expired UI | 2 giờ | ⭐⭐ | ⏳ |
| 2.15 FE: Owner leave banner + grace timer | 3 giờ | ⭐⭐ | ⏳ |
| 2.16 FE: End room + undo toast | 3 giờ | ⭐⭐ | ⏳ |
| 2.17 i18n keys (~25 keys) | 2 giờ | ⭐ | ⏳ |
| 2.18 E2E test (15+ edge cases) | 5 giờ | ⭐⭐ | ⏳ |

---

## Task 2.1: POST /reject (OWNER_REJECT)

**Mục tiêu**: Owner từ chối user với lý do OWNER_REJECT.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.3
- `liveroom-business-requirements.md` R-JOIN-09 (RejectionReason enum)
- `liveroom-state-machines.md` §1 (PENDING → REJECTED)

**Business rules**:
- R-JOIN-09: RejectionReason = `OWNER_REJECT | ROOM_FULL | ROOM_ENDED | USER_CANCELLED | NONE`
- R-REJECT-01: Chỉ owner mới được reject
- R-REJECT-02: JoinRequest phải PENDING
- R-REJECT-03: Set state = REJECTED, rejectionReason = OWNER_REJECT
- R-REJECT-04: rejectCountByOwner++ (CHỈ cho OWNER_REJECT, không cho ROOM_FULL)

**Template Service**:

```java
@Transactional
public void rejectJoinRequest(UUID requestId, RejectionReason customReason, UUID currentUserId) {
    JoinRequest request = joinRequestRepository.findById(requestId)
        .orElseThrow(() -> new BusinessException("JOIN_REQUEST_NOT_FOUND"));

    LiveRoom room = entityManager.find(
        LiveRoom.class, request.getRoomId(), LockModeType.PESSIMISTIC_WRITE
    );

    if (!room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (request.getState() != JoinRequestState.PENDING) {
        throw new BusinessException("JOIN_REQUEST_NOT_PENDING");
    }

    RejectionReason reason = customReason != null ? customReason : RejectionReason.OWNER_REJECT;

    request.setState(JoinRequestState.REJECTED);
    request.setRejectionReason(reason);
    request.setResolvedAt(OffsetDateTime.now());
    joinRequestRepository.save(request);

    // R-REJECT-04: CHỈ tăng counter cho OWNER_REJECT
    if (reason == RejectionReason.OWNER_REJECT) {
        rejectCounterRepository.incrementRejectCountByOwner(room.getId(), request.getUserId());
    }

    realtimeBroadcaster.sendToUser(request.getUserId(), "REQUEST_REJECTED_BY_OWNER", ...);
    realtimeBroadcaster.broadcast(room.getId(), "JOIN_REQUEST_RESOLVED", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner reject user A → JoinRequest A state = REJECTED, reason = OWNER_REJECT
- [ ] rejectCountByOwner(A) = 1
- [ ] User A nhận WS event `REQUEST_REJECTED_BY_OWNER`
- [ ] Owner reject 3 lần cùng user A → A bị LOCKED

---

## Task 2.2: Capacity Check Atomic

**Mục tiêu**: Đảm bảo capacity check atomic qua pessimistic lock.

**Doc tham chiếu**:
- `liveroom-concurrency.md` §2.1
- `liveroom-business-requirements.md` EC-24 (race condition)

**Test scenario**:
- Phòng max=7, hiện tại 6 participants
- 2 user A, B đồng thời approve
- 1 success, 1 fail với ROOM_FULL

**Template** (cần áp dụng trong approveJoinRequest):

```java
// ⚠️ LUÔN dùng PESSIMISTIC_WRITE trên LiveRoom khi update capacity
LiveRoom room = entityManager.find(
    LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE
);
// ... (logic check + update)
```

**Acceptance Criteria**:
- [ ] Chạy 2 thread approve đồng thời → 1 success, 1 fail
- [ ] current_participant_count không vượt max
- [ ] Không có race condition

---

## Task 2.3: RejectCounter UPSERT

**Mục tiêu**: Atomic increment reject count.

**Doc tham chiếu**:
- `liveroom-concurrency.md` §2.4

**Template Repository**:

```java
public interface RejectCounterRepository extends JpaRepository<RejectCounter, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO liveroom_reject_counters (id, room_id, user_id, reject_count_by_owner, last_rejected_at, last_rejection_reason)
        VALUES (gen_random_uuid(), :roomId, :userId, 1, now(), :reason)
        ON CONFLICT (room_id, user_id)
        DO UPDATE SET
            reject_count_by_owner = liveroom_reject_counters.reject_count_by_owner + 1,
            last_rejected_at = now(),
            last_rejection_reason = :reason
        """, nativeQuery = true)
    void incrementRejectCountByOwner(@Param("roomId") UUID roomId, @Param("userId") UUID userId, @Param("reason") String reason);
}
```

**Acceptance Criteria**:
- [ ] Increment atomic
- [ ] Race condition khi 2 rejects đồng thời → đúng count
- [ ] Unique constraint (room_id, user_id) enforced

---

## Task 2.4: Lock after 3 rejects

**Mục tiêu**: User bị 3 rejects → LOCKED → không gửi request được nữa.

**Doc tham chiếu**:
- `liveroom-state-machines.md` §1 (LOCKED state)

**Logic** (trong createJoinRequest):

```java
Optional<RejectCounter> counter = rejectCounterRepository.findByRoomIdAndUserId(roomId, currentUserId);
if (counter.isPresent() && counter.get().getRejectCountByOwner() >= 3) {
    throw new BusinessException("LIVEROOM_LOCKED_AFTER_3_REJECTS");
}
```

**Acceptance Criteria**:
- [ ] User bị 3 rejects → request tiếp theo → 403 LIVEROOM_LOCKED_AFTER_3_REJECTS
- [ ] Reopen → counter reset → user có thể request lại

---

## Task 2.5: POST /cancel, /rejoin

**Mục tiêu**: User cancel request / rejoin vào phòng đã vào trước đó.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.4-5
- `liveroom-state-machines.md` §1 (PENDING → CANCELLED, ACTIVE → RECONNECTING)

**Business rules**:
- R-CANCEL-01: Chỉ user sở hữu request mới cancel được
- R-CANCEL-02: Chỉ PENDING mới cancel được
- R-CANCEL-03: Reject counter KHÔNG tăng (R-REJECT-04)

**R-REJOIN-01..05**:
- User đã ACTIVE → leave → muốn vào lại
- User đã có Participant row (state = ENDED/LEFT) → rejoin
- User có was_approved = TRUE → vào thẳng ACTIVE
- User có was_approved = FALSE → phải gửi request mới

**Template cancelJoinRequest**:

```java
@Transactional
public void cancelJoinRequest(UUID requestId, UUID currentUserId) {
    JoinRequest request = joinRequestRepository.findById(requestId)
        .orElseThrow(() -> new BusinessException("JOIN_REQUEST_NOT_FOUND"));

    if (!request.getUserId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER_OF_REQUEST");
    }

    if (request.getState() != JoinRequestState.PENDING) {
        throw new BusinessException("JOIN_REQUEST_NOT_PENDING");
    }

    request.setState(JoinRequestState.CANCELLED);
    request.setRejectionReason(RejectionReason.USER_CANCELLED);
    request.setResolvedAt(OffsetDateTime.now());
    joinRequestRepository.save(request);

    realtimeBroadcaster.broadcast(request.getRoomId(), "JOIN_REQUEST_RESOLVED", ...);
}
```

**Template rejoin (phức tạp hơn)**:

```java
@Transactional
public void rejoin(UUID roomId, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    if (room.getStatus() != RoomStatus.ACTIVE) {
        throw new BusinessException("LIVEROOM_ROOM_ENDED");
    }

    Optional<Participant> existing = participantRepository.findByRoomIdAndUserId(roomId, currentUserId);
    if (existing.isEmpty()) {
        throw new BusinessException("LIVEROOM_NOT_PARTICIPANT");
    }

    Participant p = existing.get();
    // R-REJOIN-08: was_approved = TRUE → vào thẳng ACTIVE
    if (!p.getWasApproved()) {
        throw new BusinessException("LIVEROOM_NEEDS_NEW_REQUEST");
    }

    if (p.getState() == ParticipantState.ACTIVE || p.getState() == ParticipantState.RECONNECTING) {
        throw new BusinessException("LIVEROOM_ALREADY_IN_SESSION");
    }

    int effectiveMax = room.getMaxParticipants() - (room.getReservedOwnerSlot() ? 1 : 0);
    if (room.getCurrentParticipantCount() >= effectiveMax) {
        throw new BusinessException("LIVEROOM_CAPACITY_FULL");
    }

    p.setState(ParticipantState.ACTIVE);
    p.setJoinedAt(OffsetDateTime.now());
    p.setLeftAt(null);
    participantRepository.save(p);

    room.setCurrentParticipantCount(room.getCurrentParticipantCount() + 1);
    liveRoomRepository.save(room);

    realtimeBroadcaster.broadcast(roomId, "PARTICIPANT_JOINED", ...);
}
```

**Acceptance Criteria**:
- [ ] Cancel PENDING request → state = CANCELLED
- [ ] Cancel non-PENDING → 400
- [ ] Rejoin was_approved = TRUE → ACTIVE
- [ ] Rejoin was_approved = FALSE → 403 LIVEROOM_NEEDS_NEW_REQUEST
- [ ] Rejoin vượt capacity → 409 LIVEROOM_CAPACITY_FULL

---

## Task 2.6: POST /leave (Owner Debounce + Grace)

**Mục tiêu**: User (owner hoặc participant) rời phòng.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.6
- `liveroom-state-machines.md` §2 (OWNER_LEAVING)
- `liveroom-concurrency.md` §2.3 (Debounce 3s)
- `liveroom-business-requirements.md` R-LEAVE-01..09

**Business rules**:
- R-LEAVE-01: Chỉ participant state ACTIVE mới leave được
- R-LEAVE-02: Set participant state = ENDED, leftAt = now
- R-LEAVE-03: current_participant_count - 1
- R-LEAVE-04: Nếu owner:
  - 3s debounce (nếu rejoin trong 3s → huỷ leave)
  - Sau 3s, broadcast OWNER_LEFT
  - Set owner_left_at = now, reserved_owner_slot = TRUE
  - effective_max = max - 1
  - Grace 60s (sau grace → phòng ENDED nếu owner không rejoin)
- R-LEAVE-05: Nếu participant thường → broadcast PARTICIPANT_LEFT

**Template Service**:

```java
@Transactional
public void leave(UUID roomId, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    if (participant.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_NOT_ACTIVE");
    }

    if (room.getOwnerId().equals(currentUserId)) {
        // Owner leave - debounce 3s
        leaveDebounceService.scheduleLeave(roomId, currentUserId);
        realtimeBroadcaster.sendToUser(currentUserId(), "OWNER_LEAVE_DEBOUNCED", Map.of("seconds", 3));
    } else {
        // Participant leave
        participant.setState(ParticipantState.ENDED);
        participant.setLeftAt(OffsetDateTime.now());
        participantRepository.save(participant);

        room.setCurrentParticipantCount(room.getCurrentParticipantCount() - 1);
        liveRoomRepository.save(room);

        realtimeBroadcaster.broadcast(roomId, "PARTICIPANT_LEFT", ...);

        if (room.getCurrentParticipantCount() == 0) {
            endRoom(room, EndedReason.EMPTY_TIMEOUT);
        }
    }
}
```

**Template LeaveDebounceService**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveDebounceService {
    private final TaskScheduler taskScheduler;
    private final LiveRoomService roomService;
    private final Map<UUID, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();

    public void scheduleLeave(UUID roomId, UUID ownerId) {
        cancelDebounce(roomId);

        ScheduledFuture<?> task = taskScheduler.schedule(() -> {
            try {
                roomService.processOwnerLeave(roomId, ownerId);
            } catch (Exception e) {
                log.error("Error processing owner leave", e);
            } finally {
                debounceTasks.remove(roomId);
            }
        }, Instant.now().plusSeconds(3));

        debounceTasks.put(roomId, task);
    }

    public void cancelDebounce(UUID roomId) {
        ScheduledFuture<?> task = debounceTasks.remove(roomId);
        if (task != null) task.cancel(false);
    }
}
```

**Acceptance Criteria**:
- [ ] Owner click leave → toast "Owner đã rời" trong 3s
- [ ] Owner rejoin trong 3s → huỷ leave, thông báo "owner rejoin"
- [ ] Owner không rejoin trong 3s → broadcast OWNER_LEFT
- [ ] Owner leave → reserved_owner_slot = TRUE, effective_max = max - 1
- [ ] Grace 60s → nếu owner không rejoin → auto-end
- [ ] Participant leave → state = ENDED, count - 1
- [ ] Last participant leave → auto-end

---

## Task 2.7: GraceExpiryJob

**Mục tiêu**: Auto-end phòng khi owner grace hết.

**Doc tham chiếu**:
- `liveroom-jobs.md` §2
- `liveroom-concurrency.md` (ShedLock leader election)

**Template**:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GraceExpiryJob {

    private final LiveRoomRepository liveRoomRepository;
    private final LiveRoomService roomService;

    @Scheduled(fixedDelay = 30_000) // 30s
    @SchedulerLock(name = "GraceExpiryJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
    public void checkGraceExpiry() {
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(60); // grace 60s default
        List<LiveRoom> expiredRooms = liveRoomRepository.findByStatusAndOwnerLeftAtBefore(
            RoomStatus.ACTIVE, threshold
        );
        for (LiveRoom room : expiredRooms) {
            log.info("Auto-ending room due to grace expiry: roomId={}", room.getId());
            roomService.endRoom(room.getId(), EndedReason.OWNER_GRACE_EXPIRED);
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Job chạy mỗi 30s
- [ ] Phòng có owner_left_at > 60s → auto-end
- [ ] reason = OWNER_GRACE_EXPIRED
- [ ] ShedLock chỉ 1 instance chạy job

---

## Task 2.8: EmptyRoomTimeoutJob

**Mục tiêu**: Auto-end phòng khi không còn participant.

**Doc tham chiếu**:
- `liveroom-jobs.md` §3

**Template**:

```java
@Scheduled(fixedDelay = 60_000)
@SchedulerLock(name = "EmptyRoomTimeoutJob", lockAtLeastFor = "10s", lockAtMostFor = "5m")
public void checkEmptyRooms() {
    OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5); // 5 min empty
    List<LiveRoom> emptyRooms = liveRoomRepository.findByStatusAndCurrentParticipantCountAndLastParticipantLeftAtBefore(
        RoomStatus.ACTIVE, 0, threshold
    );
    for (LiveRoom room : emptyRooms) {
        log.info("Auto-ending empty room: roomId={}", room.getId());
        roomService.endRoom(room.getId(), EndedReason.EMPTY_TIMEOUT);
    }
}
```

**Acceptance Criteria**:
- [ ] Phòng empty > 5min → auto-end
- [ ] reason = EMPTY_TIMEOUT

---

## Task 2.9: POST /end + 5s Undo

**Mục tiêu**: Owner end phòng với khả năng undo trong 5s.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.7
- `liveroom-state-machines.md` §2 (ENDED)
- `liveroom-business-requirements.md` R-END-01..12, R-END-12 (undo)

**Business rules**:
- R-END-01: Chỉ owner mới end được
- R-END-02: Set status = ENDED, ended_at = now, ended_reason = 'manual'
- R-END-03: Update RoomSessionCycle.ended_at = now
- R-END-04: Update tất cả Participant state ACTIVE → ENDED
- R-END-05: Update tất cả JoinRequest PENDING → EXPIRED
- R-END-06: Keep annotation, chat message (v1.8)
- R-END-07: Delete PlaybackState (R-MUSIC-07)
- R-END-08: R-END-12: 5s undo window

**Template endRoom**:

```java
@Transactional
public void endRoom(UUID roomId, EndedReason reason, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    if (!room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (room.getStatus() == RoomStatus.ENDED) {
        throw new BusinessException("LIVEROOM_ALREADY_ENDED");
    }

    room.setStatus(RoomStatus.ENDED);
    room.setEndedAt(OffsetDateTime.now());
    room.setEndedReason(reason);
    liveRoomRepository.save(room);

    // Update cycle
    RoomSessionCycle cycle = cycleRepository.findById(room.getCurrentSessionCycleId()).orElseThrow();
    cycle.setEndedAt(OffsetDateTime.now());
    cycleRepository.save(cycle);

    // Update participants
    participantRepository.updateAllActiveToEnded(roomId, OffsetDateTime.now());

    // Expire pending requests
    joinRequestRepository.updateAllPendingToExpired(roomId, OffsetDateTime.now(), RejectionReason.ROOM_ENDED);

    // Delete PlaybackState
    playbackStateRepository.deleteByRoomId(roomId);

    realtimeBroadcaster.broadcast(roomId, "ROOM_" + reason.name(), ...);
}
```

**Template undoEnd**:

```java
@Transactional
public void undoEnd(UUID roomId, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    if (!room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (room.getStatus() != RoomStatus.ENDED) {
        throw new BusinessException("LIVEROOM_NOT_ENDED");
    }

    if (Duration.between(room.getEndedAt(), OffsetDateTime.now()).getSeconds() > 5) {
        throw new BusinessException("LIVEROOM_UNDO_EXPIRED");
    }

    room.setStatus(RoomStatus.ACTIVE);
    room.setEndedAt(null);
    room.setEndedReason(null);
    liveRoomRepository.save(room);

    realtimeBroadcaster.broadcast(roomId, "ROOM_REVIVED", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner end → status = ENDED, ended_at = now
- [ ] Click "Hoàn tác" trong 5s → status = ACTIVE
- [ ] Click "Hoàn tác" sau 5s → 400 LIVEROOM_UNDO_EXPIRED
- [ ] Non-owner end → 403 LIVEROOM_NOT_OWNER

---

## Task 2.10: POST /reopen

**Mục tiêu**: Owner reopen phòng ENDED.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.8
- `liveroom-state-machines.md` §3 (REOPEN)
- `liveroom-business-requirements.md` R-REOPEN-01..08

**Business rules**:
- R-REOPEN-01: Chỉ owner mới reopen
- R-REOPEN-02: Room phải ENDED
- R-REOPEN-03: Owner phải còn PRO plan
- R-REOPEN-04: Tạo RoomSessionCycle mới (cycleNumber++)
- R-REOPEN-05: Reset current_participant_count = 0
- R-REOPEN-06: Reset rejectCountByOwner (counter cho user bị xoá)
- R-REOPEN-07: Reset was_approved (tất cả về FALSE)
- R-REOPEN-08: was_approved = was ACTIVE (R-B-Fix-10)

**Template**:

```java
@Transactional
public void reopenRoom(UUID roomId, UUID currentUserId) {
    LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);

    if (!room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (room.getStatus() != RoomStatus.ENDED) {
        throw new BusinessException("LIVEROOM_NOT_ENDED");
    }

    UserDto user = userService.getUser(currentUserId);
    if (!user.getPlan().equals("PRO")) {
        throw new BusinessException("USER_NOT_PRO");
    }

    RoomSessionCycle oldCycle = cycleRepository.findById(room.getCurrentSessionCycleId()).orElseThrow();
    RoomSessionCycle newCycle = RoomSessionCycle.builder()
        .roomId(roomId)
        .cycleNumber(oldCycle.getCycleNumber() + 1)
        .startedAt(OffsetDateTime.now())
        .build();
    newCycle = cycleRepository.save(newCycle);

    room.setStatus(RoomStatus.ACTIVE);
    room.setEndedAt(null);
    room.setEndedReason(null);
    room.setCurrentParticipantCount(0);
    room.setCurrentSessionCycleId(newCycle.getId());
    liveRoomRepository.save(room);

    // R-REOPEN-06: Reset reject counters
    rejectCounterRepository.deleteAllByRoomId(roomId);

    realtimeBroadcaster.broadcast(roomId, "ROOM_REOPENED", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner reopen → status = ACTIVE, cycle mới
- [ ] rejectCountByOwner reset về 0
- [ ] was_approved reset (user bị 3 lần reject có thể request lại)
- [ ] Non-owner reopen → 403

---

## Task 2.11: Multi-tab Conflict Detection

**Mục tiêu**: Phát hiện 2 tab cùng user cùng phòng.

**Doc tham chiếu**:
- `liveroom-business-requirements.md` R-JOIN-10
- `liveroom-concurrency.md` §2.5 (Partial unique index)

**Template Flyway migration**:

```sql
-- V2__liveroom_partial_unique_index.sql
CREATE UNIQUE INDEX idx_liveroom_participants_active
ON liveroom_participants (room_id, user_id)
WHERE state IN ('ACTIVE', 'RECONNECTING');
```

**FE: BroadcastChannel**:

```typescript
// frontend/src/features/liveroom/hooks/use-multi-tab-detection.ts
import { useEffect } from "react";

export function useMultiTabDetection(roomId: string, userId: string) {
  useEffect(() => {
    const channel = new BroadcastChannel(`liveroom-${roomId}-${userId}`);
    channel.onmessage = (event) => {
      if (event.data.type === "JOIN") {
        // Another tab is trying to join
        window.alert("Bạn đang ở phòng này ở tab khác, vui lòng dùng tab hiện tại hoặc đóng tab này");
        window.close();
      }
    };
    channel.postMessage({ type: "JOIN", timestamp: Date.now() });
    return () => channel.close();
  }, [roomId, userId]);
}
```

**Acceptance Criteria**:
- [ ] User mở 2 tab → tab 2 hiển thị warning
- [ ] User cố tình bypass → 2 rows ACTIVE → DB constraint fail → 500

---

## Task 2.12: WS Events (9 events)

**Mục tiêu**: Broadcast 9 WS events cho lifecycle.

**Doc tham chiếu**:
- `liveroom-ws-protocol.md` §4.1-4.2

**9 events**:

| # | Event | Trigger |
|---|---|---|
| 1 | `REQUEST_REJECTED_BY_OWNER` | Owner reject user |
| 2 | `REQUEST_REJECTED_BY_CAPACITY` | Approve fail vì capacity full |
| 3 | `PARTICIPANT_LEFT` | User leave chủ động |
| 4 | `OWNER_LEFT` | Owner leave (sau 3s debounce) |
| 5 | `OWNER_REJOINED` | Owner rejoin |
| 6 | `ROOM_MANUAL_ENDED` | Owner end |
| 7 | `ROOM_AUTO_ENDED` | Auto-end (grace/empty) |
| 8 | `ROOM_REVIVED` | Undo end trong 5s |
| 9 | `ROOM_REOPENED` | Reopen phòng |

**Acceptance Criteria**:
- [ ] 9 events broadcast đúng trigger
- [ ] Payload đúng schema

---

## Task 2.13-2.16: FE Screens

### Task 2.13: SC-02 Owner Waiting Room

**Files**:
- `frontend/src/features/liveroom/components/OwnerWaitingRoom.tsx`

**Components**:
- List pending JoinRequest
- Mỗi row: avatar, email, "Duyệt" / "Từ chối" button
- Counter "X/Y participants"

**Acceptance Criteria**:
- [ ] WS event `JOIN_REQUEST_CREATED` → thêm row
- [ ] Click "Duyệt" → approve
- [ ] Click "Từ chối" → reject modal

---

### Task 2.14: SC-03b Rejected/Expired UI

**Files**:
- `frontend/src/features/liveroom/components/RejectedNotice.tsx`

**Components**:
- Hiển thị lý do (OWNER_REJECT / CAPACITY_FULL / ROOM_ENDED)
- Nút "Về trang chủ"

**Acceptance Criteria**:
- [ ] WS event `REQUEST_REJECTED_BY_OWNER` → hiển thị UI
- [ ] Lý do đúng

---

### Task 2.15: Owner Leave Banner + Grace Timer

**Files**:
- `frontend/src/features/liveroom/components/OwnerLeftBanner.tsx`

**Components**:
- Banner "Owner đã rời phòng — chờ owner quay lại (60s)"
- Countdown timer
- Music control disabled

**Acceptance Criteria**:
- [ ] WS event `OWNER_LEFT` → hiển thị banner
- [ ] Countdown chính xác
- [ ] hết 60s → auto-end → ROOM_AUTO_ENDED

---

### Task 2.16: End Room + Undo Toast (5s)

**Files**:
- `frontend/src/features/liveroom/components/EndRoomUndoToast.tsx`

**Components**:
- Owner click "Kết thúc phòng"
- Toast "Đã kết thúc" + button "Hoàn tác" (5s)
- Sau 5s → toast biến mất

**Acceptance Criteria**:
- [ ] Click "Kết thúc" → gọi POST /end
- [ ] Toast 5s có button "Hoàn tác"
- [ ] Click "Hoàn tác" → gọi POST /end/undo

---

## Task 2.17: i18n Keys

**Mục tiêu**: Add ~25 keys mới.

**Keys ví dụ**:
- `liveroom.reject.success`
- `liveroom.leave.owner.broadcast`
- `liveroom.leave.owner.grace_seconds`
- `liveroom.end.confirm`
- `liveroom.end.undo`
- `liveroom.reopen.success`
- `liveroom.rejected.notice.owner_reject`
- ... (~25 keys)

**Files cần sửa**:
- `Backend/shared-web/src/main/resources/messages/messages*.properties` (3 files)
- `frontend/messages/en.json`, `vi.json`

**Acceptance Criteria**:
- [ ] Tất cả keys có EN + VI

---

## Task 2.18: E2E Test (15+ edge cases)

**Mục tiêu**: Test 15+ edge cases từ `liveroom-user-flow.md` §4.

**Test scenarios**:
1. EC-03: User join vào phòng full → REJECTED (ROOM_FULL)
2. EC-04: User A gửi 3 requests liên tiếp → 1 PENDING, 2 idempotent
3. EC-05: Owner reject user A 3 lần → A bị LOCKED
4. EC-06: User A bị reject 2 lần → request mới OK
5. EC-07: Owner leave → 3s debounce → rejoin trong 3s → huỷ leave
6. EC-08: Owner leave → 3s → broadcast OWNER_LEFT → 60s grace → auto-end
7. EC-09: Owner leave → rejoin → grace huỷ
8. EC-10: Participant leave → broadcast PARTICIPANT_LEFT
9. EC-11: Last participant leave → auto-end với EMPTY_TIMEOUT
10. EC-12: Owner end → undo trong 5s → REVIVED
11. EC-13: Owner end → undo sau 5s → fail
12. EC-14: Owner reopen ENDED phòng
13. EC-15: Owner reopen → cycle mới → counters reset
14. EC-23: 2 user đồng thời request khi còn 1 slot → 1 success, 1 fail
15. EC-24: Multi-tab conflict (2 tab cùng user)

**Acceptance Criteria**:
- [ ] 15+ edge cases pass manual test

---

## 🚦 Definition of Done Phase 2

- [ ] Tất cả 18 tasks DONE
- [ ] 6 BE endpoints + 9 WS events + 3 jobs hoạt động
- [ ] 4 FE screens render đúng
- [ ] i18n ~25 keys
- [ ] 15+ edge cases pass
- [ ] Pessimistic lock protect race condition (EC-23)
- [ ] Grace expiry hoạt động
- [ ] Undo end trong 5s hoạt động
- [ ] Multi-tab conflict detection

---

**Cập nhật**: 2026-07-26
