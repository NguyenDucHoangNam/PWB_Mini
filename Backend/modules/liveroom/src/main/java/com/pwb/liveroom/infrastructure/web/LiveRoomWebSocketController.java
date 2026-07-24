package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.exception.WsAuthException;
import com.pwb.liveroom.core.service.LiveRoomParticipantService;
import com.pwb.liveroom.core.service.LiveRoomPlaybackService;
import com.pwb.liveroom.infrastructure.realtime.PlaybackRateLimiter;
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
    private final LiveRoomPlaybackService playbackService;
    private final PlaybackRateLimiter playbackRateLimiter;

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

    @MessageMapping("/room/{roomCode}/playback/play")
    public void handlePlaybackPlay(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_PLAY");
        if (userId == null) {
            return;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "play"))) {
            log.warn("Playback play rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        try {
            playbackService.play(userId, roomCode);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback play rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    @MessageMapping("/room/{roomCode}/playback/pause")
    public void handlePlaybackPause(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_PAUSE");
        if (userId == null) {
            return;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "pause"))) {
            log.warn("Playback pause rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        try {
            playbackService.pause(userId, roomCode);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback pause rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    @MessageMapping("/room/{roomCode}/playback/state/request")
    public void handlePlaybackStateRequest(
            @DestinationVariable("roomCode") String roomCode,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_STATE_REQUEST");
        if (userId == null) {
            return;
        }
        try {
            playbackService.requestPlaybackState(userId, roomCode);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback state request rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    @MessageMapping("/room/{roomCode}/playback/seek")
    public void handlePlaybackSeek(
            @DestinationVariable("roomCode") String roomCode,
            Map<String, Object> payload,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_SEEK");
        if (userId == null) {
            return;
        }
        long direction = 1L;
        Object directionObj = payload == null ? null : payload.get("direction");
        if (directionObj instanceof Number num) {
            direction = num.longValue() >= 0 ? 1L : -1L;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "seek"))) {
            log.warn("Playback seek rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        try {
            playbackService.seek(userId, roomCode, direction);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback seek rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    @MessageMapping("/room/{roomCode}/playback/rate")
    public void handlePlaybackRate(
            @DestinationVariable("roomCode") String roomCode,
            Map<String, Object> payload,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_RATE");
        if (userId == null) {
            return;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "rate"))) {
            log.warn("Playback rate rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        try {
            java.math.BigDecimal rate = new java.math.BigDecimal(String.valueOf(payload == null ? "1.00" : payload.get("rate")));
            playbackService.setRate(userId, roomCode, rate);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback rate rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        } catch (NumberFormatException ex) {
            log.warn("Playback rate invalid payload: roomCode={}, userId={}", roomCode, userId);
        }
    }

    @MessageMapping("/room/{roomCode}/playback/loop")
    public void handlePlaybackLoop(
            @DestinationVariable("roomCode") String roomCode,
            Map<String, Object> payload,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_LOOP");
        if (userId == null) {
            return;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "loop"))) {
            log.warn("Playback loop rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        String mode = payload == null ? "OFF" : String.valueOf(payload.get("mode"));
        try {
            playbackService.setLoop(userId, roomCode, mode);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback loop rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    @MessageMapping("/room/{roomCode}/playback/shuffle")
    public void handlePlaybackShuffle(
            @DestinationVariable("roomCode") String roomCode,
            Map<String, Object> payload,
            Principal principal) {
        UUID userId = requireActiveParticipant(roomCode, principal, "PLAYBACK_SHUFFLE");
        if (userId == null) {
            return;
        }
        if (!playbackRateLimiter.tryAcquire(rateLimitKey(userId, roomCode, "shuffle"))) {
            log.warn("Playback shuffle rate-limited: roomCode={}, userId={}", roomCode, userId);
            return;
        }
        boolean enabled = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
        try {
            playbackService.setShuffle(userId, roomCode, enabled);
        } catch (com.pwb.backend.exception.BusinessException ex) {
            log.warn("Playback shuffle rejected: roomCode={}, userId={}, code={}", roomCode, userId, ex.getErrorCode().getCode());
        }
    }

    private UUID requireActiveParticipant(String roomCode, Principal principal, String action) {
        if (principal == null) {
            log.warn("{} rejected: unauthenticated user, roomCode={}", action, roomCode);
            return null;
        }
        UUID userId = parseUserId(principal);
        if (userId == null) {
            log.warn("{} rejected: bad principal name={}", action, principal.getName());
            return null;
        }
        if (!participantService.isActiveParticipant(roomCode, userId)) {
            log.warn("{} rejected: user not in room, roomCode={}, userId={}", action, roomCode, userId);
            return null;
        }
        return userId;
    }

    private static String rateLimitKey(UUID userId, String roomCode, String action) {
        return userId + "|" + roomCode + "|" + action;
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
        Object rawSignalPayload = payload.get("payload");
        Map<String, Object> signalPayload = new java.util.HashMap<>();
        if (rawSignalPayload instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() instanceof String key && e.getValue() != null) {
                    signalPayload.put(key, e.getValue());
                }
            }
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
                "payload", signalPayload
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
