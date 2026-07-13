package com.pwb.backend.modules.liveroom.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.security.jwt.JwtTypes;
import com.pwb.backend.modules.liveroom.config.ChatProperties;
import com.pwb.backend.modules.liveroom.dto.ws.ChatFrame;
import com.pwb.backend.modules.liveroom.dto.ws.ChatRateLimitWarning;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ChatRateLimitInterceptor implements ChannelInterceptor {

    private static final long WINDOW_NANOS = 60L * 1_000_000_000L;

    private final ChatProperties properties;

    private final ObjectMapper objectMapper;

    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private Pattern compiledPattern;

    public ChatRateLimitInterceptor(ChatProperties properties,
                                    ObjectMapper objectMapper,
                                    @Lazy SimpMessagingTemplate messagingTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.compiledPattern = compile(properties.getChatDestinationPattern());
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() != StompCommand.SEND) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !matches(destination)) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return message;
        }
        String type = parseType(message);
        if (type == null) {
            return message;
        }
        int limit = ChatFrame.TYPE_REACTION.equalsIgnoreCase(type)
                ? properties.getReactionRateLimitPerMinute()
                : properties.getTextRateLimitPerMinute();
        String key = type + ":" + sessionId;
        long now = System.nanoTime();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (window) {
            if (now - window.startNanos >= WINDOW_NANOS) {
                window.startNanos = now;
                window.count.set(0);
            }
            long current = window.count.incrementAndGet();
            if (current > limit) {
                UUID senderId = resolveUserId(accessor);
                log.warn("CHAT_RATE_LIMIT_HIT sessionId={} senderId={} destination={} type={} count={} limit={}",
                        sessionId, senderId, destination, type, current, limit);
                if (senderId != null) {
                    deliverWarning(senderId, type, limit);
                }
                return null;
            }
        }
        return message;
    }

    private String parseType(Message<?> message) {
        Object payload = message.getPayload();
        if (payload == null) {
            return null;
        }
        byte[] bytes;
        if (payload instanceof byte[] raw) {
            bytes = raw;
        } else {
            bytes = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            ChatFrame frame = objectMapper.readValue(bytes, ChatFrame.class);
            if (frame == null || frame.getType() == null) {
                return null;
            }
            String upper = frame.getType().toUpperCase();
            if (ChatFrame.TYPE_TEXT.equals(upper) || ChatFrame.TYPE_REACTION.equals(upper)) {
                return upper;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void deliverWarning(UUID senderId, String type, int limit) {
        ChatRateLimitWarning warning = ChatRateLimitWarning.builder()
                .event(ChatRateLimitWarning.EVENT)
                .type(type)
                .limitPerMinute(limit)
                .timestamp(Instant.now())
                .build();
        try {
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    properties.getChatRateLimitUserDestination(),
                    warning);
        } catch (Exception ex) {
            log.warn("CHAT_RATE_LIMIT_WARNING_DELIVERY_FAILED senderId={} reason={}",
                    senderId, ex.getMessage());
        }
    }

    private UUID resolveUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof JwtTypes.JwtAuthenticationToken jwt) {
            return jwt.getPrincipal().userId();
        }
        return null;
    }

    private boolean matches(String destination) {
        if (compiledPattern == null) {
            return false;
        }
        return compiledPattern.matcher(destination).matches();
    }

    private static Pattern compile(String regex) {
        if (regex == null || regex.isBlank()) {
            return Pattern.compile("^/app/rooms/[^/]+/chat$");
        }
        return Pattern.compile(regex);
    }

    private static final class Window {

        private long startNanos;

        private final AtomicLong count = new AtomicLong();

        Window(long startNanos) {
            this.startNanos = startNanos;
        }
    }
}
