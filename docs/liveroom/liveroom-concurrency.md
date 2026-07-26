# Live Room — Concurrency & Race Conditions

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-data-model.md` v1.0
**Đối tượng đọc**: Backend Dev, QA
**Mục đích**: Quy chuẩn chiến lược locking, race condition protection cho module Live Room. Đây là spec cho implementation + test concurrency.

---

## 1. Tổng quan Concurrency Strategy

| Layer | Strategy | Entity / Operation |
|---|---|---|
| **Database** | Pessimistic lock (`SELECT FOR UPDATE`) | **`LiveRoom`** — toàn bộ update (approve/rejoin/leave/kick/end/undo-end/reopen) |
| **Database** | Optimistic lock (`@Version`) | **`PlaybackState`** — music control play/pause/seek/volume (R-MUSIC-10 v1.8) |
| **Database** | Unique constraint + UPSERT | `JoinRequest` (idempotency), `RejectCounter` (monotonic increment) |
| **Database** | Partial unique index | `Participant` (state ∈ ACTIVE/RECONNECTING) — chống multi-tab |
| **Application** | Debounce + buffer | Owner leave/rejoin (3s, R-LEAVE-04.1 v1.8) |
| **Background Job** | Single instance leader election (ShedLock) | Cleanup job, grace expiry |

### 1.1. Tại sao LiveRoom dùng PESSIMISTIC, không phải @Version?

| Tiêu chí | PESSIMISTIC_WRITE | @Version (Optimistic) |
|---|---|---|
| Conflict rate | Thấp (capacity-sensitive hiếm khi conflict) | Cao nếu nhiều update đồng thời |
| Lock duration | Ngắn (1 transaction) — không giữ lock lâu | Không có lock, retry khi fail |
| Implementation | Đơn giản — `entityManager.find(..., PESSIMISTIC_WRITE)` | Phức tạp — cần retry + conflict handling |
| Predictability | Chắc chắn serialize | Có thể fail 409 nếu conflict cao |
| Phù hợp với | Update có thể predict trước & serialize | Update phân tán, latency thấp quan trọng |

**Quyết định**: `LiveRoom` update luôn đi kèm capacity check + atomic increment/decrement → **PESSIMISTIC** đơn giản hơn. `PlaybackState` chỉ là single row update với sequence number ordering → **OPTIMISTIC** phù hợp vì latency là critical (R-MUSIC-10 v1.8).

---

## 2. Pessimistic Locking (JPA `LockModeType.PESSIMISTIC_WRITE`)

### 2.1. Áp dụng cho

Các operation thay đổi `current_participant_count`:

| Endpoint | Lý do |
|---|---|
| `POST /liverooms/{id}/join-requests/{reqId}/approve` | Cần check capacity + atomic increment (R-CAPACITY-02 v1.7) |
| `POST /liverooms/{id}/rejoin` | Cần check capacity + atomic increment |
| `POST /liverooms/{id}/leave` | Cần atomic decrement + owner leave side effects |
| `POST /liverooms/{id}/participants/{userId}/kick` | Cần atomic decrement + set kickedCooldown |
| `POST /liverooms/{id}/end` | Cần lock để tránh race giữa manual end và grace job |
| `POST /liverooms/{id}/undo-end` | Cần lock để tránh race với auto-end job |
| `POST /liverooms/{id}/reopen` | Cần lock để tránh race với grace expiry job |

### 2.2. Implementation Pattern

```java
@Transactional
public void approveJoinRequest(UUID roomId, UUID requestId, UUID currentUserId) {
    LiveRoom room = entityManager.find(
        LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE
    );
    JoinRequest request = entityManager.find(
        JoinRequest.class, requestId, LockModeType.PESSIMISTIC_WRITE
    );
    validateOwner(room, currentUserId);
    validatePending(request);
    validateCapacity(room);
    
    request.setState(JoinRequestState.APPROVED);
    request.setResolvedAt(OffsetDateTime.now());
    
    Participant participant = Participant.builder()
        .id(UUID.randomUUID())
        .roomId(room.getId())
        .userId(request.getUserId())
        .roomSessionCycleId(room.getCurrentSessionCycleId())
        .state(ParticipantState.ACTIVE)
        .joinedAt(OffsetDateTime.now())
        .wasApproved(true)
        .build();
    participantRepository.save(participant);
    
    room.setCurrentParticipantCount(room.getCurrentParticipantCount() + 1);
    entityManager.flush();
}
```

### 2.3. Hibernate Lock Timeout

```yaml
# application.yml
spring:
  jpa:
    properties:
      jakarta.persistence.lock.timeout: 5000  # 5s
      hibernate:
        query.timeout: 10000
