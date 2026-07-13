package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.modules.liveroom.dto.ws.RoomEvictionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LocalRoomSessionRegistry {

    public static final String ROOM_ATTR = "liveroom.roomCode";
    public static final String USER_ATTR = "liveroom.userId";
    public static final String FORCE_CLOSE_USER_DESTINATION = "/queue/rooms/force-close";

    private final SimpUserRegistry simpUserRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Set<UUID>> sessionsByRoom = new ConcurrentHashMap<>();

    public LocalRoomSessionRegistry(@Lazy SimpUserRegistry simpUserRegistry,
                                    @Lazy SimpMessagingTemplate messagingTemplate) {
        this.simpUserRegistry = simpUserRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    public void bind(String roomCode, UUID userId, String stompSessionId) {
        if (roomCode == null || roomCode.isBlank() || userId == null || stompSessionId == null) {
            return;
        }
        sessionsByRoom.computeIfAbsent(roomCode, key -> ConcurrentHashMap.newKeySet()).add(userId);
        log.debug("ROOM_SESSION_BOUND roomCode={} userId={} stompSessionId={}", roomCode, userId, stompSessionId);
    }

    public void unbind(String roomCode, UUID userId) {
        if (roomCode == null || roomCode.isBlank() || userId == null) {
            return;
        }
        Set<UUID> users = sessionsByRoom.get(roomCode);
        if (users == null) {
            return;
        }
        users.remove(userId);
        if (users.isEmpty()) {
            sessionsByRoom.remove(roomCode, users);
        }
    }

    public int evictRoom(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return 0;
        }
        Set<UUID> users = sessionsByRoom.remove(roomCode);
        if (users == null || users.isEmpty()) {
            log.info("LOCAL_SESSIONS_EVICTED roomCode={} closed=0", roomCode);
            return 0;
        }
        int notified = 0;
        for (UUID userId : users) {
            try {
                messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        FORCE_CLOSE_USER_DESTINATION,
                        new RoomEvictionEvent(RoomEvictionEvent.EVENT_FORCE_CLOSE_ROOM_SESSIONS, roomCode));
                notified++;
            } catch (Exception ex) {
                log.warn("FORCE_CLOSE_PUBLISH_FAILED roomCode={} userId={} reason={}",
                        roomCode, userId, ex.getMessage());
            }
        }
        log.info("LOCAL_SESSIONS_EVICTED roomCode={} notified={} knownLocalUsers={}",
                roomCode, notified, users.size());
        return notified;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null || simpUserRegistry == null) {
            return;
        }
        for (SimpUser user : simpUserRegistry.getUsers()) {
            for (SimpSession session : user.getSessions()) {
                if (sessionId.equals(session.getId())) {
                    sessionsByRoom.entrySet().removeIf(entry -> {
                        entry.getValue().remove(UUID.fromString(user.getName()));
                        return entry.getValue().isEmpty();
                    });
                    return;
                }
            }
        }
    }
}
