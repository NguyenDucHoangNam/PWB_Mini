package com.pwb.liveroom.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRoomRealtimeBroadcaster {

    private static final String ROOM_TOPIC_BASE = "/topic/room/";
    private static final String PARTICIPANTS_TOPIC_SUFFIX = "/participants";
    private static final String PEERS_TOPIC_SUFFIX = "/peers";
    private static final String JOIN_REQUESTS_TOPIC_SUFFIX = "/join-requests";
    private static final String USER_QUEUE_BASE = "/queue/user/";
    private static final String JOIN_REQUESTS_USER_SUFFIX = "/join-requests";

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastParticipantJoined(
            String roomCode,
            UUID userId,
            String displayName,
            String roleAtJoin,
            int currentCount,
            int maxParticipants,
            int availableSlots,
            String timestamp) {
        String destination = ROOM_TOPIC_BASE + roomCode + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PARTICIPANT_JOINED",
                "roomCode", roomCode,
                "userId", userId,
                "displayName", displayName,
                "roleAtJoin", roleAtJoin,
                "currentCount", currentCount,
                "maxParticipants", maxParticipants,
                "availableSlots", availableSlots,
                "timestamp", timestamp
        );
        send(destination, payload, "PARTICIPANT_JOINED", roomCode, userId);
    }

    public void broadcastParticipantLeft(
            String roomCode,
            UUID userId,
            String displayName,
            int currentCount,
            int maxParticipants,
            int availableSlots,
            String timestamp) {
        String destination = ROOM_TOPIC_BASE + roomCode + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PARTICIPANT_LEFT",
                "roomCode", roomCode,
                "userId", userId,
                "displayName", displayName,
                "currentCount", currentCount,
                "maxParticipants", maxParticipants,
                "availableSlots", availableSlots,
                "timestamp", timestamp
        );
        send(destination, payload, "PARTICIPANT_LEFT", roomCode, userId);
    }

    public void broadcastPeerJoined(
            String roomCode,
            UUID userId,
            String displayName,
            String timestamp) {
        String destination = ROOM_TOPIC_BASE + roomCode + PEERS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PEER_JOINED",
                "roomCode", roomCode,
                "userId", userId,
                "displayName", displayName,
                "timestamp", timestamp
        );
        send(destination, payload, "PEER_JOINED", roomCode, userId);
    }

    public void broadcastPeerLeft(
            String roomCode,
            UUID userId,
            String timestamp) {
        String destination = ROOM_TOPIC_BASE + roomCode + PEERS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PEER_LEFT",
                "roomCode", roomCode,
                "userId", userId,
                "timestamp", timestamp
        );
        send(destination, payload, "PEER_LEFT", roomCode, userId);
    }

    public void broadcastMediaStateChanged(
            String roomCode,
            UUID userId,
            String displayName,
            boolean micMuted,
            boolean cameraOff,
            String timestamp) {
        String destination = ROOM_TOPIC_BASE + roomCode + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "MEDIA_STATE_CHANGED",
                "roomCode", roomCode,
                "userId", userId,
                "displayName", displayName,
                "micMuted", micMuted,
                "cameraOff", cameraOff,
                "timestamp", timestamp
        );
        send(destination, payload, "MEDIA_STATE_CHANGED", roomCode, userId);
    }

    public void broadcastJoinRequestCreated(
            String roomCode,
            UUID requestId,
            UUID requesterUserId,
            String displayName,
            String message,
            String createdAt) {
        String destination = ROOM_TOPIC_BASE + roomCode + JOIN_REQUESTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "JOIN_REQUEST_CREATED",
                "roomCode", roomCode,
                "requestId", requestId,
                "requesterUserId", requesterUserId,
                "displayName", displayName,
                "message", message == null ? "" : message,
                "timestamp", createdAt
        );
        send(destination, payload, "JOIN_REQUEST_CREATED", roomCode, requesterUserId);
    }

    public void pushJoinRequestDecided(
            UUID requesterUserId,
            String roomCode,
            UUID requestId,
            String status,
            String reason,
            String decidedAt) {
        String destination = USER_QUEUE_BASE + requesterUserId + JOIN_REQUESTS_USER_SUFFIX;
        Object payload = Map.of(
                "type", "JOIN_REQUEST_DECIDED",
                "roomCode", roomCode,
                "requestId", requestId,
                "status", status,
                "reason", reason == null ? "" : reason,
                "timestamp", decidedAt
        );
        send(destination, payload, "JOIN_REQUEST_DECIDED:" + status, roomCode, requesterUserId);
    }

    private void send(String destination, Object payload, String type, String roomCode, UUID userId) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Broadcast {}: roomCode={}, userId={}", type, roomCode, userId);
        } catch (Exception ex) {
            log.warn("Failed to broadcast {} for roomCode={}, userId={}: {}",
                    type, roomCode, userId, ex.getMessage());
        }
    }
}
