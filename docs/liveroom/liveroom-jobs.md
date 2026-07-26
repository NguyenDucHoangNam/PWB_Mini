# Live Room — Background Jobs & Scheduler

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-data-model.md` v1.0
**Đối tượng đọc**: Backend Dev, DevOps, QA
**Mục đích**: Quy chuẩn background jobs, scheduler, cleanup logic cho module Live Room.

---

## 1. Tổng quan

Module Live Room có **7 background jobs** chính:

| # | Job | Trigger | Mục đích |
|---|---|---|---|
| 1 | GraceExpiryJob | Fixed delay 10s | Đóng phòng khi owner grace expire (R-END-08) |
| 2 | EmptyRoomTimeoutJob | Fixed delay 60s | Đóng phòng trống 5 phút (R-END-09) |
| 3 | ParticipantReconnectTimeoutJob | Fixed delay 30s | Set participant RECONNECTING → ENDED sau 60s |
| 4 | IdleGhostIndicatorJob | Fixed delay 60s | Đánh dấu participant idle > 5 phút (R-MEDIA-12) |
| 5 | IdempotencyCleanupJob | Cron daily 02:00 | Xoá idempotency key > 24h |
| 6 | ChatArchiveJob | Cron weekly | Archive chat cũ (POST-MVP) |
| 7 | AnnotationArchiveJob | Fixed delay 1h | Snapshot annotation stats theo cycle |

---

## 2. Configuration

### 2.1. Quartz (Recommended)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    properties:
      org:
        quartz:
          scheduler:
            instanceName: LiveroomScheduler
            instanceId: AUTO
          jobStore:
            driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
            dataSource: quartzDataSource
          threadPool:
            threadCount: 10
```

### 2.2. ShedLock (cho Spring `@Scheduled`)

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.10.0</version>
</dependency>
```

### 2.3. Application Config

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 5

liveroom:
  jobs:
    grace-expiry:
      enabled: true
      interval-ms: 10000
      batch-size: 100
    empty-room:
      enabled: true
      interval-ms: 60000
      batch-size: 100
      threshold-minutes: 5
    reconnect-timeout:
      enabled: true
      interval-ms: 30000
      batch-size: 100
      threshold-seconds: 60
    idle-ghost:
      enabled: true
      interval-ms: 60000
      batch-size: 100
      threshold-minutes: 5
    idempotency-cleanup:
      enabled: true
      cron: "0 0 2 * * ?"
    chat-archive:
      enabled: false  # POST-MVP
    annotation-archive:
      enabled: true
      interval-ms: 3600000  # 1 hour
```

### 2.4. Multi-instance Safety

Tất cả jobs dùng `@SchedulerLock` để đảm bảo chỉ 1 instance chạy:

```java
@Scheduled(fixedDelayString = "${liveroom.jobs.grace-expiry.interval-ms}")
@SchedulerLock(
    name = "GraceExpiryJob",
    lockAtMostFor = "30s",
    lockAtLeastFor = "5s"
)
public void run() { ... }
```

---

## 3. Job #1: GraceExpiryJob

### 3.1. Mục đích

Đóng phòng khi owner leave mà không quay lại trong `ownerGraceSeconds` (R-LEAVE-07, R-END-08).

### 3.2. Trigger

- **Fixed delay**: 10s (configurable)
- **Single execution**: dùng Quartz trigger 1 lần khi owner leave (POST-MVP) HOẶC polling mỗi 10s (MVP)

### 3.3. Logic

```java
@Component
@RequiredArgsConstructor
public class GraceExpiryJob {
    
    private final LiveRoomRepository roomRepository;
    private final WsBroadcaster wsBroadcaster;
    private final LiveroomErrorCodeMapper errorCodeMapper;
    
    @Scheduled(fixedDelayString = "${liveroom.jobs.grace-expiry.interval-ms}")
    @SchedulerLock(name = "GraceExpiryJob", lockAtMostFor = "30s", lockAtLeastFor = "5s")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        
        List<LiveRoom> expiredRooms = roomRepository.findByOwnerLeftAtBeforeAndStatus(
            now, RoomStatus.ACTIVE, PageRequest.of(0, 100)
        );
        
        for (LiveRoom room : expiredRooms) {
            try {
                endRoomDueToGraceExpiry(room.getId());
            } catch (BusinessException ex) {
                log.warn("Failed to end room due to grace expiry: roomId={}, error={}",
                    room.getId(), ex.getCode());
            }
        }
    }
    
    @Transactional
    public void endRoomDueToGraceExpiry(UUID roomId) {
        LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        
        if (room.getStatus() != RoomStatus.ACTIVE) return;  // Already ended
        if (room.getOwnerLeftAt() == null) return;  // Owner rejoined
        
        OffsetDateTime graceExpiry = room.getOwnerLeftAt()
            .plusSeconds(room.getOwnerGraceSeconds());
        if (OffsetDateTime.now().isBefore(graceExpiry)) return;  // Still in grace
        
        room.setStatus(RoomStatus.ENDED);
        room.setEndedAt(OffsetDateTime.now());
        room.setEndedReason(EndedReason.OWNER_GRACE_EXPIRED);
        
        endAllParticipants(roomId);
        expireAllPendingRequests(roomId);
        
        entityManager.flush();
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wsBroadcaster.broadcastRoomAutoEnded(roomId, EndedReason.OWNER_GRACE_EXPIRED);
                }
            }
        );
    }
}
```

