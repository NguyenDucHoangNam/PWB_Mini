package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class StompLiveroomEventPublisherAdapter implements LiveroomEventPublisher {

    static final String ROOM_TOPIC_PREFIX = "/topic/liveroom/";
    static final String USER_QUEUE = "/queue/liveroom";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcastToRoom(RoomEvent event) {
        afterCommit(() -> {
            messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + event.roomId(), event);
            log.debug("Broadcast {} to room {}", event.type(), event.roomId());
        });
    }

    @Override
    public void broadcastToRoomChannel(RoomEvent event, String channel) {
        afterCommit(() -> {
            messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + event.roomId() + "/" + channel, event);
            log.debug("Broadcast {} to room {} channel {}", event.type(), event.roomId(), channel);
        });
    }

    @Override
    public void sendToUser(UUID userId, RoomEvent event) {
        afterCommit(() -> {
            messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE, event);
            log.debug("Sent {} to user {}", event.type(), userId);
        });
    }

    @Override
    public void sendToUserChannel(UUID userId, RoomEvent event, String channel) {
        afterCommit(() -> {
            messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE + "/" + channel, event);
            log.debug("Sent {} to user {} channel {}", event.type(), userId, channel);
        });
    }


    private void afterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(send);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(send);
            }
        });
    }

    private void dispatch(Runnable send) {
        try {
            send.run();
        } catch (Exception ex) {
            log.warn("Failed to publish live room event: {}", ex.getMessage());
        }
    }
}