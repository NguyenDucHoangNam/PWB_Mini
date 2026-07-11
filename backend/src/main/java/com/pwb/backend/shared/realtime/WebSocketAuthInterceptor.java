package com.pwb.backend.shared.realtime;

import com.pwb.backend.shared.web.security.JwtVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern SELF_USER_TOPIC = Pattern.compile("^/topic/user/([^,/]+)/.*$");
    private static final Pattern SELF_USER_QUEUE = Pattern.compile("^/queue/user/([^,/]+)/.*$");

    private final JwtVerifier jwtVerifier;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            case DISCONNECT, UNSUBSCRIBE -> {  }
            default -> log.debug("WebSocket frame {} allowed for session", command);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: Missing or invalid Authorization header");
            throw new AccessDeniedException("Unauthorized WebSocket connection");
        }

        String token = authHeader.substring(7);
        if (!jwtVerifier.isTokenValid(token)) {
            log.warn("WebSocket CONNECT rejected: Invalid JWT token");
            throw new AccessDeniedException("Invalid JWT token");
        }

        String email = jwtVerifier.extractEmail(token);
        String role = jwtVerifier.extractRole(token);
        log.info("WebSocket CONNECT authenticated (user redacted)");

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority(role)));

        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        requireAuthenticatedSession(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        String principal = principalName(accessor);
        java.util.regex.Matcher topicMatcher = SELF_USER_TOPIC.matcher(destination);
        if (topicMatcher.matches()) {
            ensureSelfOrThrow(principal, topicMatcher.group(1), destination, "subscribe");
            return;
        }
        java.util.regex.Matcher queueMatcher = SELF_USER_QUEUE.matcher(destination);
        if (queueMatcher.matches()) {
            ensureSelfOrThrow(principal, queueMatcher.group(1), destination, "subscribe");
        }

    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        requireAuthenticatedSession(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        String principal = principalName(accessor);

        java.util.regex.Matcher topicMatcher = SELF_USER_TOPIC.matcher(destination);
        if (topicMatcher.matches()) {
            ensureSelfOrThrow(principal, topicMatcher.group(1), destination, "send");
            return;
        }
        java.util.regex.Matcher queueMatcher = SELF_USER_QUEUE.matcher(destination);
        if (queueMatcher.matches()) {
            ensureSelfOrThrow(principal, queueMatcher.group(1), destination, "send");
        }
    }

    private static void ensureSelfOrThrow(String principal,
                                          String ownerInDestination,
                                          String destination,
                                          String action) {
        if (principal == null || !principal.equals(ownerInDestination)) {
            log.warn("WebSocket {} denied for user='{}' on destination='{}'",
                action, principal, destination);
            throw new AccessDeniedException(
                "WebSocket destination '" + destination + "' is not owned by the caller");
        }
    }

    private static String principalName(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            return null;
        }
        return accessor.getUser().getName();
    }

    private void requireAuthenticatedSession(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            log.warn("WebSocket {} rejected: no authenticated session", accessor.getCommand());
            throw new AccessDeniedException("WebSocket session is not authenticated");
        }
    }
}