```

Nếu lock timeout → throw `LockTimeoutException` → 409 LIVEROOM_CONCURRENT_OPERATION.

### 2.4. PostgreSQL Configuration

```sql
-- Check current locks
SELECT * FROM pg_locks WHERE NOT granted;

-- Kill long-running lock
SELECT pg_cancel_backend(pid);
```

### 2.5. Risk: Deadlock

Nếu 2 transaction lock `liveroom_rooms` và `liveroom_join_requests` theo thứ tự khác nhau → deadlock.

**Solution**: LUÔN lock theo thứ tự:
1. `liveroom_rooms` (parent)
2. `liveroom_join_requests` (child)
3. `liveroom_participants` (child)

```java
// ALWAYS this order
entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);
entityManager.find(JoinRequest.class, requestId, LockModeType.PESSIMISTIC_WRITE);
```

### 2.6. Risk: Lock và long transaction

Pessimistic lock giữ transaction lâu → block concurrent operations.

**Solution**:
- Transaction chỉ chứa DB operations, KHÔNG chứa external call (HTTP, WS broadcast)
- WS broadcast thực hiện **SAU commit** qua `TransactionSynchronization.afterCommit`

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            wsBroadcaster.broadcastRoomCapacityChanged(roomId);
        }
    }
);
```

---

## 3. Optimistic Locking (JPA `@Version`)

### 3.1. Áp dụng cho

| Entity | Lý do |
|---|---|
| `PlaybackState` | R-MUSIC-10 v1.8 race condition cho music control — play/pause/seek/volume từ nhiều user cùng lúc, cần 409 conflict nhanh để client re-fetch |

**Lưu ý**: `LiveRoom` **KHÔNG** dùng `@Version` (xem `liveroom-data-model.md` §1.3). Tất cả update `LiveRoom` đều qua `PESSIMISTIC_WRITE` (§2.1).

### 3.2. Implementation

```java
@Entity
@Table(name = "liveroom_playback_states")
@Getter
@Setter
public class PlaybackState {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    private Integer version;
    
    private UUID roomId;
    private UUID songId;
    private PlaybackStatus status;
    private Double currentPositionSeconds;
    private Short volumePercent;
    private Long sequenceNumber;
    private OffsetDateTime lastUpdatedAt;
    // ...
}
```

### 3.3. Exception Handling

```java
@Transactional
public PlaybackState updatePlayback(UUID roomId, MusicControlRequest request, Long expectedSequence) {
    PlaybackState state = playbackStateRepository.findByRoomId(roomId)
        .orElseThrow(() -> new BusinessException(LiveroomErrorCode.PLAYBACK_STATE_NOT_FOUND));
    
    if (state.getSequenceNumber() != expectedSequence) {
        throw new BusinessException(LiveroomErrorCode.LIVEROOM_MUSIC_STATE_CONFLICT);
    }
    
    state.setStatus(request.getStatus());
    state.setCurrentPositionSeconds(request.getPosition());
    state.setSequenceNumber(state.getSequenceNumber() + 1);
    state.setLastUpdatedAt(OffsetDateTime.now());
    
    try {
        return playbackStateRepository.save(state);
    } catch (OptimisticLockingFailureException ex) {
        throw new BusinessException(LiveroomErrorCode.LIVEROOM_MUSIC_STATE_CONFLICT);
    }
}
```

### 3.4. WS Broadcast Strategy

