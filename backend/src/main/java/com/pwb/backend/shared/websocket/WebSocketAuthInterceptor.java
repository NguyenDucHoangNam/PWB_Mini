package com.pwb.backend.shared.websocket;

import com.pwb.backend.shared.security.JwtVerifier;
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

/**
 * STOMP inbound-channel interceptor.
 *
 * <p>H7 (defense-in-depth): previously we only required an authenticated
 * session on SUBSCRIBE / SEND, which let any authenticated user wire up a
 * subscription to {@code /user/{someoneElse}/queue/...}. The destination
 * is now checked here as a backstop, while controllers remain the source
 * of truth for business rules.
 *
 * <p>L8: the {@code MESSAGE} case is removed because the Spring client
 * inbound channel does not deliver MESSAGE frames from clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /**
     * Allow a user to subscribe only to topics that mention their own
     * principal email or a generic public destination. {@code [^,]+} keeps
     * the match from spilling across STOMP destination segments.
     */
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
            case DISCONNECT, UNSUBSCRIBE -> { /* no-op: session cleanup handled by Spring */ }
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
        // /topic/* and /queue/* without the /user prefix are broker broadcasts
        // and intentionally remain accessible to every authenticated user.
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        requireAuthenticatedSession(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        String principal = principalName(accessor);
        // Sending to another user's destination is never legitimate from
        // the client. The application destination prefix (/app/...) is the
        // only path that application controllers route.
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