### 3.4. Edge Cases

| Case | Handling |
|---|---|
| Owner rejoin trước grace | Rejoin service cancel pending job (set `owner_left_at = null`) |
| Phòng đã ENDED bởi manual | Skip (check status) |
| Job restart sau crash | Quartz hoặc ShedLock recover |
| DB connection fail | Retry với exponential backoff (3 lần) |

### 3.5. Quartz (Future Optimization)

POST-MVP: tạo trigger 1 lần thay vì polling:

```java
public void scheduleGraceExpiry(UUID roomId, OffsetDateTime expiresAt) {
    Trigger trigger = TriggerBuilder.newTrigger()
        .forJob("graceExpiryJob")
        .withIdentity("grace-" + roomId)
        .startAt(expiresAt)
        .build();
    scheduler.scheduleJob(trigger);
}

public void cancelGraceExpiry(UUID roomId) {
    scheduler.unscheduleJob(new TriggerKey("grace-" + roomId));
}
```

---

## 4. Job #2: EmptyRoomTimeoutJob

### 4.1. Mục đích

Đóng phòng nếu trống (không có participant ACTIVE) quá 5 phút (R-END-09).

### 4.2. Trigger

- **Fixed delay**: 60s
- **Threshold**: 5 phút (configurable)

### 4.3. Logic

```java
@Component
@RequiredArgsConstructor
public class EmptyRoomTimeoutJob {
    
    private final LiveRoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    
    @Scheduled(fixedDelayString = "${liveroom.jobs.empty-room.interval-ms}")
    @SchedulerLock(name = "EmptyRoomTimeoutJob", lockAtMostFor = "30s", lockAtLeastFor = "5s")
    public void run() {
        OffsetDateTime threshold = OffsetDateTime.now()
            .minusMinutes(emptyRoomConfig.getThresholdMinutes());
        
        List<LiveRoom> activeRooms = roomRepository.findByStatusAndStartedAtBefore(
            RoomStatus.ACTIVE, threshold, PageRequest.of(0, 100)
        );
        
        for (LiveRoom room : activeRooms) {
            try {
                if (isRoomEmpty(room.getId())) {
                    endRoomDueToEmptyTimeout(room.getId());
                }
            } catch (BusinessException ex) {
                log.warn("Failed to end room due to empty timeout: roomId={}",
                    room.getId(), ex);
            }
        }
    }
    
    private boolean isRoomEmpty(UUID roomId) {
        return participantRepository.countByRoomIdAndState(
            roomId, ParticipantState.ACTIVE
        ) == 0 && participantRepository.countByRoomIdAndState(
            roomId, ParticipantState.RECONNECTING
        ) == 0;
    }
    
    @Transactional
    public void endRoomDueToEmptyTimeout(UUID roomId) {
        LiveRoom room = entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        
        if (room.getStatus() != RoomStatus.ACTIVE) return;
        if (!isRoomEmpty(roomId)) return;
        
        room.setStatus(RoomStatus.ENDED);
        room.setEndedAt(OffsetDateTime.now());
        room.setEndedReason(EndedReason.EMPTY_TIMEOUT);
        
        endAllParticipants(roomId);
        
        entityManager.flush();
        // ... WS broadcast ...
    }
}
```

### 4.4. Edge Cases

| Case | Handling |
|---|---|
| Owner rejoin lúc job chạy | Pessimistic lock → rejoin wait |
| Lobby user (PENDING) trong phòng trống | Vẫn đóng — chỉ count ACTIVE |
| Owner absent nhưng vẫn ACTIVE user khác | Owner leave grace job vẫn chạy |