Sau khi save thành công → broadcast qua WS với `sequenceNumber` mới. Client apply:
- `event.sequenceNumber > clientLastSequenceNumber` → apply + update last
- `event.sequenceNumber <= clientLastSequenceNumber` → discard (out-of-order)

---

## 4. UPSERT Pattern cho RejectCounter (R-REJECT-04 v1.8)

### 4.1. Problem

- 2 user gửi JoinRequest cùng lúc, cả 2 bị reject
- Cần atomic increment `reject_count_by_owner`

### 4.2. SQL Pattern (PostgreSQL)

```sql
INSERT INTO liveroom_reject_counters (
    id, user_id, room_id, room_session_cycle_id, 
    reject_count_by_owner, reject_count_by_capacity, 
    created_at, updated_at
)
VALUES (
    uuid_generate_v4(), ?, ?, ?, 1, 0, now(), now()
)
ON CONFLICT (user_id, room_id, room_session_cycle_id)
DO UPDATE SET 
    reject_count_by_owner = liveroom_reject_counters.reject_count_by_owner + 1,
    updated_at = now()
RETURNING reject_count_by_owner;
```

### 4.3. JPA Implementation

Dùng `@Query` annotation:

```java
@Modifying
@Query(value = """
    INSERT INTO liveroom_reject_counters (id, user_id, room_id, room_session_cycle_id, 
        reject_count_by_owner, reject_count_by_capacity, created_at, updated_at)
    VALUES (:id, :userId, :roomId, :cycleId, 1, 0, now(), now())
    ON CONFLICT (user_id, room_id, room_session_cycle_id)
    DO UPDATE SET 
        reject_count_by_owner = liveroom_reject_counters.reject_count_by_owner + 1,
        updated_at = now()
    """, nativeQuery = true)
void incrementRejectByOwner(
    @Param("id") UUID id,
    @Param("userId") UUID userId,
    @Param("roomId") UUID roomId,
    @Param("cycleId") UUID cycleId
);
```

### 4.4. Read After Increment

```java
@Query(value = """
    SELECT reject_count_by_owner FROM liveroom_reject_counters
    WHERE user_id = :userId AND room_id = :roomId AND room_session_cycle_id = :cycleId
    """, nativeQuery = true)
Short findRejectCountByOwner(
    @Param("userId") UUID userId,
    @Param("roomId") UUID roomId,
    @Param("cycleId") UUID cycleId
);
```

### 4.5. Edge Cases

| Case | Handling |
|---|---|
| Increment và reached 3 | Set JoinRequest.state = LOCKED |
| User retry sau khi count = 3 | 403 REQUEST_LOCKED |
| Room reopen | DELETE counter cho cycle cũ (R-REJECT-04.1) |

---

## 5. Idempotency cho JoinRequest (R-JOIN-07 v1.8)

### 5.1. Problem

- User click "Join" 2 lần (double submit)
- Network timeout → retry → duplicate request

### 5.2. Solution

- Client gửi `Idempotency-Key: <uuid>` header
- Server check key trong `liveroom_join_requests.idempotency_key`
- Nếu tồn tại và created_at > now - 24h → return row cũ (200 OK)
- Nếu không → INSERT row mới

### 5.3. Implementation

```java
@Transactional
public JoinRequest createJoinRequest(UUID roomId, String idempotencyKey, UUID currentUserId) {
    if (idempotencyKey == null) {
        throw new BusinessException(LiveroomErrorCode.LIVEROOM_IDEMPOTENCY_KEY_REQUIRED);
    }
    
    Optional<JoinRequest> existing = joinRequestRepository
        .findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey, OffsetDateTime.now().minusHours(24));
    if (existing.isPresent()) {
        return existing.get();
    }
    
    JoinRequest request = JoinRequest.builder()
        .id(UUID.randomUUID())
        .roomId(roomId)
        .userId(currentUserId)
        .roomSessionCycleId(currentCycleId)
        .state(JoinRequestState.PENDING)
        .idempotencyKey(idempotencyKey)
        .createdAt(OffsetDateTime.now())
        .build();
    
    try {
        return joinRequestRepository.save(request);
    } catch (DataIntegrityViolationException ex) {
        // Race: another thread inserted same idempotencyKey
        return joinRequestRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new BusinessException(LiveroomErrorCode.LIVEROOM_INTERNAL_ERROR));
    }
}
```

