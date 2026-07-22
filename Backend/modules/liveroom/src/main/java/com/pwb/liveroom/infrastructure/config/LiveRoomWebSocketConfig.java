package com.pwb.liveroom.infrastructure.config;

import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
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
@RequiredArgsConstructor
public class LiveRoomWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws/liveroom";
    private static final String APP_DESTINATION_PREFIX = "/app";
    private static final String TOPIC_PREFIX = "/topic";
    private static final String QUEUE_PREFIX = "/queue";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns("*")
                .withSockJS();
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
            } else if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
                if (accessor.getUser() == null) {
                    log.warn("STOMP {} rejected: no authenticated user", command);
                    throw new IllegalArgumentException("Unauthenticated STOMP message");
                }
            }
            return message;
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