---

## 5. Job #3: ParticipantReconnectTimeoutJob

### 5.1. Mục đích

Set participant RECONNECTING → ENDED nếu không reconnect trong 60s.

### 5.2. Trigger

- **Fixed delay**: 30s
- **Threshold**: 60s (R-JOIN-09)

### 5.3. Logic

```java
@Component
@RequiredArgsConstructor
public class ParticipantReconnectTimeoutJob {
    
    private final ParticipantRepository participantRepository;
    
    @Scheduled(fixedDelayString = "${liveroom.jobs.reconnect-timeout.interval-ms}")
    @SchedulerLock(name = "ParticipantReconnectTimeoutJob", lockAtMostFor = "30s", lockAtLeastFor = "5s")
    public void run() {
        OffsetDateTime threshold = OffsetDateTime.now()
            .minusSeconds(reconnectConfig.getThresholdSeconds());
        
        List<Participant> expired = participantRepository
            .findByStateAndUpdatedAtBefore(ParticipantState.RECONNECTING, threshold,
                PageRequest.of(0, 100));
        
        for (Participant p : expired) {
            try {
                endReconnectingParticipant(p.getRoomId(), p.getUserId());
            } catch (BusinessException ex) {
                log.warn("Failed to end reconnecting participant: userId={}, roomId={}",
                    p.getUserId(), p.getRoomId(), ex);
            }
        }
    }
    
    @Transactional
    public void endReconnectingParticipant(UUID roomId, UUID userId) {
        Participant p = entityManager.find(Participant.class,
            participantRepository.findByRoomIdAndUserId(roomId, userId).getId(),
            LockModeType.PESSIMISTIC_WRITE);
        
        if (p.getState() != ParticipantState.RECONNECTING) return;
        
        p.setState(ParticipantState.ENDED);
        p.setLeftAt(OffsetDateTime.now());
        
        decrementRoomCount(roomId);
        
        entityManager.flush();
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wsBroadcaster.broadcastParticipantConnectionChanged(roomId, userId, ConnectionStatus.OFFLINE);
                }
            }
        );
    }
}
```

---

## 6. Job #4: IdleGhostIndicatorJob (R-MEDIA-12 v1.8)

### 6.1. Mục đích

Đánh dấu participant idle (không tương tác > 5 phút) là "ghost" trên UI — UI hiển thị avatar mờ.

### 6.2. Trigger

- **Fixed delay**: 60s
- **Threshold**: 5 phút (no interaction)

### 6.3. Interaction events (cập nhật `last_interaction_at`)

| Event | Update |
|---|---|
| User send chat | YES |
| User toggle mic/camera | YES |
| User play/pause music | YES |
| User create annotation | YES |
| User không làm gì | NO |

### 6.4. Logic

```java
@Component
@RequiredArgsConstructor
public class IdleGhostIndicatorJob {
    
    @Scheduled(fixedDelayString = "${liveroom.jobs.idle-ghost.interval-ms}")
    @SchedulerLock(name = "IdleGhostIndicatorJob", lockAtMostFor = "30s", lockAtLeastFor = "5s")
    public void run() {
        OffsetDateTime threshold = OffsetDateTime.now()
            .minusMinutes(idleGhostConfig.getThresholdMinutes());
        
        List<Participant> idleParticipants = participantRepository
            .findByStateAndLastInteractionAtBefore(ParticipantState.ACTIVE, threshold,
                PageRequest.of(0, 100));
        
        for (Participant p : idleParticipants) {
            wsBroadcaster.broadcastParticipantIdle(p.getRoomId(), p.getUserId());
        }
    }
}
```

### 6.5. WS Event

```json
{
  "eventType": "PARTICIPANT_IDLE",
  "data": {
    "roomId": "uuid",
    "userId": "uuid",
    "idleSinceSeconds": 300
  }
}
```

Frontend nhận → render avatar với opacity 0.5 + "ghost" label.

---

## 7. Job #5: IdempotencyCleanupJob

### 7.1. Mục đích

Xoá `idempotency_key` cũ (> 24h) khỏi `liveroom_join_requests` (R-JOIN-07 v1.8 TTL).

### 7.2. Trigger

- **Cron**: `0 0 2 * * ?` (daily 2 AM)

### 7.3. Logic

