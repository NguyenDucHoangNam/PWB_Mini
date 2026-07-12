package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompDisconnectListener {

    private final RoomLifecycleService roomLifecycleService;
    private final StringRedisTemplate stringRedisTemplate;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        try {
            roomLifecycleService.markHostDisconnected(sessionId);
        } catch (Exception ex) {
            log.warn("WS_SESSION_DISCONNECT_FAILED sessionId={} reason={}", sessionId, ex.getMessage());
        } finally {
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionRoomKey(sessionId));
        }
    }
}