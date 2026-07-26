# Live Room — Audit Logging & Observability

**Phiên bản**: 1.0 (2026-07-26)
**Căn cứ**: `liveroom-business-requirements.md` v1.8, `liveroom-data-model.md` v1.0
**Đối tượng đọc**: Backend Dev, DevOps, Security, QA
**Mục đích**: Quy chuẩn log format, audit trail, observability cho module Live Room.

---

## 1. Tổng quan

Module Live Room có **3 lớp logging**:

| Lớp | Mục đích | Lưu trữ | Retention |
|---|---|---|---|
| **Application log** | Debug, monitoring | ELK/Loki | 30 ngày |
| **Audit log** | Compliance, security | PostgreSQL audit tables | 90 ngày (POST-MVP configurable) |
| **Business event** | Analytics, KPI | Data warehouse (S3/ClickHouse) | 1 năm |

### 1.1. Nguyên tắc chung

- **KHÔNG log**: password, token, PII (email full, phone, address)
- **CÓ log**: userId, roomId, action type, timestamp, kết quả
- **Structured log**: dùng key=value hoặc JSON (KHÔNG string concat)
- **Log level**: ERROR (lỗi cần xử lý), WARN (cảnh báo), INFO (business event), DEBUG (chi tiết dev)

---

## 2. Application Logging

### 2.1. Log Format (JSON)

```json
{
  "timestamp": "2026-07-26T10:00:00.000Z",
  "level": "INFO",
  "logger": "com.pwb.liveroom.core.service.LiveRoomService",
  "thread": "http-nio-8080-exec-1",
  "traceId": "abc123",
  "userId": "uuid",
  "roomId": "uuid",
  "event": "ROOM_CREATED",
  "message": "Room created successfully",
  "context": {
    "roomCode": "ABC123",
    "maxParticipants": 7,
    "durationMs": 150
  }
}
```

### 2.2. Logger Configuration (logback-spring.xml)

```xml
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>roomId</includeMdcKeyName>
            <customFields>{"app":"liveroom","env":"${ENV:-dev}"}</customFields>
        </encoder>
    </appender>
    
    <logger name="com.pwb.liveroom" level="INFO"/>
    <logger name="com.pwb.liveroom.core.service.LiveRoomService" level="DEBUG"/>
    
    <root level="WARN">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

### 2.3. MDC (Mapped Diagnostic Context)

Spring Filter tự động attach vào MDC:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest req = (HttpServletRequest) request;
        
        String traceId = req.getHeader("X-Request-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

### 2.4. Authentication MDC

```java
@Component
public class AuthenticationMdcFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) {
        try {
            String token = extractToken(req);
            if (token != null && jwtValidator.isValid(token)) {
                UUID userId = jwtValidator.getUserId(token);
                String role = jwtValidator.getRole(token);
                MDC.put("userId", userId.toString());
                MDC.put("userRole", role);
            }
            chain.doFilter(req, resp);
        } finally {
            MDC.remove("userId");
            MDC.remove("userRole");
        }
    }
}
```

### 2.5. Controller Logging Pattern

```java
@Slf4j
@RestController
@RequiredArgsConstructor
public class LiveRoomController {
    
    private static final String MSG_ROOM_CREATED = "LIVEROOM_ROOM_CREATED";
    
