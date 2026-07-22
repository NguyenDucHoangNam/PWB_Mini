package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveRoomWebSocketController {

    private static final String SIGNAL_USER_BASE = "/queue/room/";
    private static final String SIGNAL_OFFER_SUFFIX = "/signal/offer";
    private static final String SIGNAL_ANSWER_SUFFIX = "/signal/answer";
    private static final String SIGNAL_ICE_SUFFIX = "/signal/ice";

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/room/{roomCode}/ping")
    public Map<String, Object> handlePing(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {

        log.debug("Ping received: roomCode={}, userId={}", roomCode, user == null ? "anonymous" : user.getId());

        return Map.of(
                "type", "PONG",
                "roomCode", roomCode,
                "echo", payload == null ? Map.of() : payload,
                "serverTime", Instant.now().toString()
        );
    }

    @MessageMapping("/room/{roomCode}/signal/offer")
    public void relayOffer(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {
        relaySignal(roomCode, user, payload, "OFFER", SIGNAL_OFFER_SUFFIX);
    }

    @MessageMapping("/room/{roomCode}/signal/answer")
    public void relayAnswer(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {
        relaySignal(roomCode, user, payload, "ANSWER", SIGNAL_ANSWER_SUFFIX);
    }

    @MessageMapping("/room/{roomCode}/signal/ice")
    public void relayIce(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {
        relaySignal(roomCode, user, payload, "ICE", SIGNAL_ICE_SUFFIX);
    }

    private void relaySignal(
            String roomCode,
            AuthenticatedUser user,
            Map<String, Object> payload,
            String type,
            String suffix) {
        if (user == null) {
            log.warn("Signal {} rejected: unauthenticated user, roomCode={}", type, roomCode);
            return;
        }
        if (payload == null) {
            log.warn("Signal {} rejected: empty payload, userId={}, roomCode={}", type, user.getId(), roomCode);
            return;
        }
        Object rawToUserId = payload.get("toUserId");
        if (!(rawToUserId instanceof String toUserIdStr) || toUserIdStr.isBlank()) {
            log.warn("Signal {} rejected: missing toUserId, userId={}, roomCode={}", type, user.getId(), roomCode);
            return;
        }
        UUID toUserId;
        try {
            toUserId = UUID.fromString(toUserIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("Signal {} rejected: bad toUserId={}, userId={}", type, toUserIdStr, user.getId());
            return;
        }
        if (toUserId.equals(user.getId())) {
            log.warn("Signal {} rejected: self-send, userId={}", type, user.getId());
            return;
        }

        Map<String, Object> forwarded = Map.of(
                "type", type,
                "roomCode", roomCode,
                "fromUserId", user.getId().toString(),
                "toUserId", toUserIdStr,
                "payload", payload.getOrDefault("payload", Map.of())
        );
        String destination = SIGNAL_USER_BASE + roomCode + suffix;
        try {
            messagingTemplate.convertAndSendToUser(toUserIdStr, destination, forwarded);
            log.debug("Relay {}: from={}, to={}, roomCode={}", type, user.getId(), toUserId, roomCode);
        } catch (Exception ex) {
            log.warn("Failed to relay {}: from={}, to={}, roomCode={}, error={}",
                    type, user.getId(), toUserId, roomCode, ex.getMessage());
        }
    }
}
