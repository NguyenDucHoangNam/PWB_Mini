package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.liveroom.api.realtime.LiveroomStompExceptionHandler;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import com.pwb.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompRateLimitInterceptor implements ChannelInterceptor {

    private static final String RTC_SEGMENT = "/rtc/";
    private static final String CHAT_SEGMENT = "/chat/";

    private final LiveroomConfig config;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageSource messageSource;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SEND.equals(accessor.getCommand())) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        if (sessionId == null || destination == null) {
            return message;
        }

        Bucket bucket = bucketOf(destination);
        Window window = windows.computeIfAbsent(sessionId + "|" + bucket, key -> new Window());

        if (window.allow(limitOf(bucket), config.getRealtime().getRateLimitWindow().toMillis())) {
            return message;
        }

        if (window.claimWarning()) {
            notifySender(accessor);
            log.warn("Throttled STOMP frames: sessionId={} bucket={} destination={}",
                    sessionId, bucket, destination);
        }
        return null;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        windows.keySet().removeIf(key -> key.startsWith(sessionId + "|"));
    }

    private Bucket bucketOf(String destination) {
        if (destination.contains(RTC_SEGMENT)) {
            return Bucket.RTC;
        }
        if (destination.contains(CHAT_SEGMENT)) {
            return Bucket.CHAT;
        }
        return Bucket.OTHER;
    }

    private int limitOf(Bucket bucket) {
        LiveroomConfig.Realtime realtime = config.getRealtime();
        return switch (bucket) {
            case RTC -> realtime.getRtcFramesPerWindow();
            case CHAT -> realtime.getChatFramesPerWindow();
            case OTHER -> realtime.getDefaultFramesPerWindow();
        };
    }

    private void notifySender(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    accessor.getUser().getName(),
                    LiveroomStompExceptionHandler.ERROR_QUEUE,
                    ApiResponse.error(LiveroomErrorCode.WS_RATE_LIMITED, resolve()));
        } catch (Exception ex) {
            log.debug("Failed to notify throttled sender: {}", ex.getMessage());
        }
    }

    private String resolve() {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(
                    LiveroomErrorCode.WS_RATE_LIMITED.code(),
                    null,
                    LiveroomErrorCode.WS_RATE_LIMITED.defaultMessage(),
                    locale);
        } catch (Exception ex) {
            return LiveroomErrorCode.WS_RATE_LIMITED.defaultMessage();
        }
    }

    private enum Bucket {
        RTC, CHAT, OTHER
    }

    private static final class Window {

        private long startedAt;
        private int count;
        private boolean warned;

        synchronized boolean allow(int limit, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - startedAt >= windowMs) {
                startedAt = now;
                count = 0;
                warned = false;
            }
            count += 1;
            return count <= limit;
        }

        synchronized boolean claimWarning() {
            if (warned) {
                return false;
            }
            warned = true;
            return true;
        }
    }
}