    private final LiveRoomService liveRoomService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(
            @Valid @RequestBody RoomCreateRequest request) {
        
        log.info("Creating room: roomName={}, maxParticipants={}", 
            request.getRoomName(), request.getMaxParticipants());
        
        RoomResponse data = liveRoomService.createRoom(request);
        
        log.info("Room created: roomId={}, roomCode={}, ownerId={}",
            data.getId(), data.getRoomCode(), data.getOwnerId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(message(MSG_ROOM_CREATED), data));
    }
}
```

---

## 3. Log Levels theo Event

### 3.1. ERROR — Lỗi cần xử lý ngay

| Event | Khi nào |
|---|---|
| `DATABASE_ERROR` | DB connection fail, query timeout |
| `EXTERNAL_SERVICE_ERROR` | Voice module fail, IAM fail |
| `UNEXPECTED_EXCEPTION` | RuntimeException không catch được |
| `WS_BROADCAST_FAIL` | WebSocket gửi fail (retry cần alert) |
| `TRANSACTION_ROLLBACK` | @Transactional fail |

```java
log.error("Database query failed: roomId={}, query={}", roomId, sql, exception);
```

### 3.2. WARN — Cảnh báo, không phải lỗi nghiêm trọng

| Event | Khi nào |
|---|---|
| `SLOW_LOCK_ACQUIRE` | Pessimistic lock > 500ms |
| `LOCK_TIMEOUT` | Pessimistic lock timeout |
| `OPTIMISTIC_LOCK_CONFLICT` | Version conflict (R-MUSIC-10 v1.8) |
| `CAPACITY_REJECTED` | User rejected do capacity full |
| `RATE_LIMIT_TRIGGERED` | Brute force / kicked cooldown |
| `BRUTE_FORCE_LIMIT` | 5 attempts/IP/giờ (R-CREATE-05 v1.8) |
| `IDLE_GHOST_DETECTED` | Participant idle > 5min |
| `GRACE_EXPIRY_WARNING` | 10s trước grace expire |
| `WS_RECONNECT_ATTEMPT` | Client retry reconnect |

```java
log.warn("Lock acquire slow: roomId={}, operation={}, elapsedMs={}",
    roomId, operationName, elapsed);
```

### 3.3. INFO — Business events

| Event | Khi nào |
|---|---|
| `ROOM_CREATED` | POST /liverooms |
| `ROOM_ENDED` | POST /liverooms/{id}/end |
| `ROOM_REVIVED` | POST /liverooms/{id}/undo-end |
| `ROOM_REOPENED` | POST /liverooms/{id}/reopen |
| `REQUEST_CREATED` | POST /join-requests |
| `REQUEST_APPROVED` | POST /approve |
| `REQUEST_REJECTED_BY_OWNER` | POST /reject |
| `REQUEST_REJECTED_BY_CAPACITY` | POST /approve (full) |
| `REQUEST_CANCELLED` | POST /cancel |
| `PARTICIPANT_JOINED` | Approve, rejoin |
| `PARTICIPANT_LEFT` | POST /leave |
| `PARTICIPANT_KICKED` | POST /kick |
| `PARTICIPANT_MIC_MUTED_BY_OWNER` | POST /mute-mic |
| `OWNER_LEFT` | Owner leave (sau 3s debounce) |
| `OWNER_REJOINED` | Owner rejoin |
| `CHAT_MESSAGE_SENT` | POST /chat/messages |
| `ANNOTATION_CREATED` | POST /annotations |
| `MUSIC_SONG_CHANGED` | Select song |
| `MUSIC_PLAYBACK_STATE_CHANGED` | Play/pause/seek/volume |

```java
log.info("Room created: roomId={}, roomCode={}, ownerId={}",
    roomId, roomCode, ownerId);
```

### 3.4. DEBUG — Chi tiết dev

| Event | Khi nào |
|---|---|
| `VALIDATION_TRACE` | Input validation step-by-step |
| `STATE_TRANSITION_TRACE` | From → to transition details |
| `WS_MESSAGE_DETAILS` | WS frame content |
| `SQL_QUERY_PARAMS` | Native query parameters |
| `TRANSACTION_DETAILS` | Begin/commit/rollback |

```java
log.debug("State transition: participantId={}, from={}, to={}, trigger={}",
    participantId, fromState, toState, trigger);
```

---

## 4. Audit Logging

### 4.1. Audit Tables

Module Live Room có **3 audit tables**:

1. `liveroom_admin_actions` — Kick, remote mute (R-ADMIN-06 v1.8)
2. `liveroom_ownership_history` — Owner change (R-ROLE-13 v1.8)
3. `liveroom_privacy_actions` — Email hide/show (R-DISPLAY-06 v1.8)

Xem schema chi tiết trong `liveroom-data-model.md`.

### 4.2. Audit Write Pattern

```java
@Service
@RequiredArgsConstructor
public class AuditLogService {
    
