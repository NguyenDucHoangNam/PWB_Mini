package com.pwb.backend.modules.liveroom.ws;

import io.jsonwebtoken.JwtException;
import org.springframework.security.access.AccessDeniedException;

import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.common.security.jwt.JwtSigner;
import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String DESTINATION_PREFIX = "/topic/rooms/";
    private static final String HEADER_ROOM_CODE = "X-Room-Code";

    private static final String ROLE_USER_PRO = "USER_PRO";
    private static final String ROLE_LISTENER = "LISTENER";

    public static final String SESSION_ATTR_IS_CONTROLLER = "isController";

    private final JwtSigner jwtSigner;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final RoomLifecycleService roomLifecycleService;
    private final LiveRoomProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final LocalRoomSessionRegistry localRoomSessionRegistry;

    public StompAuthChannelInterceptor(JwtSigner jwtSigner,
                                       BearerTokenExtractor bearerTokenExtractor,
                                       @Lazy RoomLifecycleService roomLifecycleService,
                                       LiveRoomProperties properties,
                                       StringRedisTemplate stringRedisTemplate,
                                       LocalRoomSessionRegistry localRoomSessionRegistry) {
        this.jwtSigner = jwtSigner;
        this.bearerTokenExtractor = bearerTokenExtractor;
        this.roomLifecycleService = roomLifecycleService;
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.localRoomSessionRegistry = localRoomSessionRegistry;
    }

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
            throw new AccessDeniedException("Missing Authorization header");
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
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(SESSION_ATTR_IS_CONTROLLER, ROLE_USER_PRO.equals(role));
            }
            if (ROLE_LISTENER.equals(role)) {
                bindListenerSession(sessionId, user.userId(), roomCode);
            } else if (ROLE_USER_PRO.equals(role)) {
                bindHostSession(sessionId, user.userId(), roomCode);
            } else {
                log.warn("WS_CONNECT_UNKNOWN_ROLE role={} userId={}", role, user.userId());
            }
            if (roomCode != null && !roomCode.isBlank()) {
                registerLocalRoomSession(accessor, roomCode);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("WS_CONNECT_REJECTED reason=invalid_token sessionId={} error={}",
                    accessor.getSessionId(), ex.getMessage());
            throw new AccessDeniedException("Invalid JWT");
        }
    }

    private void bindHostSession(String sessionId, UUID userId, String roomCode) {
        if (sessionId == null) {
            return;
        }
        if (roomCode != null && !roomCode.isBlank()) {
            try {
                roomLifecycleService.activateRoomPhase2(roomCode);
                roomLifecycleService.markHostActive(roomCode);
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
        } else {
            try {
                roomLifecycleService.cancelEmptyRoomCleanup(roomCode);
            } catch (Exception ex) {
                log.warn("WS_CONNECT_LISTENER_CANCEL_CLEANUP_FAILED roomCode={} reason={}",
                        roomCode, ex.getMessage());
            }
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

    private void registerLocalRoomSession(StompHeaderAccessor accessor, String roomCode) {
        if (accessor.getSessionAttributes() == null) {
            return;
        }
        accessor.getSessionAttributes().put(LocalRoomSessionRegistry.ROOM_ATTR, roomCode);
        try {
            UUID userId = resolveUserId(accessor);
            if (userId != null) {
                accessor.getSessionAttributes().put(LocalRoomSessionRegistry.USER_ATTR, userId.toString());
                localRoomSessionRegistry.bind(roomCode, userId, accessor.getSessionId());
                if (!ROLE_LISTENER.equals(currentRole(accessor))) {
                    roomLifecycleService.markHostActive(roomCode);
                } else {
                    roomLifecycleService.cancelEmptyRoomCleanup(roomCode);
                }
            }
        } catch (Exception ex) {
            log.warn("LOCAL_SESSION_BIND_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
        }
    }

    private String currentRole(StompHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() == null) {
            return null;
        }
        Object meta = accessor.getSessionAttributes().get(SESSION_ATTR_IS_CONTROLLER);
        if (meta == null) {
            return null;
        }
        return Boolean.TRUE.equals(meta) ? ROLE_USER_PRO : ROLE_LISTENER;
    }

    UUID resolveUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof JwtTypes.JwtAuthenticationToken token) {
            return token.getPrincipal().userId();
        }
        return null;
    }
}
