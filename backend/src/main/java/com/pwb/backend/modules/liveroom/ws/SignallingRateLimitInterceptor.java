package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.modules.liveroom.config.WebRtcProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SignallingRateLimitInterceptor implements ChannelInterceptor {

    private final WebRtcProperties properties;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private Pattern compiledPattern;

    private static final long WINDOW_NANOS = 60L * 1_000_000_000L;

    public SignallingRateLimitInterceptor(WebRtcProperties properties) {
        this.properties = properties;
        this.compiledPattern = compile(properties.getSignallingDestinationPattern());
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
        int limit = properties.getSignallingRateLimitPerMinute();
        long now = System.nanoTime();
        Window window = windows.computeIfAbsent(sessionId, key -> new Window(now));
        synchronized (window) {
            if (now - window.startNanos >= WINDOW_NANOS) {
                window.startNanos = now;
                window.count.set(0);
            }
            long current = window.count.incrementAndGet();
            if (current > limit) {
                log.warn("SIGNALLING_RATE_LIMIT_HIT sessionId={} destination={} count={} limit={}",
                        sessionId, destination, current, limit);
                return null;
            }
        }
        return message;
    }

    private boolean matches(String destination) {
        if (compiledPattern == null) {
            return false;
        }
        return compiledPattern.matcher(destination).matches();
    }

    private static Pattern compile(String regex) {
        if (regex == null || regex.isBlank()) {
            return Pattern.compile("^/app/rooms/[^/]+/signalling$");
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
