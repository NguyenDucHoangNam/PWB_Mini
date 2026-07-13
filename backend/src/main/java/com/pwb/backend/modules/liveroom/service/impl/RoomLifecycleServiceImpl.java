package com.pwb.backend.modules.liveroom.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.entity.Room;
import com.pwb.backend.modules.liveroom.enums.RoomMode;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.repository.RoomRepository;
import com.pwb.backend.modules.liveroom.service.RoomCodeGenerator;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class RoomLifecycleServiceImpl implements RoomLifecycleService {

    private static final String ROOM_HASH_FIELD_HOST_ID = "hostId";
    private static final String ROOM_HASH_FIELD_HOST_DISPLAY_NAME = "hostDisplayName";
    private static final String ROOM_HASH_FIELD_MODE = "mode";
    private static final String ROOM_HASH_FIELD_STATUS = "status";
    private static final String ROOM_HASH_FIELD_MAX_PARTICIPANTS = "maxParticipants";
    private static final String ROOM_HASH_FIELD_CURRENT_PARTICIPANTS = "currentParticipants";
    private static final String ROOM_HASH_FIELD_CREATED_AT = "createdAt";

    private static final String DISCONNECT_KEY_VALUE = "disconnected";

    private final RoomRepository roomRepository;
    private final RoomCodeGenerator roomCodeGenerator;
    private final LiveRoomProperties properties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public CreateRoomResponse createRoom(RoomMode mode, UUID hostId, String hostDisplayName) {
        log.info("CREATE_ROOM_REQUEST hostId={} mode={}", hostId, mode);

        Optional<Room> existing = roomRepository.findFirstByHostIdAndStatusOrderByCreatedAtDesc(hostId, RoomStatus.ACTIVE);
        if (existing.isPresent()) {
            Room current = existing.get();
            log.warn("ROOM_ALREADY_ACTIVE_REJECTED hostId={} currentRoomCode={}",
                    hostId, current.getRoomCode());
            throw new BusinessException(
                    LiveRoomErrorCode.ROOM_ALREADY_ACTIVE,
                    "Host already owns an ACTIVE room " + current.getRoomCode(),
                    null,
                    java.util.Map.<String, Object>of("roomCode", current.getRoomCode()));
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
        String disconnectKey = LiveRoomRedisKeys.hostDisconnectKey(roomCode);
        stringRedisTemplate.opsForValue()
                .set(disconnectKey, DISCONNECT_KEY_VALUE,
                        Duration.ofMinutes(properties.getHostGracePeriodMinutes()));
        stringRedisTemplate.opsForHash()
                .put(LiveRoomRedisKeys.roomStatusKey(roomCode),
                        ROOM_HASH_FIELD_STATUS, RoomStatus.INACTIVE_HOST.name());
        log.warn("HOST_DISCONNECTED_GRACE roomCode={} graceMinutes={} sessionId={}",
                roomCode, properties.getHostGracePeriodMinutes(), sessionId);
        markRoomInactiveHost(roomCode);
    }

    @Override
    @Transactional
    public void closeRoom(String roomCode) {
        Room room = roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.ACTIVE)
                .or(() -> roomRepository.findByRoomCodeAndStatus(roomCode, RoomStatus.INACTIVE_HOST))
                .orElse(null);
        if (room == null) {
            log.info("ROOM_CLOSE_NOOP roomCode={} reason=not_found", roomCode);
            stringRedisTemplate.delete(LiveRoomRedisKeys.roomStatusKey(roomCode));
            stringRedisTemplate.delete(LiveRoomRedisKeys.hostDisconnectKey(roomCode));
            stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.ACTIVE_ROOM_ZSET_KEY, roomCode);
            return;
        }
        Instant now = Instant.now();
        room.markClosed(now);
        roomRepository.save(room);
        stringRedisTemplate.delete(LiveRoomRedisKeys.roomStatusKey(roomCode));
        stringRedisTemplate.delete(LiveRoomRedisKeys.hostDisconnectKey(roomCode));
        stringRedisTemplate.delete(LiveRoomRedisKeys.roomDelegatedKey(roomCode));
        stringRedisTemplate.opsForZSet().remove(LiveRoomRedisKeys.ACTIVE_ROOM_ZSET_KEY, roomCode);
        log.info("ROOM_CLOSED roomCode={} hostId={} closedAt={}", roomCode, room.getHostId(), now);
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