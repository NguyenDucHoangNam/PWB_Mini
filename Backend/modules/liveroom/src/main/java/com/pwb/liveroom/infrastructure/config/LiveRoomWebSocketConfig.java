package com.pwb.liveroom.infrastructure.config;

import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import com.pwb.liveroom.core.service.LiveRoomParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class LiveRoomWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws/liveroom";
    private static final String APP_DESTINATION_PREFIX = "/app";
    private static final String TOPIC_PREFIX = "/topic";
    private static final String QUEUE_PREFIX = "/queue";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final LiveRoomProperties properties;
    private final LiveRoomParticipantService participantService;

    public LiveRoomWebSocketConfig(
            JwtTokenProvider jwtTokenProvider,
            LiveRoomProperties properties,
            @Lazy LiveRoomParticipantService participantService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
        this.participantService = participantService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint(ENDPOINT);
        List<String> origins = properties.getWs() == null || properties.getWs().getAllowedOrigins() == null
                ? List.of()
                : properties.getWs().getAllowedOrigins();
        if (origins.isEmpty()) {
            log.warn("WS liveroom allowed origins empty - using dev fallback");
            endpoint.setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
        } else {
            endpoint.setAllowedOrigins(origins.toArray(String[]::new));
        }
        endpoint.withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX, QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(APP_DESTINATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthInterceptor());
    }

    private class StompAuthInterceptor implements ChannelInterceptor {

        @Override
        public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) {
                return message;
            }
            StompCommand command = accessor.getCommand();
            if (command == null) {
                return message;
            }
            if (command == StompCommand.CONNECT) {
                String token = extractBearerToken(accessor);
                if (token == null) {
                    log.warn("STOMP CONNECT rejected: missing Authorization header");
                    throw new IllegalArgumentException("Missing Authorization header");
                }
                if (!jwtTokenProvider.validateAccessToken(token)) {
                    log.warn("STOMP CONNECT rejected: invalid JWT token");
                    throw new IllegalArgumentException("Invalid JWT token");
                }
                UUID userId = jwtTokenProvider.extractUserId(token);
                Principal principal = new StompUserPrincipal(userId);
                accessor.setUser(principal);
                log.debug("STOMP CONNECT authenticated: userId={}", userId);
            } else if (command == StompCommand.SUBSCRIBE) {
                if (accessor.getUser() == null) {
                    log.warn("STOMP SUBSCRIBE rejected: no authenticated user");
                    throw new IllegalArgumentException("Unauthenticated STOMP subscribe");
                }
                String destination = accessor.getDestination();
                String roomCode = extractRoomCodeFromDestination(destination);
                if (roomCode != null && isPlaybackDestination(destination)) {
                    UUID userId = principalUserId(accessor);
                    if (userId != null && !participantService.isActiveParticipant(roomCode, userId)) {
                        log.warn("STOMP SUBSCRIBE rejected: user not in room, roomCode={}, userId={}, destination={}",
                                roomCode, userId, destination);
                        throw new IllegalArgumentException("Not an active participant of this room");
                    }
                }
            } else if (command == StompCommand.SEND) {
                if (accessor.getUser() == null) {
                    log.warn("STOMP SEND rejected: no authenticated user");
                    throw new IllegalArgumentException("Unauthenticated STOMP message");
                }
            }
            return message;
        }

        @Nullable
        private UUID principalUserId(StompHeaderAccessor accessor) {
            Principal principal = accessor.getUser();
            if (principal instanceof StompUserPrincipal stompUser) {
                return stompUser.userId();
            }
            return null;
        }

        @Nullable
        private String extractRoomCodeFromDestination(@Nullable String destination) {
            if (destination == null) {
                return null;
            }
            String marker = "/room/";
            int idx = destination.indexOf(marker);
            if (idx < 0) {
                return null;
            }
            String after = destination.substring(idx + marker.length());
            int nextSlash = after.indexOf('/');
            if (nextSlash < 0) {
                return null;
            }
            String code = after.substring(0, nextSlash);
            if (code.length() != 6) {
                return null;
            }
            return code;
        }

        private boolean isPlaybackDestination(String destination) {
            return destination.contains("/playback");
        }

        @Nullable
        private String extractBearerToken(StompHeaderAccessor accessor) {
            List<String> values = accessor.getNativeHeader(AUTH_HEADER);
            if (values == null) return null;
            for (String value : values) {
                if (value != null && value.startsWith(BEARER_PREFIX)) {
                    return value.substring(BEARER_PREFIX.length());
                }
            }
            return null;
        }
    }

    public record StompUserPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
