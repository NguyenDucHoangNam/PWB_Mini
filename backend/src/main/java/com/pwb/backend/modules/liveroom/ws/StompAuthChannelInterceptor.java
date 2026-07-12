package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.common.security.jwt.JwtSigner;
import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String DESTINATION_PREFIX = "/topic/rooms/";
    private static final String HEADER_ROOM_CODE = "X-Room-Code";

    private static final String ROLE_USER_PRO = "USER_PRO";
    private static final String ROLE_LISTENER = "LISTENER";

    private final JwtSigner jwtSigner;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final RoomLifecycleService roomLifecycleService;
    private final LiveRoomProperties properties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case DISCONNECT -> handleDisconnect(accessor);
            default -> {
            }
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String token = bearerTokenExtractor.extract(authHeader);
        if (token == null) {
            log.warn("WS_CONNECT_REJECTED reason=missing_token sessionId={}", accessor.getSessionId());
            throw new org.springframework.security.access.AccessDeniedException("Missing Authorization header");
        }
        try {
            JwtTypes.AuthenticatedUser user = jwtSigner.verifyAndExtract(token);
            AbstractAuthenticationToken authentication = new JwtTypes.JwtAuthenticationToken(user);
            accessor.setUser(authentication);
            String role = user.role();
            String roomCode = accessor.getFirstNativeHeader(HEADER_ROOM_CODE);
            if (roomCode == null || roomCode.isBlank()) {
                roomCode = accessor.getFirstNativeHeader("roomCode");
            }
            String sessionId = accessor.getSessionId();
            if (ROLE_LISTENER.equals(role)) {
                bindListenerSession(sessionId, user.userId(), roomCode);
            } else if (ROLE_USER_PRO.equals(role)) {
                bindHostSession(sessionId, user.userId(), roomCode);
            } else {
                log.warn("WS_CONNECT_UNKNOWN_ROLE role={} userId={}", role, user.userId());
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            log.warn("WS_CONNECT_REJECTED reason=invalid_token sessionId={} error={}",
                    accessor.getSessionId(), ex.getMessage());
            throw new org.springframework.security.access.AccessDeniedException("Invalid JWT");
        }
    }

    private void bindHostSession(String sessionId, UUID userId, String roomCode) {
        if (sessionId == null) {
            return;
        }
        if (roomCode != null && !roomCode.isBlank()) {
            try {
                roomLifecycleService.activateRoomPhase2(roomCode);
            } catch (Exception ex) {
                log.warn("WS_CONNECT_PHASE2_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
            }
        } else {
            log.info("WS_CONNECT_HOST_NO_ROOM_CODE userId={} sessionId={}", userId, sessionId);
        }
        writeSessionMeta(sessionId, userId.toString(), roomCode, ROLE_USER_PRO);
    }

    private void bindListenerSession(String sessionId, UUID userId, String roomCode) {
        if (sessionId == null) {
            return;
        }
        if (roomCode == null || roomCode.isBlank()) {
            log.warn("WS_CONNECT_LISTENER_NO_ROOM_CODE userId={} sessionId={}", userId, sessionId);
        }
        writeSessionMeta(sessionId, userId.toString(), roomCode, ROLE_LISTENER);
        stringRedisTemplate.opsForValue().set(
                LiveRoomRedisKeys.sessionListenerKey(sessionId),
                userId.toString(),
                Duration.ofSeconds(properties.getTemporaryTokenTtlSeconds()));
    }

    private void writeSessionMeta(String sessionId, String userIdStr, String roomCode, String role) {
        StringBuilder meta = new StringBuilder();
        meta.append("userId=").append(userIdStr == null ? "" : userIdStr).append('|');
        meta.append("role=").append(role).append('|');
        meta.append("roomCode=").append(roomCode == null ? "" : roomCode);
        stringRedisTemplate.opsForValue().set(
                LiveRoomRedisKeys.sessionListenerMetaKey(sessionId),
                meta.toString(),
                Duration.ofSeconds(properties.getTemporaryTokenTtlSeconds()));
        if (roomCode != null && !roomCode.isBlank()) {
            stringRedisTemplate.opsForValue().set(
                    LiveRoomRedisKeys.sessionRoomKey(sessionId),
                    roomCode,
                    Duration.ofSeconds(properties.getTemporaryTokenTtlSeconds()));
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(DESTINATION_PREFIX)) {
            return;
        }
        log.debug("WS_SUBSCRIBE sessionId={} destination={}", accessor.getSessionId(), destination);
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        try {
            roomLifecycleService.markHostDisconnected(sessionId);
        } catch (Exception ex) {
            log.warn("WS_DISCONNECT_HANDLER_FAILED sessionId={} reason={}", sessionId, ex.getMessage());
        } finally {
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionRoomKey(sessionId));
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionListenerMetaKey(sessionId));
            stringRedisTemplate.delete(LiveRoomRedisKeys.sessionListenerKey(sessionId));
        }
    }

    UUID resolveUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof JwtTypes.JwtAuthenticationToken token) {
            return token.getPrincipal().userId();
        }
        return null;
    }
}