### 5.4. Cleanup Job

Idempotency key chỉ giữ 24h (xem `liveroom-jobs.md` cho cleanup).

---

## 6. Owner Leave + Rejoin Debounce (R-LEAVE-04.1 v1.8)

### 6.1. Problem

- Owner click Leave → UI ngay lập tức navigate → click lại Join
- WS broadcast `OWNER_LEFT` giữa 2 action → user thấy flicker

### 6.2. Solution: 3s Debounce

```java
public void handleOwnerLeave(UUID roomId, UUID ownerId) {
    LiveRoom room = lockRoom(roomId);
    
    // Set state immediately
    room.setOwnerLeftAt(OffsetDateTime.now());
    room.setReservedOwnerSlot(true);
    entityManager.flush();
    
    // Broadcast WS sau 3s
    ScheduledExecutorService.schedule(() -> {
        if (stillAbsent(roomId)) {
            wsBroadcaster.broadcastOwnerLeft(roomId);
            scheduleGraceExpiryJob(roomId);
        }
    }, 3, TimeUnit.SECONDS);
}

public void handleOwnerRejoin(UUID roomId, UUID ownerId) {
    ScheduledExecutorService.cancel(pendingBroadcast);  // Cancel pending broadcast
    
    LiveRoom room = lockRoom(roomId);
    room.setOwnerLeftAt(null);
    room.setReservedOwnerSlot(false);
    entityManager.flush();
    
    // Broadcast ngay
    wsBroadcaster.broadcastOwnerRejoined(roomId);
}
```

### 6.3. State Management

Dùng `Caffeine cache` để track pending broadcasts:

```java
Cache<UUID, ScheduledFuture<?>> pendingBroadcasts = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.SECONDS)
    .build();
```

---

## 7. Race Condition Scenarios & Solutions

### 7.1. Race #1: 2 users cùng join khi còn 1 slot

**Scenario**:
- T=0: current_count = 6, max = 7
- T=1: User A submit join-request → APPROVE
- T=2: User B submit join-request → APPROVE

**Solution**:
- Cả 2 cùng lock `liveroom_rooms` (pessimistic)
- 1 transaction wait, 1 transaction succeed
- Waiter check capacity sau → 409 ROOM_FULL → state = REJECTED_BY_CAPACITY
- Counter increment cho user rejected

### 7.2. Race #2: Music Play 2 lần trong 100ms

**Scenario**:
- T=0: state.version = 5, sequence = 100
- T=10: User A click Play → `UPDATE SET version = 6`
- T=15: User B click Play → `UPDATE WHERE version = 5` → 0 rows affected

**Solution**:
- Optimistic lock `@Version`
- User B nhận `OptimisticLockingFailureException` → 409 MUSIC_STATE_CONFLICT
- Client auto re-fetch state → apply mới nhất

### 7.3. Race #3: Owner leave + Grace expire đồng thời

**Scenario**:
- T=0: owner_left_at = now
- T=60s: grace expiry job chạy
- T=60s+1ms: owner click rejoin

**Solution**:
- Cả 2 đều lock `liveroom_rooms` (pessimistic)
- Job thắng → ENDED
- Rejoin nhận 409 ROOM_ENDED
- Owner phải reopen

### 7.4. Race #4: WS reconnect giữa session timeout

**Scenario**:
- T=0: participant.state = ACTIVE
- T=10: WS disconnect → state = RECONNECTING
- T=70: cleanup job set state = ENDED
- T=71: WS reconnect từ tab cũ

**Solution**:
- Cleanup job lock row
- WS reconnect check `state == ACTIVE | RECONNECTING`
- Nếu state = ENDED → reject WS, navigate về SC-13

### 7.5. Race #5: Chat send 2 message cùng lúc

**Scenario**:
- 2 user gửi "hello" cùng lúc

**Solution**:
- 2 INSERT riêng biệt (không conflict vì UUID primary key)
- Order theo `sent_at` timestamp
- WS broadcast 2 event riêng