    private final RoomAdminActionRepository roomAdminActionRepository;
    private final RoomOwnershipHistoryRepository ownershipHistoryRepository;
    
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
        roomAdminActionRepository.save(action);
        
        log.info("AUDIT: room kick logged: roomId={}, actor={}, target={}, reason={}",
            roomId, actorId, targetId, reason);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOwnershipChange(UUID roomId, UUID newOwnerId, OwnershipChangeType changeType, String reason) {
        RoomOwnershipHistory entry = RoomOwnershipHistory.builder()
            .id(UUID.randomUUID())
            .roomId(roomId)
            .ownerUserId(newOwnerId)
            .changeType(changeType)
            .reason(reason)
            .changedAt(OffsetDateTime.now())
            .build();
        ownershipHistoryRepository.save(entry);
        
        log.info("AUDIT: ownership change: roomId={}, newOwner={}, type={}",
            roomId, newOwnerId, changeType);
    }
}
```

### 4.3. Audit Trail Coverage

Audit log BẮT BUỘC cho:

| Action | Audit table | Lý do |
|---|---|---|
| Kick participant | `liveroom_admin_actions` | Có thể khiếu nại |
| Remote-mute participant | `liveroom_admin_actions` | Có thể khiếu nại |
| Owner force-end room | `liveroom_ownership_history` | Downgrade compliance |
| Owner change (PRO↔USER) | `liveroom_ownership_history` | Quyền sở hữu |
| User toggle email hide/show | `liveroom_privacy_actions` | GDPR compliance |

### 4.4. Audit Query API

```java
@GetMapping("/{roomId}/admin-actions")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<Page<AdminActionResponse>>> getAdminActions(
        @PathVariable UUID roomId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    
    Page<RoomAdminAction> actions = auditLogService.getAdminActions(roomId, page, size);
    return ResponseEntity.ok(ApiResponse.success(
        message("LIVEROOM_AUDIT_FETCHED"),
        actions.map(this::toDto)
    ));
}
```

---

## 5. Business Event Logging

### 5.1. KPI Metrics

| Metric | Source | Aggregation |
|---|---|---|
| Active rooms count | `liveroom_rooms` WHERE status = ACTIVE | Real-time |
| Participants online | `liveroom_participants` WHERE state IN (ACTIVE, RECONNECTING) | Real-time |
| Total rooms created | `liveroom_rooms` (count) | Daily |
| Rooms ended (by reason) | `liveroom_rooms.ended_reason` | Daily |
| Average room duration | `ended_at - started_at` | Weekly |
| Music plays per room | `liveroom_playback_states` (count by room) | Daily |
| Chat messages per room | `liveroom_chat_messages` (count by room) | Daily |
| Annotations per room | `liveroom_annotations` (count by room) | Daily |
| Kick rate | `liveroom_admin_actions` WHERE action_type = KICK | Weekly |
| Remote mute rate | `liveroom_admin_actions` WHERE action_type = REMOTE_MUTE | Weekly |

### 5.2. Event Streaming (Kafka)

```java
@Component
@RequiredArgsConstructor
public class BusinessEventPublisher {
    
    private final KafkaTemplate<String, BusinessEvent> kafkaTemplate;
    
    private static final String TOPIC = "liveroom-business-events";
    
