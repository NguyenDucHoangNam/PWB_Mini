package com.pwb.backend.modules.liveroom.service.impl;

import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import java.util.Set;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.outbox.OutboxService;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.dto.ws.PlaybackUpdateMessage;
import com.pwb.backend.modules.liveroom.dto.ws.RoomClosedMessage;
import com.pwb.backend.modules.liveroom.dto.ws.RoomEvictionEvent;
import com.pwb.backend.modules.liveroom.entity.Room;
import com.pwb.backend.modules.liveroom.enums.RoomMode;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.outbox.RoomLifecycleEndedEvent;
import com.pwb.backend.modules.liveroom.repository.RoomRepository;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.RedisMessagePublisher;
import com.pwb.backend.modules.liveroom.service.RoomCodeGenerator;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RoomLifecycleServiceImpl implements RoomLifecycleService {

    private static final String ROOM_HASH_FIELD_HOST_ID = "hostId";
    private static final String ROOM_HASH_FIELD_HOST_DISPLAY_NAME = "hostDisplayName";
    private static final String ROOM_HASH_FIELD_MODE = "mode";
    private static final String ROOM_HASH_FIELD_STATUS = "status";
    private static final String ROOM_HASH_FIELD_MAX_PARTICIPANTS = "maxParticipants";
    private static final String ROOM_HASH_FIELD_CURRENT_PARTICIPANTS = "currentParticipants";
    private static final String ROOM_HASH_FIELD_CREATED_AT = "createdAt";

    private static final String PLAYBACK_HASH_FIELD_STATE = "playbackState";
    private static final String PLAYBACK_HASH_FIELD_LAST_UPDATED_BY = "lastUpdatedBy";
    private static final String PLAYBACK_HASH_FIELD_SERVER_TIMESTAMP = "serverTimestamp";

    private static final String PLAYBACK_STATE_PAUSED = "PAUSED";

    private static final String DISCONNECT_KEY_VALUE = "disconnected";

    private static final String CLOSE_REASON_HOST_VOLUNTARY = "HOST_VOLUNTARY";
    private static final String CLOSE_REASON_HOST_TIMEOUT = "HOST_TIMEOUT";
    private static final String CLOSE_REASON_EMPTY_ROOM = "EMPTY_ROOM_15M";
    private static final String CLOSE_REASON_HOST_RECONNECTED_CANCELLED = "HOST_RECONNECTED";

    private static final String SESSION_TO_ROOM_PUBSUB_PAYLOAD_FORMAT = "{\"event\":\"%s\",\"roomCode\":\"%s\"}";

    private final RoomRepository roomRepository;
    private final RoomCodeGenerator roomCodeGenerator;
    private final LiveRoomProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessagePublisher redisMessagePublisher;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;
    @Lazy
    private final LiveRoomMembershipNotifier notifier;

    public RoomLifecycleServiceImpl(RoomRepository roomRepository,
                                    RoomCodeGenerator roomCodeGenerator,
                                    LiveRoomProperties properties,
                                    StringRedisTemplate stringRedisTemplate,
                                    RedisMessagePublisher redisMessagePublisher,
                                    OutboxService outboxService,
                                    ApplicationEventPublisher eventPublisher,
                                    @Lazy LiveRoomMembershipNotifier notifier) {
        this.roomRepository = roomRepository;
        this.roomCodeGenerator = roomCodeGenerator;
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisMessagePublisher = redisMessagePublisher;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.notifier = notifier;
    }

    @Override
    @Transactional
    public CreateRoomResponse createRoom(RoomMode mode, UUID hostId, String hostDisplayName) {
        log.info("CREATE_ROOM_REQUEST hostId={} mode={}", hostId, mode);

        Optional<Room> existing = roomRepository.findFirstByHostIdAndStatusOrderByCreatedAtDesc(hostId, RoomStatus.ACTIVE);
        if (existing.isPresent()) {
            Room current = existing.get();
            log.warn("ROOM_ALREADY_ACTIVE_REJECTED hostId={} currentRoomCode={}",
                    hostId, current.getRoomCode());
            throw BusinessException.builder()
                    .errorCode(LiveRoomErrorCode.ROOM_ALREADY_ACTIVE)
                    .customMessage("Host already owns an ACTIVE room " + current.getRoomCode())
                    .details(Map.<String, Object>of("roomCode", current.getRoomCode()))
                    .build();
        }

        int maxRetries = properties.getCodeCollisionMaxRetries();
        Room saved = null;
        String lockedCode = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String candidate = roomCodeGenerator.generate();
            String lockKey = LiveRoomRedisKeys.roomLockKey(candidate);

            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", Duration.ofSeconds(properties.getLockTtlSeconds()));
            if (!Boolean.TRUE.equals(acquired)) {
                log.warn("ROOM_CODE_COLLISION reason=lock_busy collisionCode={} attempt={}", candidate, attempt);
                continue;
            }

            if (roomRepository.findByRoomCodeAndStatus(candidate, RoomStatus.ACTIVE).isPresent()) {
                log.warn("ROOM_CODE_COLLISION reason=db_exists collisionCode={} attempt={}", candidate, attempt);
                releaseLock(lockKey);
                continue;
            }

            try {
                saved = persistRoom(candidate, mode, hostId);
                lockedCode = lockKey;
                log.info("ROOM_CREATED roomCode={} hostId={} mode={} maxParticipants={}",
                        saved.getRoomCode(), hostId, mode, saved.getMaxParticipants());
                break;
            } catch (DataIntegrityViolationException ex) {
                log.warn("ROOM_CODE_COLLISION reason=data_integrity collisionCode={} attempt={} error={}",
                        candidate, attempt, ex.getMostSpecificCause().getMessage());
                releaseLock(lockKey);
                continue;
            } catch (RuntimeException ex) {
                releaseLock(lockKey);
                throw ex;
            }
        }

        if (saved == null) {
            log.error("ROOM_CODE_COLLISION_FAILED hostId={} maxRetries={}", hostId, maxRetries);
            throw new BusinessException(LiveRoomErrorCode.ROOM_CODE_COLLISION_FAILED);
        }

        releaseLock(LiveRoomRedisKeys.roomLockKey(saved.getRoomCode()));

        writePhase1StatusHash(saved, hostDisplayName);
        addToActiveZSet(saved.getRoomCode(), saved.getCreatedAt());

        return CreateRoomResponse.from(saved, hostDisplayName);
    }

    @Override
    public void activateRoomPhase2(String roomCode) {
        String key = LiveRoomRedisKeys.roomStatusKey(roomCode);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            log.warn("ROOM_ACTIVATE_PHASE2_FAILED reason=key_expired roomCode={}", roomCode);
            return;
        }
        stringRedisTemplate.expire(key, Duration.ofSeconds(properties.getPhase2TtlSeconds()));
        stringRedisTemplate.opsForHash().put(key, ROOM_HASH_FIELD_STATUS, RoomStatus.ACTIVE.name());
        log.info("ROOM_ACTIVATED_PHASE2 roomCode={} ttlSeconds={}", roomCode, properties.getPhase2TtlSeconds());
    }

    @Override
    public void markHostDisconnected(String sessionId) {
        String roomCode = stringRedisTemplate.opsForValue()
                .get(LiveRoomRedisKeys.sessionRoomKey(sessionId));
        if (roomCode == null || roomCode.isBlank()) {
            log.warn("HOST_DISCONNECT_LOOKUP_FAILED sessionId={}", sessionId);
            return;
        }
        markHostInactive(roomCode);
    }

    @Override
    @Transactional
    public void closeRoom(String roomCode) {
        closeRoom(roomCode, CLOSE_REASON_HOST_VOLUNTARY);
    }

    @Override
    @Transactional
    public void closeRoomByHost(String roomCode, UUID hostId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> BusinessException.builder()
                        .errorCode(LiveRoomErrorCode.ROOM_NOT_FOUND)
                        .customMessage("Room " + roomCode + " not found")
                        .details(Map.<String, Object>of("roomCode", roomCode))
                        .build());
        if (room.getStatus() == RoomStatus.CLOSED) {
            log.info("ROOM_CLOSE_NOOP roomCode={} reason=already_closed", roomCode);
            throw BusinessException.builder()
                    .errorCode(LiveRoomErrorCode.ROOM_LIFECYCLE_ALREADY_CLOSED)
                    .customMessage("Room " + roomCode + " already closed")
                    .details(Map.<String, Object>of("roomCode", roomCode))
                    .build();
        }
        if (!room.getHostId().equals(hostId)) {
            log.warn("UNAUTHORIZED_CLOSE_ATTEMPT roomCode={} callerId={} ownerId={}",
                    roomCode, hostId, room.getHostId());
            throw BusinessException.builder()
                    .errorCode(LiveRoomErrorCode.FORBIDDEN_NOT_HOST)
                    .details(Map.<String, Object>of("roomCode", roomCode))
                    .build();
        }
        log.info("VOLUNTARY_CLOSE_REQUEST roomCode={} hostId={}", roomCode, hostId);
        closeRoom(roomCode, CLOSE_REASON_HOST_VOLUNTARY);
    }

    @Override
    public void markHostInactive(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return;
        }
        String statusKey = LiveRoomRedisKeys.roomStatusKey(roomCode);
        String disconnectKey = LiveRoomRedisKeys.hostDisconnectKey(roomCode);

        Object rawStatus = stringRedisTemplate.opsForHash().get(statusKey, ROOM_HASH_FIELD_STATUS);
        if (rawStatus == null) {
            log.warn("HOST_DISCONNECT_STATUS_MISSING roomCode={}", roomCode);
            return;
        }
        String currentStatus = rawStatus.toString();
        if (RoomStatus.CLOSED.name().equals(currentStatus)) {
            log.info("HOST_DISCONNECT_SKIP_CLOSED roomCode={}", roomCode);
            return;
        }

        stringRedisTemplate.opsForValue()
                .set(disconnectKey, DISCONNECT_KEY_VALUE,
                        Duration.ofMinutes(properties.getHostGracePeriodMinutes()));
        stringRedisTemplate.opsForHash()
                .put(statusKey, ROOM_HASH_FIELD_STATUS, RoomStatus.INACTIVE_HOST.name());

        pausePlaybackOnHostDisconnect(roomCode);

        Instant expiry = Instant.now().plus(Duration.ofMinutes(properties.getHostGracePeriodMinutes()));
        stringRedisTemplate.opsForZSet()
                .add(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode, expiry.toEpochMilli());

        log.warn("HOST_DISCONNECTED_GRACE roomCode={} cooldownSeconds={}",
                roomCode, properties.getHostGracePeriodMinutes() * 60L);

        markRoomInactiveHost(roomCode);
    }

    @Override
    public void markHostActive(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return;
        }
        String statusKey = LiveRoomRedisKeys.roomStatusKey(roomCode);
        Object rawStatus = stringRedisTemplate.opsForHash().get(statusKey, ROOM_HASH_FIELD_STATUS);
        if (rawStatus == null) {
            return;
        }
        String currentStatus = rawStatus.toString();
        if (RoomStatus.CLOSED.name().equals(currentStatus)) {
            return;
        }
        if (!RoomStatus.INACTIVE_HOST.name().equals(currentStatus)) {
            return;
        }
        stringRedisTemplate.delete(LiveRoomRedisKeys.hostDisconnectKey(roomCode));
        stringRedisTemplate.opsForHash().put(statusKey, ROOM_HASH_FIELD_STATUS, RoomStatus.ACTIVE.name());
        Long removed = stringRedisTemplate.opsForZSet()
                .remove(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode);
        log.info("HOST_RECONNECTED_GRACE_CANCELLED roomCode={} timelineRemoved={}", roomCode, removed);
        markRoomReactivated(roomCode);
    }

    @Override
    public void scheduleEmptyRoomCleanup(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return;
        }
        Instant expiry = Instant.now().plus(Duration.ofMinutes(properties.getEmptyRoomGraceMinutes()));
        stringRedisTemplate.opsForZSet()
                .add(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode, expiry.toEpochMilli());
        log.info("EMPTY_ROOM_CLEANUP_SCHEDULED roomCode={} graceSeconds={}",
                roomCode, properties.getEmptyRoomGraceMinutes() * 60L);
    }

    @Override
    public void cancelEmptyRoomCleanup(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return;
        }
        Long removed = stringRedisTemplate.opsForZSet()
                .remove(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode);
        if (removed != null && removed > 0) {
            log.info("EMPTY_ROOM_CLEANUP_CANCELLED roomCode={}", roomCode);
        }
    }

    @Override
    public Set<String> findExpiredCleanupCandidates(long scoreCeilingExclusive, int limit) {
        Set<String> result = stringRedisTemplate.opsForZSet()
                .rangeByScore(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, 0, scoreCeilingExclusive, 0, limit);
        return result == null ? Set.of() : result;
    }

    @Override
    public boolean isStatusStillInactiveOrEmpty(String roomCode) {
        Object rawStatus = stringRedisTemplate.opsForHash()
                .get(LiveRoomRedisKeys.roomStatusKey(roomCode), ROOM_HASH_FIELD_STATUS);
        if (rawStatus == null) {
            return true;
        }
        String status = rawStatus.toString();
        return RoomStatus.INACTIVE_HOST.name().equals(status);
    }

    @Override
    public void forceBroadcastEviction(String roomCode) {
        String payload = String.format(SESSION_TO_ROOM_PUBSUB_PAYLOAD_FORMAT,
                RoomEvictionEvent.EVENT_FORCE_CLOSE_ROOM_SESSIONS, roomCode);
        redisMessagePublisher.publishRoomEviction(payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Room persistRoom(String roomCode, RoomMode mode, UUID hostId) {
        Instant now = Instant.now();
        Room room = Room.builder()
                .id(UUID.randomUUID())
                .roomCode(roomCode)
                .hostId(hostId)
                .mode(mode)
                .status(RoomStatus.ACTIVE)
                .maxParticipants(properties.getMaxParticipants())
                .createdAt(now)
                .build();
        return roomRepository.saveAndFlush(room);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markRoomInactiveHost(String roomCode) {
        roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.ACTIVE)
                .ifPresent(room -> {
                    room.markInactiveHost();
                    roomRepository.save(room);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markRoomReactivated(String roomCode) {
        roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.INACTIVE_HOST)
                .ifPresent(room -> {
                    room.markReactivated();
                    roomRepository.save(room);
                });
    }

    private void closeRoom(String roomCode, String reason) {
        Room room = roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.ACTIVE)
                .or(() -> roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.INACTIVE_HOST))
                .orElse(null);
        if (room == null) {
            log.info("ROOM_CLOSE_NOOP roomCode={} reason=not_found", roomCode);
            stringRedisTemplate.delete(LiveRoomRedisKeys.roomStatusKey(roomCode));
            stringRedisTemplate.delete(LiveRoomRedisKeys.hostDisconnectKey(roomCode));
            stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.ACTIVE_ROOM_ZSET_KEY, roomCode);
            stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode);
            return;
        }
        if (room.getStatus() == RoomStatus.CLOSED) {
            log.info("ROOM_CLOSE_NOOP roomCode={} reason=already_closed", roomCode);
            return;
        }

        Instant now = Instant.now();
        room.markClosed(now);
        roomRepository.save(room);

        RoomLifecycleEndedEvent payload = new RoomLifecycleEndedEvent(
                room.getId(),
                room.getRoomCode(),
                room.getHostId(),
                room.getCreatedAt(),
                now,
                room.getMaxParticipants(),
                reason);
        persistLifecycleOutboxEvent(room, payload);

        notifier.notifyRoomClosed(roomCode, new RoomClosedMessage(
                "ROOM_CLOSED",
                new RoomClosedMessage.Data(roomCode, resolveCloseReasonLabel(reason))));

        try {
            Thread.sleep(500L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        evictRoomKeysAndSessions(roomCode);
        stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.ACTIVE_ROOM_ZSET_KEY, roomCode);
        stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.CLEANUP_TIMELINE_ZSET_KEY, roomCode);

        log.info("ROOM_CLOSED roomCode={} hostId={} closedAt={} reason={}",
                roomCode, room.getHostId(), now, reason);
    }

    private void evictRoomKeysAndSessions(String roomCode) {
        stringRedisTemplate.delete(LiveRoomRedisKeys.roomStatusKey(roomCode));
        stringRedisTemplate.delete(LiveRoomRedisKeys.hostDisconnectKey(roomCode));
        stringRedisTemplate.delete(LiveRoomRedisKeys.roomDelegatedKey(roomCode));
        forceBroadcastEviction(roomCode);
    }

    private void pausePlaybackOnHostDisconnect(String roomCode) {
        Instant now = Instant.now();
        long serverNow = now.toEpochMilli();
        String playbackKey = LiveRoomRedisKeys.roomPlaybackKey(roomCode);

        Object hostIdRaw = stringRedisTemplate.opsForHash()
                .get(LiveRoomRedisKeys.roomStatusKey(roomCode), ROOM_HASH_FIELD_HOST_ID);
        String hostIdStr = hostIdRaw == null ? null : hostIdRaw.toString();

        stringRedisTemplate.opsForHash().put(playbackKey, PLAYBACK_HASH_FIELD_STATE, PLAYBACK_STATE_PAUSED);
        if (hostIdStr != null) {
            stringRedisTemplate.opsForHash().put(playbackKey, PLAYBACK_HASH_FIELD_LAST_UPDATED_BY, hostIdStr);
        }
        stringRedisTemplate.opsForHash().put(playbackKey, PLAYBACK_HASH_FIELD_SERVER_TIMESTAMP, String.valueOf(serverNow));
        stringRedisTemplate.expire(playbackKey, Duration.ofSeconds(properties.getPhase2TtlSeconds()));

        PlaybackUpdateMessage message = new PlaybackUpdateMessage(
                "PLAYBACK_UPDATED",
                new PlaybackUpdateMessage.Data(
                        "PAUSE",
                        PLAYBACK_STATE_PAUSED,
                        0.0,
                        serverNow,
                        null));
        notifier.notifyPlaybackUpdate(roomCode, message);
    }

    private void persistLifecycleOutboxEvent(Room room, RoomLifecycleEndedEvent payload) {
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_LIVE_ROOM,
                room.getId(),
                OutboxEventTypes.ROOM_LIFECYCLE_ENDED,
                room.getRoomCode(),
                payload,
                UUID.randomUUID());
        log.info("OUTBOX_LIFECYCLE_QUEUED roomId={} roomCode={} reason={}",
                room.getId(), room.getRoomCode(), payload.reason());
    }

    private String resolveCloseReasonLabel(String reason) {
        if (CLOSE_REASON_HOST_TIMEOUT.equals(reason)) {
            return "Host connection lost and grace period expired";
        }
        if (CLOSE_REASON_EMPTY_ROOM.equals(reason)) {
            return "Room remained empty beyond the AFK grace period";
        }
        if (CLOSE_REASON_HOST_RECONNECTED_CANCELLED.equals(reason)) {
            return "Host reconnected in time";
        }
        return "Host voluntarily closed the room";
    }

    private void writePhase1StatusHash(Room room, String hostDisplayName) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ROOM_HASH_FIELD_HOST_ID, room.getHostId().toString());
        fields.put(ROOM_HASH_FIELD_HOST_DISPLAY_NAME,
                hostDisplayName == null ? "" : hostDisplayName);
        fields.put(ROOM_HASH_FIELD_MODE, room.getMode().name());
        fields.put(ROOM_HASH_FIELD_STATUS, room.getStatus().name());
        fields.put(ROOM_HASH_FIELD_MAX_PARTICIPANTS, Integer.toString(room.getMaxParticipants()));
        fields.put(ROOM_HASH_FIELD_CURRENT_PARTICIPANTS, "1");
        fields.put(ROOM_HASH_FIELD_CREATED_AT, room.getCreatedAt().toString());
        String key = LiveRoomRedisKeys.roomStatusKey(room.getRoomCode());
        stringRedisTemplate.opsForHash().putAll(key, new HashMap<>(fields));
        stringRedisTemplate.expire(key, Duration.ofSeconds(properties.getPhase1TtlSeconds()));
    }

    private void addToActiveZSet(String roomCode, Instant createdAt) {
        stringRedisTemplate.opsForZSet()
                .add(LiveRoomRedisKeys.ACTIVE_ROOM_ZSET_KEY, roomCode, createdAt.toEpochMilli());
    }

    private void releaseLock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception ex) {
            log.warn("ROOM_LOCK_RELEASE_FAILED lockKey={} reason={}", lockKey, ex.getMessage());
        }
    }

    public List<Room> findOrphanCandidates(Instant threshold, int limit) {
        return roomRepository.findByStatusAndCreatedAtBefore(
                RoomStatus.ACTIVE, threshold, PageRequest.of(0, limit));
    }

    public boolean isRedisStatusKeyMissing(String roomCode) {
        return Boolean.FALSE.equals(
                stringRedisTemplate.hasKey(LiveRoomRedisKeys.roomStatusKey(roomCode)));
    }
}
