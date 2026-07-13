package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.dto.ws.ChatFrame;
import com.pwb.backend.modules.liveroom.service.ChatBroadcastService;
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
public class ChatMessageController {

    private final ChatBroadcastService chatBroadcastService;

    @MessageMapping("/rooms/{roomCode}/chat")
    public void onChat(@DestinationVariable String roomCode,
                       ChatFrame frame,
                       org.springframework.messaging.Message<?> message) {
        try {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) {
                log.warn("CHAT_DROPPED roomCode={} reason=missing_accessor", roomCode);
                return;
            }
            UUID senderId = resolveUserId(accessor);
            if (senderId == null) {
                log.warn("CHAT_DROPPED roomCode={} reason=missing_user", roomCode);
                return;
            }
            chatBroadcastService.broadcast(roomCode, senderId, frame);
        } catch (Exception ex) {
            log.warn("CHAT_UNEXPECTED_ERROR roomCode={} reason={}",
                    roomCode, ex.getMessage());
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
