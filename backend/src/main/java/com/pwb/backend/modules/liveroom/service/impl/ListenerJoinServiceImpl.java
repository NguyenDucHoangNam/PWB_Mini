package com.pwb.backend.modules.liveroom.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.jwt.JwtSigner;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.response.JoinRoomResponse;
import com.pwb.backend.modules.liveroom.dto.ws.WaitingRequestNotification;
import com.pwb.backend.modules.liveroom.enums.RoomMode;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.service.ListenerJoinService;
import com.pwb.backend.modules.liveroom.service.LiveRoomLuaScripts;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListenerJoinServiceImpl implements ListenerJoinService {

    private static final String ROOM_STATUS_FIELD_MODE = "mode";
    private static final String ROOM_STATUS_FIELD_STATUS = "status";
    private static final String ROOM_STATUS_FIELD_HOST_ID = "hostId";
    private static final String ROOM_STATUS_FIELD_MAX_PARTICIPANTS = "maxParticipants";
    private static final String ROOM_STATUS_FIELD_CURRENT_PARTICIPANTS = "currentParticipants";

    private static final String JOIN_STATUS_APPROVED = "APPROVED";
    private static final String JOIN_STATUS_WAITING = "WAITING";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtSigner jwtSigner;
    private final LiveRoomLuaScripts luaScripts;
    private final LiveRoomProperties properties;
    private final ObjectMapper objectMapper;
    private final LiveRoomMembershipNotifier notifier;

    @Override
    public JoinRoomResponse joinRoom(String roomCode, String displayName) {
        log.info("JOIN_REQUEST roomCode={} displayName={}", roomCode, displayName);

        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            log.warn("JOIN_NOT_FOUND roomCode={}", roomCode);
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    "Room " + roomCode + " not found or not ACTIVE",
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }

        String roomStatus = asString(statusHash.get(ROOM_STATUS_FIELD_STATUS));
        if (!RoomStatus.ACTIVE.name().equals(roomStatus)) {
            log.warn("JOIN_NOT_ACTIVE roomCode={} status={}", roomCode, roomStatus);
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    "Room " + roomCode + " not in ACTIVE state",
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }

        String modeStr = asString(statusHash.get(ROOM_STATUS_FIELD_MODE));
        RoomMode mode = RoomMode.valueOf(modeStr);
        int current = parseInt(statusHash.get(ROOM_STATUS_FIELD_CURRENT_PARTICIPANTS));
        int max = parseInt(statusHash.get(ROOM_STATUS_FIELD_MAX_PARTICIPANTS));

        if (current >= max) {
            log.warn("JOIN_BLOCKED_FULL roomCode={} current={} max={}", roomCode, current, max);
            throw new BusinessException(LiveRoomErrorCode.ROOM_FULL,
                    "Room " + roomCode + " is full (" + current + "/" + max + ")");
        }

        UUID tempUserId = UUID.randomUUID();
        String temporaryToken = jwtSigner.generateAccessToken(tempUserId, null, properties.getListenerRole());
        log.debug("TEMP_TOKEN_ISSUED tempUserId={} role={}", tempUserId, properties.getListenerRole());

        Instant now = Instant.now();
        if (mode == RoomMode.OPEN) {
            return handleOpenJoin(roomCode, displayName, tempUserId, temporaryToken, mode, now);
        }
        return handleModeratedJoin(roomCode, displayName, tempUserId, temporaryToken, mode, now);
    }

    private JoinRoomResponse handleOpenJoin(String roomCode,
                                            String displayName,
                                            UUID tempUserId,
                                            String temporaryToken,
                                            RoomMode mode,
                                            Instant now) {
        Long result = stringRedisTemplate.execute(
                luaScripts.getOpenCheckIncrement(),
                List.of(LiveRoomRedisKeys.roomStatusKey(roomCode)),
                new Object[0]);
        if (result == null || result == 0L) {
            log.warn("JOIN_BLOCKED_FULL_LUA roomCode={}", roomCode);
            throw new BusinessException(LiveRoomErrorCode.ROOM_FULL,
                    "Room " + roomCode + " reached max participants during open check");
        }

        String memberJson = serializeMemberMetadata(displayName, properties.getListenerRole(), now);
        String membersKey = LiveRoomRedisKeys.roomMembersKey(roomCode);
        stringRedisTemplate.opsForHash().put(membersKey, tempUserId.toString(), memberJson);
        stringRedisTemplate.expire(membersKey, Duration.ofSeconds(properties.getMembersTtlSeconds()));

        log.info("OPEN_DIRECT_APPROVED roomCode={} listenerId={} displayName={}",
                roomCode, tempUserId, displayName);
        return new JoinRoomResponse(JOIN_STATUS_APPROVED, true, roomCode, mode, temporaryToken);
    }

    private JoinRoomResponse handleModeratedJoin(String roomCode,
                                                 String displayName,
                                                 UUID tempUserId,
                                                 String temporaryToken,
                                                 RoomMode mode,
                                                 Instant now) {
        String waitingKey = LiveRoomRedisKeys.roomWaitingKey(roomCode);
        String metaKey = LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode);
        Duration waitingTtl = Duration.ofSeconds(properties.getWaitingEntryTtlSeconds());

        stringRedisTemplate.opsForZSet().add(waitingKey, tempUserId.toString(), now.toEpochMilli());
        stringRedisTemplate.expire(waitingKey, waitingTtl);

        String metaJson = serializeWaitingMetadata(displayName, now);
        stringRedisTemplate.opsForHash().put(metaKey, tempUserId.toString(), metaJson);
        stringRedisTemplate.expire(metaKey, waitingTtl);

        log.info("WAITING_LIST_ADDED roomCode={} listenerId={} displayName={}",
                roomCode, tempUserId, displayName);

        notifier.notifyHostWaitingListChange(roomCode,
                new WaitingRequestNotification(tempUserId, displayName, now, "REQUESTED"));

        return new JoinRoomResponse(JOIN_STATUS_WAITING, false, roomCode, mode, temporaryToken);
    }

    private String serializeMemberMetadata(String displayName, String role, Instant joinedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("displayName", displayName);
        payload.put("role", role);
        payload.put("joinedAt", joinedAt.toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize member metadata", ex);
        }
    }

    private String serializeWaitingMetadata(String displayName, Instant requestedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("displayName", displayName);
        payload.put("requestedAt", requestedAt.toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize waiting metadata", ex);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