### 7.6. Race #6: Annotation create đồng thời 2 user

**Scenario**:
- 2 user cùng tạo annotation tại position 120.5

**Solution**:
- 2 INSERT riêng biệt
- Frontend sort theo `created_at` rồi `position_seconds`

### 7.7. Race #7: Owner end + Lobby user approve

**Scenario**:
- T=0: owner click End
- T=1: lobby user click Approve (vì owner rời web, có user khác request)

**Solution**:
- Cả 2 lock `liveroom_rooms`
- Owner end thắng → status = ENDED
- Approve check status ACTIVE → fail → 409 ROOM_ENDED

### 7.8. Race #8: Multi-tab join (R-JOIN-10 v1.8)

**Scenario**:
- Tab 1: user click Join → ACTIVE
- Tab 2: user click Join (cùng lúc)

**Solution**:
- Partial unique index: `UNIQUE(room_id, user_id) WHERE state IN ('ACTIVE', 'RECONNECTING')`
- Tab 2 INSERT fail → catch `DataIntegrityViolationException` → 409 MULTI_TAB_CONFLICT

---

## 8. Distributed Lock (nếu multi-instance)

### 8.1. Single Job Execution

Nếu chạy nhiều instance (cluster), cleanup job có thể chạy 2 lần → idempotent fail.

**Solution**: Leader election qua Redis hoặc Quartz cluster.

### 8.2. ShedLock

```java
@Component
public class GraceExpiryJob {
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "GraceExpiryJob", lockAtMostFor = "9s", lockAtLeastFor = "1s")
    public void run() {
        // Only runs in 1 instance at a time
    }
}
```

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.10.0</version>
</dependency>
```

### 8.3. Configuration

```yaml
shedlock:
  default-lock-at-most-for: 30s
  default-lock-at-least-for: 1s
  redis:
    host: redis-host
    port: 6379
```

---

## 9. Transaction Propagation

### 9.1. Required vs RequiresNew

| Case | Propagation | Lý do |
|---|---|---|
| REST endpoint | `REQUIRED` | Default, 1 transaction cho cả request |
| Background job | `REQUIRED` | Mỗi job run = 1 transaction |
| Audit log write | `REQUIRES_NEW` | Đảm bảo audit ghi cả khi business logic fail |
| WS broadcast trigger | `REQUIRES_NEW` | Broadcast sau commit, không rollback |

### 9.2. Audit Log Example

```java
@Service
@RequiredArgsConstructor
public class RoomAdminActionService {
    private final RoomAdminActionRepository repository;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logKick(UUID roomId, UUID actorId, UUID targetId, String reason) {
        RoomAdminAction action = RoomAdminAction.builder()
            .id(UUID.randomUUID())
            .roomId(roomId)
            .actorUserId(actorId)
            .targetUserId(targetId)
            .actionType(ActionType.KICK)
            .reason(reason)
            .createdAt(OffsetDateTime.now())
            .build();
        repository.save(action);
    }
}
```

### 9.3. WS Broadcast Trigger

```java
@Service
@RequiredArgsConstructor
public class LiveRoomService {
    private final WsBroadcaster wsBroadcaster;
    
    @Transactional
    public void endRoom(UUID roomId, UUID currentUserId) {
        LiveRoom room = lockRoom(roomId);
        // ... business logic ...
        entityManager.flush();
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wsBroadcaster.broadcastRoomManualEnded(roomId);
                }
            }
        );
    }
}
```

---

## 10. Connection Pool

### 10.1. HikariCP Configuration

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      max-lifetime: 1800000
      idle-timeout: 600000
```

### 10.2. JPA Batch

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 30
        order_inserts: true
        order_updates: true
        batch_versioned_data: true
```

### 10.3. Statement Timeout

```yaml
spring:
  datasource:
    hikari:
      data-source-properties:
        socketTimeout: 60
