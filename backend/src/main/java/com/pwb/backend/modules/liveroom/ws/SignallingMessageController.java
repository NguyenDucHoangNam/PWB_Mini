package com.pwb.backend.modules.liveroom.ws;

import java.nio.charset.StandardCharsets;
import org.springframework.messaging.Message;

import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.dto.ws.SignallingFrame;
import com.pwb.backend.modules.liveroom.service.SignallingRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SignallingMessageController {

    private static final Set<String> ALLOWED_TYPES = Set.of("offer", "answer", "candidate");

    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;

    private final SignallingRoutingService signallingRoutingService;

    @MessageMapping("/rooms/{roomCode}/signalling")
    public void onSignalling(@DestinationVariable String roomCode,
                             SignallingFrame frame,
                             Message<?> message) {
        try {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) {
                log.warn("SIGNALLING_DROPPED roomCode={} reason=missing_accessor", roomCode);
                return;
            }
            UUID senderId = resolveUserId(accessor);
            if (senderId == null) {
                log.warn("SIGNALLING_DROPPED roomCode={} reason=missing_user", roomCode);
                return;
            }
            if (frame == null || !isValidFrame(frame)) {
                log.warn("SIGNALLING_DROPPED roomCode={} senderId={} reason=invalid_frame",
                        roomCode, senderId);
                return;
            }
            signallingRoutingService.forward(roomCode, senderId, frame);
        } catch (Exception ex) {
            log.warn("SIGNALLING_UNEXPECTED_ERROR roomCode={} reason={}",
                    roomCode, ex.getMessage());
        }
    }

    private boolean isValidFrame(SignallingFrame frame) {
        if (frame.getReceiverId() == null) {
            return false;
        }
        if (frame.getType() == null) {
            return false;
        }
        if (!ALLOWED_TYPES.contains(frame.getType().toLowerCase())) {
            return false;
        }
        if (frame.getPayload() == null || frame.getPayload().isBlank()) {
            return false;
        }
        return frame.getPayload().getBytes(StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES;
    }

    private UUID resolveUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof JwtTypes.JwtAuthenticationToken jwt) {
            return jwt.getPrincipal().userId();
        }
        return null;
    }
}