```java
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {
    
    private final JoinRequestRepository joinRequestRepository;
    
    @Scheduled(cron = "${liveroom.jobs.idempotency-cleanup.cron}")
    @SchedulerLock(name = "IdempotencyCleanupJob", lockAtMostFor = "1h", lockAtLeastFor = "5m")
    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);
        
        int batchSize = 500;
        int totalDeleted = 0;
        
        while (true) {
            int deleted = joinRequestRepository.deleteOldIdempotencyKeys(cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) break;
        }
        
        log.info("Idempotency cleanup: deleted {} keys older than {}", totalDeleted, cutoff);
    }
}
```

### 7.4. SQL

```sql
DELETE FROM liveroom_join_requests
WHERE idempotency_key IS NOT NULL
  AND created_at < :cutoff
LIMIT :batchSize;
```

**Lưu ý**: Chỉ xoá `idempotency_key` field, KHÔNG xoá row (giữ audit trail).

```sql
UPDATE liveroom_join_requests
SET idempotency_key = NULL
WHERE idempotency_key IS NOT NULL
  AND created_at < :cutoff;
```

---

## 8. Job #6: AnnotationArchiveJob

### 8.1. Mục đích

Snapshot stats cho annotation theo cycle (số lượng, user contribute) cho analytics.

### 8.2. Trigger

- **Fixed delay**: 1h

### 8.3. Logic

```java
@Component
@RequiredArgsConstructor
public class AnnotationArchiveJob {
    
    private final RoomSessionCycleRepository cycleRepository;
    private final AnnotationRepository annotationRepository;
    private final AnalyticsService analyticsService;
    
    @Scheduled(fixedDelayString = "${liveroom.jobs.annotation-archive.interval-ms}")
    @SchedulerLock(name = "AnnotationArchiveJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(1);
        
        List<RoomSessionCycle> recentCycles = cycleRepository
            .findByStartedAtAfterAndEndedAtNotNull(cutoff);
        
        for (RoomSessionCycle cycle : recentCycles) {
            Long count = annotationRepository.countByRoomSessionCycleId(cycle.getId());
            Map<UUID, Long> userContributions = annotationRepository
                .countByRoomSessionCycleIdGroupByUserId(cycle.getId());
            
            analyticsService.recordAnnotationStats(cycle.getRoomId(), cycle.getId(),
                count, userContributions);
        }
    }
}
```

---

## 9. Job #7: ChatArchiveJob (POST-MVP)

### 9.1. Mục đích

Archive chat messages cũ (> 90 ngày) sang cold storage.

### 9.2. Status

- **Disabled by default** (POST-MVP)
- Config: `liveroom.jobs.chat-archive.enabled=false`

### 9.3. Logic (Future)

```java
// TODO: implement POST-MVP
public void run() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
    List<ChatMessage> oldMessages = chatRepository.findBySentAtBefore(cutoff);
    coldStorageService.archive(oldMessages);
    // KHÔNG xoá khỏi primary DB (audit)
}
```

---

## 10. Job Implementation Pattern

### 10.1. Generic Structure

```java
@Component
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractLiveroomJob<T> {
    
    @Value("${liveroom.jobs.batch-size:100}")
    protected int batchSize;
    
    protected abstract List<T> findBatch(int offset, int limit);
    protected abstract void process(T item) throws BusinessException;
    
    public void execute() {
        int offset = 0;
        while (true) {
            List<T> batch = findBatch(offset, batchSize);
            if (batch.isEmpty()) break;
            
            for (T item : batch) {
                try {
                    process(item);
                } catch (BusinessException ex) {
                    log.warn("Job processing failed: item={}, error={}", item, ex.getCode());
                } catch (Exception ex) {
                    log.error("Unexpected error processing job: item={}", item, ex);
                }
            }
            
            offset += batchSize;
        }
    }
}
```

### 10.2. Error Handling

| Error Type | Action |
|---|---|
| BusinessException (validation) | Log warn + skip item |
| DataAccessException (DB) | Retry 3 lần + log error |
| Optimistic lock fail | Skip item (race condition bình thường) |
| Lock timeout | Skip + retry next batch |
| Unexpected | Log error + alert |

### 10.3. Logging

Mỗi job run log:
```java
log.info("Job started: name={}, startedAt={}", getJobName(), OffsetDateTime.now());

long start = System.nanoTime();
execute();
long elapsed = (System.nanoTime() - start) / 1_000_000;

log.info("Job completed: name={}, elapsedMs={}, processed={}",
    getJobName(), elapsed, processedCount);
```

---

## 11. Monitoring

### 11.1. Metrics

