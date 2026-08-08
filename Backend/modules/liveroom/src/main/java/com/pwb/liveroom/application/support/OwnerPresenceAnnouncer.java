package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerPresenceAnnouncer {

    private final LiveRoomRepository liveRoomRepository;
    private final LiveroomEventPublisher eventPublisher;
    private final LiveroomConfig config;

    private final Map<UUID, ScheduledFuture<?>> pendingByRoom = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "liveroom-owner-presence");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }


    public void ownerLeft(UUID roomId) {
        afterCommit(() -> {
            cancelPending(roomId);
            long delayMillis = config.getRoom().getOwnerLeaveDebounce().toMillis();
            pendingByRoom.put(roomId, scheduler.schedule(
                    () -> announceIfStillAway(roomId), delayMillis, TimeUnit.MILLISECONDS));
        });
    }


    public void ownerReturned(LiveRoom room, Instant at) {
        if (cancelPending(room.getId())) {
            log.debug("Owner returned within the debounce window, saying nothing: roomId={}", room.getId());
            return;
        }
        eventPublisher.broadcastToRoom(RoomEvents.ownerRejoined(room, at));
    }


    public void forget(UUID roomId) {
        cancelPending(roomId);
    }

    private void announceIfStillAway(UUID roomId) {
        pendingByRoom.remove(roomId);
        try {
            LiveRoom room = liveRoomRepository.findById(roomId).orElse(null);


            if (room == null || !room.isActive() || !room.isOwnerAbsent()) {
                return;
            }
            eventPublisher.broadcastToRoom(RoomEvents.ownerLeft(room));
        } catch (Exception ex) {
            log.warn("Failed to announce owner departure for room {}: {}", roomId, ex.getMessage());
        }
    }

    private boolean cancelPending(UUID roomId) {
        ScheduledFuture<?> pending = pendingByRoom.remove(roomId);
        return pending != null && pending.cancel(false);
    }


    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}