    public void publishRoomCreated(LiveRoom room) {
        BusinessEvent event = BusinessEvent.builder()
            .eventType("ROOM_CREATED")
            .timestamp(OffsetDateTime.now())
            .userId(room.getOwnerId())
            .roomId(room.getId())
            .properties(Map.of(
                "maxParticipants", room.getMaxParticipants(),
                "roomCode", room.getRoomCode()
            ))
            .build();
        
        kafkaTemplate.send(TOPIC, room.getId().toString(), event);
    }
}
```

### 5.3. ClickHouse Schema

```sql
CREATE TABLE liveroom_business_events (
    event_date DateTime64,
    event_type LowCardinality(String),
    user_id UUID,
    room_id UUID,
    room_session_cycle_id Nullable(UUID),
    properties String  -- JSON
) ENGINE = MergeTree()
ORDER BY (event_date, room_id);
```

---

## 6. Tracing (OpenTelemetry)

### 6.1. Trace Context

```yaml
otel:
  service:
    name: liveroom-service
  exporter:
    otlp:
      endpoint: http://otel-collector:4317
  resource:
    attributes:
      deployment.environment: ${ENV}
```

### 6.2. Span cho REST Endpoint

```java
@WithSpan
@Transactional
public RoomResponse createRoom(RoomCreateRequest request) {
    Span span = Span.current();
    span.setAttribute("room.name", request.getRoomName());
    span.setAttribute("room.max_participants", request.getMaxParticipants());
    
    LiveRoom room = roomRepository.save(...);
    
    span.setAttribute("room.id", room.getId().toString());
    span.setAttribute("room.code", room.getRoomCode());
    
    return RoomResponse.fromEntity(room);
}
```

### 6.3. Span cho DB Query

```java
@Observed(name = "liveroom.room.findByCode")
public Optional<LiveRoom> findByRoomCode(String code) {
    return roomRepository.findByRoomCode(code);
}
```

### 6.4. Trace ID Propagation

- HTTP header: `traceparent` (W3C Trace Context)
- WebSocket: trong STOMP header `traceparent` hoặc custom header
- Kafka: trong message header `traceparent`

---

## 7. Metrics (Micrometer + Prometheus)

### 7.1. Counter Metrics

```java
@Component
@RequiredArgsConstructor
public class LiveroomMetrics {
    
    private final MeterRegistry registry;
    
    public void incrementRoomCreated() {
        Counter.builder("liveroom.room.created.total")
            .description("Total rooms created")
            .register(registry)
            .increment();
    }
    
    public void incrementRequestApproved() {
        Counter.builder("liveroom.request.approved.total")
            .description("Total join requests approved")
            .register(registry)
            .increment();
    }
    
    public void incrementRequestRejectedBy(String reason) {
        Counter.builder("liveroom.request.rejected.total")
            .tag("reason", reason)
            .register(registry)
            .increment();
    }
    
    public void incrementKick() {
        Counter.builder("liveroom.participant.kicked.total")
            .description("Total participants kicked")
            .register(registry)
            .increment();
    }
    
    public void incrementMusicConflict() {
        Counter.builder("liveroom.music.state.conflict.total")
            .description("Music playback state conflicts (optimistic lock)")
            .register(registry)
            .increment();
    }
}
```

### 7.2. Histogram Metrics

```java
@Component
@RequiredArgsConstructor
public class LiveroomLatencyMetrics {
    
    private final MeterRegistry registry;
    
    public void recordLockAcquire(Duration duration) {
        Timer.builder("liveroom.lock.acquire.duration")
            .description("Pessimistic lock acquire duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(duration);
    }
    
    public void recordWsBroadcast(Duration duration) {
        Timer.builder("liveroom.ws.broadcast.duration")
            .description("WebSocket broadcast duration")
            .register(registry)
            .record(duration);
    }
}
```

### 7.3. Gauge Metrics

```java
@Component
@RequiredArgsConstructor
public class LiveroomGaugeMetrics {
    
    private final LiveRoomRepository roomRepository;
    
    @PostConstruct
    public void registerGauges() {
        Gauge.builder("liveroom.active_rooms", this::countActiveRooms)
            .description("Active rooms count")
            .register(meterRegistry);
        
        Gauge.builder("liveroom.participants.online", this::countOnlineParticipants)
            .description("Online participants count")
            .register(meterRegistry);
    }
    
