package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.liveroom.application.event.RealtimeSessionEvictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class StompSessionRegistryAdapter implements RealtimeSessionEvictor {


    private static final Duration GRACE_BEFORE_CLOSE = Duration.ofSeconds(2);

    private final Map<String, WebSocketSession> socketsBySessionId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> sessionIdsByUser = new ConcurrentHashMap<>();

    private final ScheduledExecutorService closer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "liveroom-session-evictor");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdown() {
        closer.shutdownNow();
    }


    public void registerSocket(WebSocketSession session) {
        socketsBySessionId.put(session.getId(), session);
    }

    public void forgetSocket(String sessionId) {
        socketsBySessionId.remove(sessionId);
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        withSession(event.getMessage(), (userId, sessionId) ->
                sessionIdsByUser
                        .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                        .add(sessionId));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forgetSocket(event.getSessionId());
        withSession(event.getMessage(), (userId, sessionId) ->
                sessionIdsByUser.computeIfPresent(userId, (key, sessions) -> {
                    sessions.remove(sessionId);
                    return sessions.isEmpty() ? null : sessions;
                }));
    }


    @Override
    public void evictUser(UUID userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleClose(userId);
                }
            });
        } else {
            scheduleClose(userId);
        }
    }

    private void scheduleClose(UUID userId) {
        Set<String> sessionIds = sessionIdsByUser.remove(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        closer.schedule(() -> closeAll(userId, sessionIds),
                GRACE_BEFORE_CLOSE.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void closeAll(UUID userId, Set<String> sessionIds) {
        sessionIds.forEach(sessionId -> {
            WebSocketSession socket = socketsBySessionId.remove(sessionId);
            if (socket == null) {
                return;
            }
            try {
                socket.close(CloseStatus.NORMAL);
            } catch (Exception ex) {
                log.warn("Failed to close realtime session {}: {}", sessionId, ex.getMessage());
            }
        });
        log.info("Evicted {} realtime session(s) for user {}", sessionIds.size(), userId);
    }

    private void withSession(Message<?> message, SessionAction action) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();
        if (accessor.getUser() instanceof StompUserPrincipal principal && sessionId != null) {
            action.accept(principal.userId(), sessionId);
        }
    }

    @FunctionalInterface
    private interface SessionAction {
        void accept(UUID userId, String sessionId);
    }
}