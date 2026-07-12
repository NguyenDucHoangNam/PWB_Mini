package com.pwb.backend.modules.liveroom.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.ws.JoinResultMessage;
import com.pwb.backend.modules.liveroom.dto.ws.MembersSnapshotMessage;
import com.pwb.backend.modules.liveroom.dto.ws.WaitingRequestNotification;
import com.pwb.backend.modules.liveroom.service.LiveRoomLuaScripts;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompDisconnectListener {

    private static final String ROLE_LISTENER = "LISTENER";
    private static final String ROLE_USER_PRO = "USER_PRO";

    private final RoomLifecycleService roomLifecycleService;
    private final StringRedisTemplate stringRedisTemplate;
    private final LiveRoomLuaScripts luaScripts;
    private final LiveRoomMembershipNotifier notifier;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        try {
            String meta = stringRedisTemplate.opsForValue()
                    .get(LiveRoomRedisKeys.sessionListenerMetaKey(sessionId));
            String roomCode = stringRedisTemplate.opsForValue()
                    .get(LiveRoomRedisKeys.sessionRoomKey(sessionId));
            String listenerIdStr = stringRedisTemplate.opsForValue()
                    .get(LiveRoomRedisKeys.sessionListenerKey(sessionId));

            if (meta != null && meta.contains("role=" + ROLE_LISTENER)
                    && roomCode != null && listenerIdStr != null) {
                handleListenerDisconnect(roomCode, listenerIdStr);
            } else {
                roomLifecycleService.markHostDisconnected(sessionId);
            }
        } catch (Exception ex) {
            log.warn("WS_SESSION_DISCONNECT_FAILED sessionId={} reason={}", sessionId, ex.getMessage());
        } finally {
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionRoomKey(sessionId));
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionListenerMetaKey(sessionId));
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionListenerKey(sessionId));
        }
    }

    private void handleListenerDisconnect(String roomCode, String listenerIdStr) {
        UUID listenerId;
        try {
            listenerId = UUID.fromString(listenerIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("WS_LISTENER_DISCONNECT_INVALID_ID listenerIdStr={}", listenerIdStr);
            return;
        }
        try {
            Long touched = stringRedisTemplate.execute(
                    luaScripts.getListenerDisconnect(),
                    List.of(
                            LiveRoomRedisKeys.roomStatusKey(roomCode),
                            LiveRoomRedisKeys.roomMembersKey(roomCode),
                            LiveRoomRedisKeys.roomWaitingKey(roomCode),
                            LiveRoomRedisKeys.roomWaitingMetadataKey(roomCode)),
                    listenerIdStr);
            log.info("LISTENER_DISCONNECT_CLEANED roomCode={} listenerId={} touched={}",
                    roomCode, listenerId, touched);
        } catch (Exception ex) {
            log.warn("LISTENER_DISCONNECT_LUA_FAILED roomCode={} listenerId={} reason={}",
                    roomCode, listenerId, ex.getMessage());
        }

        notifier.notifyJoinResult(listenerId,
                new JoinResultMessage("DISCONNECTED", roomCode, Instant.now()));
        notifier.notifyHostWaitingListChange(roomCode,
                new WaitingRequestNotification(listenerId, null, Instant.now(), "DISCONNECTED"));
        broadcastMembersSnapshot(roomCode);
    }

    private void broadcastMembersSnapshot(String roomCode) {
        Map<Object, Object> rawMembers = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomMembersKey(roomCode));
        List<MembersSnapshotMessage.MemberEntry> members = new ArrayList<>();
        for (var entry : rawMembers.entrySet()) {
            MembersSnapshotMessage.MemberEntry parsed = toMemberEntry(
                    entry.getKey().toString(), entry.getValue().toString());
            if (parsed != null) {
                members.add(parsed);
            }
        }
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        int max = parseInt(statusHash.get("maxParticipants"));
        int current = parseInt(statusHash.get("currentParticipants"));
        MembersSnapshotMessage snapshot = new MembersSnapshotMessage(
                roomCode, members, current, max, Instant.now());
        notifier.notifyMembersChange(roomCode, snapshot);
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