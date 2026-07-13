package com.pwb.backend.modules.liveroom.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.ws.DelegationChangedMessage;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.service.ControlDelegationService;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.RedisMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlDelegationServiceImpl implements ControlDelegationService {

    private static final String ACTION_GRANT = "GRANT";
    private static final String ACTION_REVOKE = "REVOKE";

    private static final String ROOM_HASH_FIELD_STATUS = "status";
    private static final String ROOM_HASH_FIELD_HOST_ID = "hostId";
    private static final String ROOM_HASH_FIELD_GLOBAL_DELEGATION = "globalDelegation";

    private static final String GLOBAL_DELEGATION_TRUE = "true";
    private static final String GLOBAL_DELEGATION_FALSE = "false";

    private final StringRedisTemplate stringRedisTemplate;
    private final LiveRoomMembershipNotifier notifier;
    private final LiveRoomProperties properties;
    private final ObjectMapper objectMapper;
    private final RedisMessagePublisher publisher;

    @Override
    public void delegateControl(String roomCode, UUID hostId, UUID listenerId, String action) {
        assertRoomHost(roomCode, hostId);
        if (listenerId == null) {
            log.warn("UNAUTHORIZED_DELEGATE_ATTEMPT roomCode={} userId={} action={} reason=null_listener",
                    roomCode, hostId, action);
            throw new BusinessException(LiveRoomErrorCode.DELEGATION_FORBIDDEN_NOT_HOST,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        String normalized = action == null ? "" : action.toUpperCase();
        if (!ACTION_GRANT.equals(normalized) && !ACTION_REVOKE.equals(normalized)) {
            log.warn("UNAUTHORIZED_DELEGATE_ATTEMPT roomCode={} userId={} action={} reason=invalid_action",
                    roomCode, hostId, action);
            throw new BusinessException(LiveRoomErrorCode.DELEGATION_FORBIDDEN_NOT_HOST,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode, "action", String.valueOf(action)));
        }

        String delegatedKey = LiveRoomRedisKeys.roomDelegatedKey(roomCode);
        if (ACTION_GRANT.equals(normalized)) {
            stringRedisTemplate.opsForSet().add(delegatedKey, listenerId.toString());
            stringRedisTemplate.expire(delegatedKey, Duration.ofSeconds(properties.getPhase2TtlSeconds()));
        } else {
            stringRedisTemplate.opsForSet().remove(delegatedKey, listenerId.toString());
        }

        publishDelegationEvent(listenerId, roomCode, ACTION_GRANT.equals(normalized));
        broadcastDelegationChange(roomCode);

        log.info("CONTROL_DELEGATED roomCode={} listenerId={} action={} hostId={}",
                roomCode, listenerId, normalized, hostId);
    }

    @Override
    public void setGlobalDelegation(String roomCode, UUID hostId, boolean enabled) {
        assertRoomHost(roomCode, hostId);
        String statusKey = LiveRoomRedisKeys.roomStatusKey(roomCode);
        stringRedisTemplate.opsForHash().put(statusKey,
                ROOM_HASH_FIELD_GLOBAL_DELEGATION, enabled ? GLOBAL_DELEGATION_TRUE : GLOBAL_DELEGATION_FALSE);
        stringRedisTemplate.expire(statusKey, Duration.ofSeconds(properties.getPhase2TtlSeconds()));

        publishGlobalChangeEvent(roomCode, enabled);
        broadcastDelegationChange(roomCode);

        log.info("GLOBAL_DELEGATION_CHANGED roomCode={} enabled={} hostId={}", roomCode, enabled, hostId);
    }

    @Override
    public DelegationState getDelegationState(String roomCode) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        boolean global = GLOBAL_DELEGATION_TRUE.equals(asString(statusHash.get(ROOM_HASH_FIELD_GLOBAL_DELEGATION)));

        Set<String> rawMembers = stringRedisTemplate.opsForSet()
                .members(LiveRoomRedisKeys.roomDelegatedKey(roomCode));
        List<UUID> delegatedUserIds = new ArrayList<>();
        if (rawMembers != null) {
            for (String raw : rawMembers) {
                try {
                    delegatedUserIds.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ex) {
                    log.warn("DELEGATION_PARSE_UUID_FAILED roomCode={} raw={}", roomCode, raw);
                }
            }
        }
        return new DelegationState(global, delegatedUserIds);
    }

    private void broadcastDelegationChange(String roomCode) {
        DelegationState state = getDelegationState(roomCode);
        DelegationChangedMessage payload = new DelegationChangedMessage(
                "DELEGATION_CHANGED",
                new DelegationChangedMessage.Data(state.globalDelegation(), state.delegatedUserIds()));
        notifier.notifyDelegationChange(roomCode, payload);
    }

    private void publishDelegationEvent(UUID userId, String roomCode, boolean isController) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("roomCode", roomCode);
        payload.put("isController", isController);
        writeAndPublish(payload);
    }

    private void publishGlobalChangeEvent(String roomCode, boolean enabled) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "GLOBAL_CHANGED");
        payload.put("roomCode", roomCode);
        payload.put("enabled", enabled);
        writeAndPublish(payload);
    }

    private void writeAndPublish(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            publisher.publishDelegationEvent(json);
        } catch (Exception ex) {
            log.warn("REDIS_PUBSUB_PUBLISH_FAILED reason=serialization reason={}", ex.getMessage());
        }
    }

    private void assertRoomHost(String roomCode, UUID hostId) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        String status = asString(statusHash.get(ROOM_HASH_FIELD_STATUS));
        String owner = asString(statusHash.get(ROOM_HASH_FIELD_HOST_ID));
        if (!RoomStatus.ACTIVE.name().equals(status) && !RoomStatus.INACTIVE_HOST.name().equals(status)) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        if (owner == null || !owner.equals(hostId.toString())) {
            log.warn("HOST_FORBIDDEN roomCode={} callerId={} ownerId={}", roomCode, hostId, owner);
            throw new BusinessException(LiveRoomErrorCode.FORBIDDEN_NOT_HOST,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}