    private double countActiveRooms() {
        return roomRepository.countByStatus(RoomStatus.ACTIVE);
    }
    
    private double countOnlineParticipants() {
        return participantRepository.countByStateIn(
            List.of(ParticipantState.ACTIVE, ParticipantState.RECONNECTING)
        );
    }
}
```

### 7.4. Prometheus Exporter

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  metrics:
    tags:
      application: liveroom
      environment: ${ENV}
```

---

## 8. Alerting Rules

### 8.1. Critical Alerts

```yaml
- alert: LiveroomDatabaseDown
  expr: up{job="liveroom-service"} == 0
  for: 1m
  severity: critical
  annotations:
    summary: "Liveroom service is down"

- alert: LiveroomHighErrorRate
  expr: rate(liveroom_error_total[5m]) > 0.05
  for: 5m
  severity: critical
  annotations:
    summary: "Liveroom error rate > 5%"

- alert: LiveroomCapacityTooHigh
  expr: liveroom_active_rooms > 5000
  for: 10m
  severity: warning
  annotations:
    summary: "Too many active rooms (capacity strain)"

- alert: LiveroomMusicStateConflict
  expr: rate(liveroom_music_state_conflict_total[1m]) > 5
  for: 5m
  severity: warning
  annotations:
    summary: "Music state conflict rate too high"
```

### 8.2. Warning Alerts

```yaml
- alert: LiveroomSlowLock
  expr: histogram_quantile(0.99, liveroom_lock_acquire_duration) > 500
  for: 10m
  severity: warning
  annotations:
    summary: "Pessimistic lock p99 > 500ms"

- alert: LiveroomBruteForceSpike
  expr: rate(liveroom_brute_force_total[5m]) > 10
  for: 5m
  severity: warning
  annotations:
    summary: "Brute force attempts spike"

- alert: LiveroomGraceExpirySpike
  expr: increase(liveroom_grace_expiry_total[1h]) > 20
  severity: warning
  annotations:
    summary: "Too many rooms ended due to grace expiry"
```

---

## 9. Log Retention

### 9.1. Application Log (ELK/Loki)

- **Hot storage**: 7 ngày
- **Cold storage**: 30 ngày (S3/GCS)
- **Retention**: 30 ngày tổng cộng
- **Index**: theo `service`, `level`, `date`

### 9.2. Audit Tables (PostgreSQL)

- **Retention**: 90 ngày (configurable POST-MVP)
- **Cleanup job**: weekly, xoá `created_at < now - 90 days`
- **Critical actions**: giữ 1 năm (kick, force-end)

### 9.3. Business Events (ClickHouse/S3)

- **Retention**: 1 năm
- **Aggregation**: nightly rollup
- **Query**: qua BI tool (Grafana, Metabase)

### 9.4. Backup

- **Daily backup**: PostgreSQL (full + WAL archive)
- **Retention**: 30 ngày
- **DR**: restore từ backup trong 1h

---

## 10. PII Protection

### 10.1. CẤM Log

```java
// ❌ KHÔNG log email full
log.info("User joined: email={}", user.getEmail());

// ❌ KHÔNG log password
log.debug("Login attempt: password={}", password);

// ❌ KHÔNG log token
log.info("Auth: token={}", jwt);

// ❌ KHÔNG log chat content (PII có thể chứa)
log.info("Chat sent: content={}", message.getContent());

// ❌ KHÔNG log annotation content
log.info("Annotation: content={}", annotation.getContent());
```

### 10.2. ĐƯỢC Log

```java
// ✅ Log userId (UUID, không phải PII)
log.info("User joined: userId={}", userId);

// ✅ Log email partial (R-DISPLAY-06 v1.8 short form)
log.info("User joined: emailMasked={}", maskEmail(user.getEmail()));  // "a***@congty.com"

// ✅ Log roomId, roomCode (không PII)
log.info("Room created: roomId={}, roomCode={}", roomId, roomCode);

// ✅ Log action type (không PII)
log.info("Request rejected: rejectionReason={}", reason);

// ✅ Log chat metadata (không content)
log.info("Chat sent: messageId={}, messageLength={}", messageId, length);
```

### 10.3. Email Masking

```java
public static String maskEmail(String email) {
    if (email == null || !email.contains("@")) return "***";
    
    String[] parts = email.split("@");
    String local = parts[0];
    String domain = parts[1];
    
    if (local.length() <= 2) {
        return "*".repeat(local.length()) + "@" + domain;
    }
    return local.charAt(0) + "*".repeat(local.length() - 2) + 
           local.charAt(local.length() - 1) + "@" + domain;
}
```

Ví dụ:
- `an@congty.com` → `a***n@congty.com`
- `binh.le@congty.com` → `b******e@congty.com`
- `a@congty.com` → `*@congty.com`

---

## 11. Log Sampling (POST-MVP)

### 11.1. Sampling cho high-volume

- INFO logs: sample 100% (quan trọng)
- DEBUG logs: sample 10% (chi tiết)
- SQL query params: sample 1% (volume cao)
- WS frame content: sample 1%

### 11.2. Configuration

```yaml
logging:
  level:
    com.pwb.liveroom: INFO
    com.pwb.liveroom.core.service.LiveRoomService: DEBUG
    com.pwb.liveroom.persistence: DEBUG  # Sample 1%
```

---

## 12. Testing Logging

### 12.1. Unit Test

```java
@ExtendWith(OutputCaptureExtension.class)
class LiveRoomServiceLoggingTest {
    
    @Autowired LiveRoomService liveRoomService;
    
    @Test
    void roomCreated_logs_INFO(CapturedOutput output) {
        // ... create room ...
        
        assertThat(output.getOut()).contains("Room created: roomId=");
    }
    
    @Test
    void exception_logs_ERROR(CapturedOutput output) {
        // ... trigger exception ...
        
        assertThat(output.getOut()).contains("ERROR");
        assertThat(output.getOut()).contains("Database error");
    }
}
```

### 12.2. PII Test

```java
@Test
void logs_do_not_contain_PII(CapturedOutput output) {
    String userEmail = "an@congty.com";
    
    liveRoomService.someMethod(userEmail);
    
    assertThat(output.getOut()).doesNotContain(userEmail);
    assertThat(output.getOut()).contains("emailMasked=a***n@congty.com");
}
```

### 12.3. Audit Log Test

```java
@Test
void kick_action_creates_audit_record() {
    UUID roomId = createRoom();
    UUID ownerId = createOwner();
    UUID targetId = createParticipant(roomId);
    
    roomService.kickParticipant(roomId, ownerId, targetId, "spam");
    
    List<RoomAdminAction> audits = roomAdminActionRepository.findByRoomId(roomId);
    assertThat(audits).hasSize(1);
    assertThat(audits.get(0).getActionType()).isEqualTo(ActionType.KICK);
    assertThat(audits.get(0).getReason()).isEqualTo("spam");
}
```

---

## 13. Out of Scope (POST-MVP)

- ❌ Real-time log streaming (cho live debugging)
- ❌ Log aggregation per feature (chỉ cần per service)
- ❌ Audit log signing (blockchain-style)
- ❌ GDPR right-to-be-forgotten cho audit log (cần legal review)

---

## 14. Tham chiếu chéo

- **Business**: `liveroom-business-requirements.md` v1.8
- **Data model**: `liveroom-data-model.md` v1.0
- **API**: `liveroom-api-spec.md` v1.0
- **WebSocket**: `liveroom-ws-protocol.md` v1.0
- **State machines**: `liveroom-state-machines.md` v1.0
- **Concurrency**: `liveroom-concurrency.md` v1.0
- **Jobs**: `liveroom-jobs.md` v1.0
- **i18n keys**: `liveroom-i18n-keys.md` v1.0

---

**Người viết**: Senior Dev Team
**Reviewer**: Backend Lead, DevOps, Security Lead, QA Lead
**Ngày review**: Pending