```

---

## 11. Monitoring & Alerting

### 11.1. Metrics cần track

| Metric | Threshold | Action |
|---|---|---|
| `liveroom_lock_acquire_time_ms` | p99 > 500ms | Alert |
| `liveroom_optimistic_lock_failure_total` | > 10/giây | Alert (race condition nặng) |
| `liveroom_transaction_rollback_total` | > 5% / 1 phút | Alert |
| `liveroom_pessimistic_lock_timeout_total` | > 5/giờ | Alert |
| `liveroom_capacity_rejected_total` | > 30% / 1 phút | Alert |
| `liveroom_music_state_conflict_total` | > 10/giây | Alert |

### 11.2. Prometheus Exporter

```java
@Component
public class ConcurrencyMetrics {
    private final Counter optimisticLockFailures = Counter.build()
        .name("liveroom_optimistic_lock_failure_total")
        .help("Optimistic lock failure count")
        .register();
    
    private final Histogram lockAcquireTime = Histogram.build()
        .name("liveroom_lock_acquire_time_ms")
        .help("Pessimistic lock acquire time")
        .register();
}
```

### 11.3. Logging

```java
@Slf4j
@Service
public class RoomLockingService {
    @Transactional
    public void lockRoom(UUID roomId, String operationName) {
        long start = System.nanoTime();
        try {
            LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            
            if (elapsed > 500) {
                log.warn("Slow lock acquire: roomId={}, operation={}, elapsedMs={}",
                    roomId, operationName, elapsed);
            }
        } catch (LockTimeoutException ex) {
            log.warn("Lock timeout: roomId={}, operation={}", roomId, operationName);
            throw new BusinessException(LiveroomErrorCode.LIVEROOM_CONCURRENT_OPERATION);
        }
    }
}
```

---

## 12. Testing Concurrency

### 12.1. Unit Tests (Concurrency)

Dùng `CountDownLatch` để force race condition:

```java
@Test
void twoApprovers_when_one_slot_race_only_one_succeeds() throws InterruptedException {
    UUID roomId = createRoomWithMaxParticipants(1);
    UUID user1 = createUserAndJoinRequest(roomId);
    UUID user2 = createUserAndJoinRequest(roomId);
    
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger fullRejectCount = new AtomicInteger(0);
    
    Thread t1 = new Thread(() -> {
        try {
            latch.countDown();
            latch.await();
            liveRoomService.approveJoinRequest(roomId, user1, ownerId);
            successCount.incrementAndGet();
        } catch (BusinessException ex) {
            if (ex.getCode() == LIVEROOM_ROOM_FULL) fullRejectCount.incrementAndGet();
        }
    });
    
    Thread t2 = new Thread(() -> {
        try {
            latch.countDown();
            latch.await();
            liveRoomService.approveJoinRequest(roomId, user2, ownerId);
            successCount.incrementAndGet();
        } catch (BusinessException ex) {
            if (ex.getCode() == LIVEROOM_ROOM_FULL) fullRejectCount.incrementAndGet();
        }
    });
    
    t1.start();
    t2.start();
    t1.join();
    t2.join();
    
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(fullRejectCount.get()).isEqualTo(1);
}
```

### 12.2. Stress Tests

Dùng JMeter hoặc Gatling:
- 100 concurrent join requests cùng lúc
- Verify chỉ N (capacity) được approve, còn lại ROOM_FULL
- Verify `current_participant_count` final = capacity

### 12.3. Chaos Tests

Test các failure scenario:
- DB connection drop giữa transaction
- WS disconnect giữa transaction
- Service crash trong background job

---

## 13. Out of Scope (POST-MVP)

- ❌ Distributed transaction (Saga pattern) — chưa cần vì scope 1 service
- ❌ Event sourcing cho audit
- ❌ Two-phase commit (XA)
- ❌ Leader election cho multi-region

---

## 14. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **Data model**: `liveroom-data-model.md` v1.0
- **API**: `liveroom-api-spec.md` v1.0
- **State machines**: `liveroom-state-machines.md` v1.0
- **Jobs**: `liveroom-jobs.md` (sẽ tạo)
- **Logging**: `liveroom-audit-logging.md` (sẽ tạo)

---

**Người viết**: Senior Dev Team
**Reviewer**: Backend Lead, DevOps
**Ngày review**: Pending