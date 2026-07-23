package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.exception.WsAuthException;
import com.pwb.liveroom.core.service.LiveRoomParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
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
    private final LiveRoomParticipantService participantService;

    @MessageMapping("/room/{roomCode}/ping")
    public Map<String, Object> handlePing(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal,
            Map<String, Object> payload) {

        log.debug("Ping received: roomCode={}, userId={}", roomCode, principal == null ? "anonymous" : principal.getName());

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
            Principal principal,
            Map<String, Object> payload) {
        relaySignal(roomCode, principal, payload, "OFFER", SIGNAL_OFFER_SUFFIX);
    }

    @MessageMapping("/room/{roomCode}/signal/answer")
    public void relayAnswer(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal,
            Map<String, Object> payload) {
        relaySignal(roomCode, principal, payload, "ANSWER", SIGNAL_ANSWER_SUFFIX);
    }

    @MessageMapping("/room/{roomCode}/signal/ice")
    public void relayIce(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal,
            Map<String, Object> payload) {
        relaySignal(roomCode, principal, payload, "ICE", SIGNAL_ICE_SUFFIX);
    }

    @MessageMapping("/room/{roomCode}/state/request")
    public void handleRoomStateRequest(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal) {
        if (principal == null) {
            log.warn("ROOM_STATE request rejected: unauthenticated user, roomCode={}", roomCode);
            return;
        }
        UUID userId = parseUserId(principal);
        if (userId == null) {
            log.warn("ROOM_STATE request rejected: bad principal name={}", principal.getName());
            return;
        }
        if (!participantService.isActiveParticipant(roomCode, userId)) {
            log.warn("ROOM_STATE request rejected: user not in room, roomCode={}, userId={}", roomCode, userId);
            return;
        }
        log.debug("ROOM_STATE request: roomCode={}, userId={}", roomCode, userId);
        participantService.requestRoomState(userId, roomCode);
    }

    @org.springframework.messaging.handler.annotation.MessageExceptionHandler(WsAuthException.class)
    public Map<String, Object> handleWsAuthException(WsAuthException ex) {
        log.warn("WS auth exception: code={}, message={}", ex.getCode(), ex.getMessage());
        return Map.of(
                "type", "ERROR",
                "code", ex.getCode(),
                "message", ex.getMessage()
        );
    }

    private void relaySignal(
            String roomCode,
            Principal principal,
            Map<String, Object> payload,
            String type,
            String suffix) {
        if (principal == null) {
            log.warn("Signal {} rejected: unauthenticated user, roomCode={}", type, roomCode);
            return;
        }
        UUID userId = parseUserId(principal);
        if (userId == null) {
            log.warn("Signal {} rejected: bad principal name={}, roomCode={}", type, principal.getName(), roomCode);
            return;
        }
        if (payload == null) {
            log.warn("Signal {} rejected: empty payload, userId={}, roomCode={}", type, userId, roomCode);
            return;
        }
        Object rawToUserId = payload.get("toUserId");
        if (!(rawToUserId instanceof String toUserIdStr) || toUserIdStr.isBlank()) {
            log.warn("Signal {} rejected: missing toUserId, userId={}, roomCode={}", type, userId, roomCode);
            return;
        }
        UUID toUserId;
        try {
            toUserId = UUID.fromString(toUserIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("Signal {} rejected: bad toUserId={}, userId={}", type, toUserIdStr, userId);
            return;
        }
        if (toUserId.equals(userId)) {
            log.warn("Signal {} rejected: self-send, userId={}", type, userId);
            return;
        }
        if (!participantService.isActiveParticipant(roomCode, userId)) {
            throw new WsAuthException("WS_001", "User is not an active participant of this room");
        }
        if (!participantService.isActiveParticipant(roomCode, toUserId)) {
            throw new WsAuthException("WS_002", "Target user is not an active participant of this room");
        }

        Map<String, Object> forwarded = Map.of(
                "type", type,
                "roomCode", roomCode,
                "fromUserId", userId.toString(),
                "toUserId", toUserIdStr,
                "payload", payload.getOrDefault("payload", Map.of())
        );
        String destination = SIGNAL_USER_BASE + roomCode + suffix;
        try {
            messagingTemplate.convertAndSendToUser(toUserIdStr, destination, forwarded);
            log.debug("Relay {}: from={}, to={}, roomCode={}", type, userId, toUserId, roomCode);
        } catch (Exception ex) {
            log.warn("Failed to relay {}: from={}, to={}, roomCode={}, error={}",
                    type, userId, toUserId, roomCode, ex.getMessage());
        }
    }

    private UUID parseUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
