package com.pwb.backend.modules.liveroom.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.response.WaitingListResponse;
import com.pwb.backend.modules.liveroom.dto.response.WaitingMemberResponse;
import com.pwb.backend.modules.liveroom.dto.ws.JoinResultMessage;
import com.pwb.backend.modules.liveroom.dto.ws.MembersSnapshotMessage;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.service.LiveRoomLuaScripts;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.WaitingListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingListServiceImpl implements WaitingListService {

    private static final String ROOM_STATUS_FIELD_HOST_ID = "hostId";
    private static final String ROOM_STATUS_FIELD_STATUS = "status";
    private static final String ROOM_STATUS_FIELD_MODE = "mode";
    private static final String ROOM_STATUS_FIELD_MAX_PARTICIPANTS = "maxParticipants";
    private static final String ROOM_STATUS_FIELD_CURRENT_PARTICIPANTS = "currentParticipants";

    private final StringRedisTemplate stringRedisTemplate;
    private final LiveRoomLuaScripts luaScripts;
    private final LiveRoomProperties properties;
    private final ObjectMapper objectMapper;
    private final LiveRoomMembershipNotifier notifier;

    @Override
    public WaitingListResponse listWaiting(String roomCode, UUID hostId) {
        assertRoomHost(roomCode, hostId);
        int removed = lazyCleanupWaitingList(roomCode);
        if (removed > 0) {
            log.warn("LAZY_CLEANUP_EXECUTED roomCode={} removedCount={}", roomCode, removed);
        }

        String waitingKey = LiveRoomRedisKeys.roomWaitingKey(roomCode);
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().rangeWithScores(waitingKey, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return buildResponse(roomCode, List.of());
        }

        String metaKey = LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode);
        List<Object> userIds = new ArrayList<>();
        for (var tuple : tuples) {
            userIds.add(tuple.getValue());
        }
        List<Object> metaValues = stringRedisTemplate.opsForHash().multiGet(metaKey, userIds);

        List<WaitingMemberResponse> members = new ArrayList<>();
        int idx = 0;
        for (var tuple : tuples) {
            String userIdStr = tuple.getValue();
            Double score = tuple.getScore();
            String meta = idx < metaValues.size() ? asString(metaValues.get(idx)) : null;
            members.add(toWaitingMember(userIdStr, score, meta));
            idx++;
        }
        return buildResponse(roomCode, members);
    }

    @Override
    public void approve(String roomCode, UUID hostId, UUID listenerId) {
        assertRoomHost(roomCode, hostId);
        String memberJson = serializeMemberJson(
                lookupDisplayNameFromWaiting(roomCode, listenerId),
                properties.getListenerRole(),
                Instant.now());
        Long result = stringRedisTemplate.execute(
                luaScripts.getApproveStateMigration(),
                List.of(
                        LiveRoomRedisKeys.roomStatusKey(roomCode),
                        LiveRoomRedisKeys.roomWaitingKey(roomCode),
                        LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode),
                        LiveRoomRedisKeys.roomMembersKey(roomCode)),
                listenerId.toString(),
                memberJson);
        if (result == null || result == 0L) {
            boolean inWaiting = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForZSet().score(
                            LiveRoomRedisKeys.roomWaitingKey(roomCode), listenerId.toString()) != null);
            if (!inWaiting) {
                log.warn("APPROVE_NOT_IN_WAITING roomCode={} listenerId={}", roomCode, listenerId);
                throw new BusinessException(LiveRoomErrorCode.LISTENER_NOT_IN_WAITING);
            }
            log.warn("APPROVE_BLOCKED_FULL roomCode={} listenerId={}", roomCode, listenerId);
            throw new BusinessException(LiveRoomErrorCode.ROOM_FULL);
        }
        stringRedisTemplate.expire(LiveRoomRedisKeys.roomMembersKey(roomCode),
                Duration.ofSeconds(properties.getMembersTtlSeconds()));
        log.info("LISTENER_APPROVED roomCode={} listenerId={} hostId={}", roomCode, listenerId, hostId);
        notifier.notifyJoinResult(listenerId,
                new JoinResultMessage("APPROVED", roomCode, Instant.now()));
        notifyMembersSnapshot(roomCode);
    }

    @Override
    public void reject(String roomCode, UUID hostId, UUID listenerId) {
        assertRoomHost(roomCode, hostId);
        Long removed = stringRedisTemplate.opsForZSet()
                .remove(LiveRoomRedisKeys.roomWaitingKey(roomCode), listenerId.toString());
        stringRedisTemplate.opsForHash()
                .delete(LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode), listenerId.toString());
        if (removed == null || removed == 0L) {
            log.warn("REJECT_NOT_IN_WAITING roomCode={} listenerId={}", roomCode, listenerId);
            throw new BusinessException(LiveRoomErrorCode.LISTENER_NOT_IN_WAITING);
        }
        log.info("LISTENER_REJECTED roomCode={} listenerId={} hostId={}", roomCode, listenerId, hostId);
        notifier.notifyJoinResult(listenerId,
                new JoinResultMessage("REJECTED", roomCode, Instant.now()));
    }

    @Override
    public void kick(String roomCode, UUID hostId, UUID listenerId) {
        assertRoomHost(roomCode, hostId);
        Long result = stringRedisTemplate.execute(
                luaScripts.getKickListener(),
                List.of(
                        LiveRoomRedisKeys.roomStatusKey(roomCode),
                        LiveRoomRedisKeys.roomMembersKey(roomCode),
                        LiveRoomRedisKeys.roomWaitingKey(roomCode),
                        LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode)),
                listenerId.toString());
        if (result == null || result == 0L) {
            log.warn("KICK_NOT_A_MEMBER roomCode={} listenerId={}", roomCode, listenerId);
            throw new BusinessException(LiveRoomErrorCode.LISTENER_NOT_A_MEMBER);
        }
        log.info("LISTENER_KICKED roomCode={} listenerId={} hostId={}", roomCode, listenerId, hostId);
        notifier.notifyJoinResult(listenerId,
                new JoinResultMessage("KICKED", roomCode, Instant.now()));
        notifyMembersSnapshot(roomCode);
    }

    @Override
    public void notifyMembersSnapshot(String roomCode) {
        String membersKey = LiveRoomRedisKeys.roomMembersKey(roomCode);
        Map<Object, Object> rawMembers = stringRedisTemplate.opsForHash().entries(membersKey);
        List<MembersSnapshotMessage.MemberEntry> members = new ArrayList<>();
        for (var entry : rawMembers.entrySet()) {
            members.add(toMemberEntry(entry.getKey().toString(), entry.getValue().toString()));
        }
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        int max = parseInt(statusHash.get(ROOM_STATUS_FIELD_MAX_PARTICIPANTS));
        int current = parseInt(statusHash.get(ROOM_STATUS_FIELD_CURRENT_PARTICIPANTS));
        MembersSnapshotMessage snapshot = new MembersSnapshotMessage(
                roomCode, members, current, max, Instant.now());
        notifier.notifyMembersChange(roomCode, snapshot);
    }

    private int lazyCleanupWaitingList(String roomCode) {
        String waitingKey = LiveRoomRedisKeys.roomWaitingKey(roomCode);
        long maxAgeMillis = properties.getWaitingEntryMaxAgeSeconds() * 1000L;
        long cutoff = Instant.now().minusMillis(maxAgeMillis).toEpochMilli();
        Long removed = stringRedisTemplate.opsForZSet()
                .removeRangeByScore(waitingKey, Double.NEGATIVE_INFINITY, (double) cutoff);
        if (removed != null && removed > 0) {
            cleanupOrphanMetadata(roomCode);
        }
        return removed == null ? 0 : removed.intValue();
    }

    private void cleanupOrphanMetadata(String roomCode) {
        String metaKey = LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode);
        Set<String> currentWaiting = stringRedisTemplate.opsForZSet()
                .range(waitingKey(roomCode), 0, -1);
        Set<Object> metaKeys = stringRedisTemplate.opsForHash().keys(metaKey);
        if (metaKeys == null) {
            return;
        }
        for (Object metaKey_ : metaKeys) {
            if (currentWaiting == null || !currentWaiting.contains(metaKey_.toString())) {
                stringRedisTemplate.opsForHash().delete(metaKey, metaKey_);
            }
        }
    }

    private String waitingKey(String roomCode) {
        return LiveRoomRedisKeys.roomWaitingKey(roomCode);
    }

    private void assertRoomHost(String roomCode, UUID hostId) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    java.util.Map.<String, Object>of("roomCode", roomCode));
        }
        String status = asString(statusHash.get(ROOM_STATUS_FIELD_STATUS));
        String owner = asString(statusHash.get(ROOM_STATUS_FIELD_HOST_ID));
        if (!RoomStatus.ACTIVE.name().equals(status) && !RoomStatus.INACTIVE_HOST.name().equals(status)) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    java.util.Map.<String, Object>of("roomCode", roomCode));
        }
        if (owner == null || !owner.equals(hostId.toString())) {
            log.warn("HOST_FORBIDDEN roomCode={} callerId={} ownerId={}", roomCode, hostId, owner);
            throw new BusinessException(LiveRoomErrorCode.FORBIDDEN_NOT_HOST,
                    null,
                    null,
                    java.util.Map.<String, Object>of("roomCode", roomCode));
        }
    }

    private WaitingMemberResponse toWaitingMember(String userIdStr, Double score, String metaJson) {
        UUID listenerId;
        try {
            listenerId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Instant requestedAt = score == null ? null : Instant.ofEpochMilli(score.longValue());
        String displayName = extractField(metaJson, "displayName");
        if (requestedAt == null) {
            String requestedAtStr = extractField(metaJson, "requestedAt");
            if (requestedAtStr != null) {
                try {
                    requestedAt = Instant.parse(requestedAtStr);
                } catch (Exception ignored) {
                    requestedAt = Instant.now();
                }
            } else {
                requestedAt = Instant.now();
            }
        }
        return new WaitingMemberResponse(listenerId, displayName, requestedAt);
    }

    private MembersSnapshotMessage.MemberEntry toMemberEntry(String userIdStr, String metaJson) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String displayName = extractField(metaJson, "displayName");
        String role = extractField(metaJson, "role");
        String joinedAtStr = extractField(metaJson, "joinedAt");
        Instant joinedAt = Instant.now();
        if (joinedAtStr != null) {
            try {
                joinedAt = Instant.parse(joinedAtStr);
            } catch (Exception ignored) {
            }
        }
        return new MembersSnapshotMessage.MemberEntry(userId, displayName, role, joinedAt);
    }

    private String serializeMemberJson(String displayName, String role, Instant joinedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("displayName", displayName == null ? "" : displayName);
        payload.put("role", role);
        payload.put("joinedAt", joinedAt.toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize member metadata", ex);
        }
    }

    private String lookupDisplayNameFromWaiting(String roomCode, UUID listenerId) {
        Object meta = stringRedisTemplate.opsForHash()
                .get(LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode), listenerId.toString());
        return extractField(asString(meta), "displayName");
    }

    private WaitingListResponse buildResponse(String roomCode, List<WaitingMemberResponse> members) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        int max = parseInt(statusHash.get(ROOM_STATUS_FIELD_MAX_PARTICIPANTS));
        int current = parseInt(statusHash.get(ROOM_STATUS_FIELD_CURRENT_PARTICIPANTS));
        List<WaitingMemberResponse> filtered = new ArrayList<>();
        for (WaitingMemberResponse m : members) {
            if (m != null) {
                filtered.add(m);
            }
        }
        return new WaitingListResponse(filtered, filtered.size(), current, max);
    }

    private String extractField(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(json);
            var found = node.get(field);
            return found == null ? null : found.asText();
        } catch (Exception ex) {
            return null;
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