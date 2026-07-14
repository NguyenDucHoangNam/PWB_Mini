package com.pwb.backend.modules.liveroom.ws;

import org.springframework.messaging.Message;

import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.dto.request.PlaybackSyncCommand;
import com.pwb.backend.modules.liveroom.service.PlaybackSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PlaybackSyncMessageController {

    private final PlaybackSyncService playbackSyncService;

    @MessageMapping("/rooms/{roomCode}/sync-state")
    public void onSyncState(@DestinationVariable String roomCode,
                            PlaybackSyncCommand command,
                            Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            log.warn("WS_SYNC_ERROR roomCode={} reason=missing_accessor", roomCode);
            return;
        }
        Boolean isController = accessor.getSessionAttributes() == null
                ? null
                : (Boolean) accessor.getSessionAttributes().get(StompAuthChannelInterceptor.SESSION_ATTR_IS_CONTROLLER);
        UUID userId = resolveUserId(accessor);

        if (!Boolean.TRUE.equals(isController)) {
            log.warn("PLAYBACK_UNAUTHORIZED_ATTEMPT roomCode={} userId={} action={}",
                    roomCode, userId, command == null ? null : command.action());
            return;
        }
        if (userId == null) {
            log.warn("WS_SYNC_ERROR roomCode={} reason=missing_user", roomCode);
            return;
        }
        try {
            playbackSyncService.handleSyncCommand(roomCode, userId, command);
        } catch (Exception ex) {
            log.warn("WS_SYNC_ERROR roomCode={} userId={} reason={}", roomCode, userId, ex.getMessage());
        }
    }

    private UUID resolveUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof JwtTypes.JwtAuthenticationToken jwt) {
            return jwt.getPrincipal().userId();
        }
        return null;
    }
}
