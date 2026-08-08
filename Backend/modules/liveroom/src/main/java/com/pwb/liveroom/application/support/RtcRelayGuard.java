package com.pwb.liveroom.application.support;

import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RtcRelayGuard {

    private static final int PRUNE_THRESHOLD = 512;

    private final RoomSessions roomSessions;
    private final LiveroomConfig config;

    private final Map<String, Long> verifiedAt = new ConcurrentHashMap<>();

    public void verify(UUID roomId, UUID actorId, UUID targetUserId) {
        String key = roomId + "|" + actorId + "|" + targetUserId;
        long ttl = config.getRealtime().getRelayVerifyTtl().toMillis();
        long now = System.currentTimeMillis();

        Long checkedAt = verifiedAt.get(key);
        if (checkedAt != null && now - checkedAt < ttl) {
            return;
        }

        roomSessions.requireRelayAllowed(roomId, actorId, targetUserId);

        if (verifiedAt.size() >= PRUNE_THRESHOLD) {
            verifiedAt.values().removeIf(stamp -> now - stamp >= ttl);
        }
        verifiedAt.put(key, now);
    }
}