| Metric | Type | Threshold |
|---|---|---|
| `liveroom_job_duration_seconds` | Histogram | p99 > 30s → alert |
| `liveroom_job_processed_total` | Counter | — |
| `liveroom_job_failed_total` | Counter | > 5% → alert |
| `liveroom_job_skipped_total` | Counter | — |
| `liveroom_grace_expiry_rooms_total` | Counter | > 10/giờ → alert |
| `liveroom_idle_ghost_count` | Gauge | > 50% ACTIVE → alert (nhiều idle) |

### 11.2. Alerting Rules

```yaml
# Prometheus alert
- alert: LiveroomJobTooSlow
  expr: histogram_quantile(0.99, liveroom_job_duration_seconds) > 30
  for: 5m
  annotations:
    summary: "Job {{ $labels.name }} running too slow"

- alert: LiveroomJobHighFailure
  expr: rate(liveroom_job_failed_total[5m]) > 0.05
  for: 5m
  annotations:
    summary: "Job failure rate > 5%"

- alert: LiveroomGraceExpirySpike
  expr: increase(liveroom_grace_expiry_rooms_total[1h]) > 10
  annotations:
    summary: "Too many rooms ended due to grace expiry"
```

---

## 12. Testing Strategy

### 12.1. Unit Test

```java
@SpringBootTest
class GraceExpiryJobTest {
    
    @Autowired GraceExpiryJob job;
    @Autowired LiveRoomService roomService;
    @Autowired ParticipantService participantService;
    
    @Test
    void endRoom_when_owner_grace_expired() {
        UUID roomId = createRoomWithGraceSeconds(1);
        UUID ownerId = createOwner();
        roomService.leaveRoom(roomId, ownerId);  // Owner leave
        
        Thread.sleep(2000);  // Wait 2s
        
        job.run();
        
        LiveRoom room = roomRepository.findById(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ENDED);
        assertThat(room.getEndedReason()).isEqualTo(EndedReason.OWNER_GRACE_EXPIRED);
    }
    
    @Test
    void noEnd_when_owner_rejoin_before_grace() {
        UUID roomId = createRoomWithGraceSeconds(60);
        UUID ownerId = createOwner();
        roomService.leaveRoom(roomId, ownerId);
        
        // Owner rejoin trong 5s
        Thread.sleep(1000);
        roomService.rejoinRoom(roomId, ownerId);
        
        job.run();
        
        LiveRoom room = roomRepository.findById(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
    }
}
```

### 12.2. Integration Test

```java
@SpringBootTest
@TestPropertySource(properties = {
    "liveroom.jobs.grace-expiry.interval-ms=1000"  // 1s cho test
})
class JobIntegrationTest {
    
    @Test
    void full_lifecycle_with_grace_expiry() {
        UUID roomId = createRoom();
        UUID ownerId = createOwner();
        UUID participantId = createParticipant(roomId);
        
        // Owner leave → grace → expiry
        roomService.leaveRoom(roomId, ownerId);
        
        await().atMost(5, SECONDS).untilAsserted(() -> {
            LiveRoom room = roomRepository.findById(roomId).orElseThrow();
            assertThat(room.getStatus()).isEqualTo(RoomStatus.ENDED);
            assertThat(room.getEndedReason()).isEqualTo(EndedReason.OWNER_GRACE_EXPIRED);
        });
        
        // Verify participants ENDED
        List<Participant> participants = participantRepository.findByRoomId(roomId);
        assertThat(participants).allMatch(p -> p.getState() == ParticipantState.ENDED);
        
        // Verify WS broadcast
        verify(wsBroadcaster, atLeastOnce()).broadcastRoomAutoEnded(eq(roomId), eq(EndedReason.OWNER_GRACE_EXPIRED));
    }
}
```

### 12.3. Stress Test

- 100 rooms với owner leave + grace expire
- Verify chỉ chạy 1 job (ShedLock)
- Verify tất cả rooms đúng status ENDED

---

## 13. Out of Scope (POST-MVP)

- ❌ Quartz persistent jobs (đang dùng ShedLock + Redis)
- ❌ Distributed leader election qua ZooKeeper
- ❌ Job priority queue
- ❌ Job retry với exponential backoff
- ❌ Chat archive to cold storage
- ❌ Annotation sentiment analysis

---

## 14. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **Data model**: `liveroom-data-model.md` v1.0
- **State machines**: `liveroom-state-machines.md` v1.0
- **Concurrency**: `liveroom-concurrency.md` v1.0
- **Logging**: `liveroom-audit-logging.md` (sẽ tạo)

---

**Người viết**: Senior Dev Team
**Reviewer**: Backend Lead, DevOps, QA Lead
**Ngày review**: Pending