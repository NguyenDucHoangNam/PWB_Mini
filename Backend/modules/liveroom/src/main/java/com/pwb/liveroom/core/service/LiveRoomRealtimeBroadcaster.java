package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.dto.response.PlaybackSnapshotResponse;
import com.pwb.liveroom.api.dto.response.SongPlaybackSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String PLAYBACK_TOPIC_SUFFIX = "/playback";
    private static final String USER_QUEUE_BASE = "/queue/user/";
    private static final String JOIN_REQUESTS_USER_SUFFIX = "/join-requests";
    private static final String ROOM_USER_QUEUE_BASE = "/queue/room/";
    private static final String ROOM_STATE_USER_SUFFIX = "/state";
    private static final String PLAYBACK_STATE_USER_SUFFIX = "/playback/state";
    private static final String MEDIA_STATE_USER_SUFFIX = "/liveroom-media";

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

    public void pushMediaStateToUser(
            String roomCode,
            UUID recipientUserId,
            UUID targetUserId,
            String displayName,
            boolean micMuted,
            boolean cameraOff,
            String timestamp) {
        String destination = USER_QUEUE_BASE + recipientUserId + MEDIA_STATE_USER_SUFFIX;
        Object payload = Map.of(
                "type", "MEDIA_STATE_CHANGED",
                "roomCode", roomCode,
                "userId", targetUserId,
                "displayName", displayName,
                "micMuted", micMuted,
                "cameraOff", cameraOff,
                "timestamp", timestamp
        );
        sendToUser(recipientUserId.toString(), destination, payload, "MEDIA_STATE_CHANGED", roomCode);
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

    public void broadcastRoomEnded(
            String roomCode,
            UUID hostUserId,
            String endedAt) {
        String destination = ROOM_TOPIC_BASE + roomCode + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "ROOM_ENDED",
                "roomCode", roomCode,
                "hostUserId", hostUserId,
                "timestamp", endedAt
        );
        send(destination, payload, "ROOM_ENDED", roomCode, hostUserId);
    }

    public void pushRoomStateToUser(
            String roomCode,
            UUID recipientUserId,
            List<Map<String, String>> participants) {
        String destination = ROOM_USER_QUEUE_BASE + roomCode + ROOM_STATE_USER_SUFFIX;
        Object payload = Map.of(
                "type", "ROOM_STATE",
                "roomCode", roomCode,
                "participants", participants,
                "timestamp", Instant.now().toString()
        );
        sendToUser(recipientUserId.toString(), destination, payload, "ROOM_STATE", roomCode);
    }

    public void broadcastPlaybackEvent(
            String roomCode,
            PlaybackSnapshotResponse snapshot,
            String timestamp) {
        if (snapshot == null) {
            return;
        }
        String destination = ROOM_TOPIC_BASE + roomCode + PLAYBACK_TOPIC_SUFFIX;
        Object payload = buildPlaybackPayload(roomCode, snapshot, timestamp, "PLAYBACK_STATE_CHANGED");
        send(destination, payload, "PLAYBACK_STATE_CHANGED", roomCode, null);
    }

    public void pushPlaybackStateToUser(
            String roomCode,
            UUID recipientUserId,
            PlaybackSnapshotResponse snapshot) {
        if (snapshot == null) {
            return;
        }
        String destination = ROOM_USER_QUEUE_BASE + roomCode + PLAYBACK_STATE_USER_SUFFIX;
        Object payload = buildPlaybackPayload(roomCode, snapshot, Instant.now().toString(), "PLAYBACK_STATE");
        sendToUser(recipientUserId.toString(), destination, payload, "PLAYBACK_STATE", roomCode);
    }

    private Map<String, Object> buildPlaybackPayload(
            String roomCode,
            PlaybackSnapshotResponse snapshot,
            String timestamp,
            String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", eventType);
        payload.put("roomCode", roomCode);
        payload.put("status", snapshot.getStatus());
        payload.put("positionSeconds", snapshot.getPositionSeconds() == null ? 0L : snapshot.getPositionSeconds());
        payload.put("effectiveAt", snapshot.getEffectiveAt() == null ? "" : snapshot.getEffectiveAt().toString());
        payload.put("version", snapshot.getVersion() == null ? 0L : snapshot.getVersion());
        payload.put("empty", snapshot.isEmpty());
        payload.put("song", buildSongPayload(snapshot.getSong()));
        payload.put("changedByUserId", snapshot.getChangedByUserId() == null ? "" : snapshot.getChangedByUserId().toString());
        payload.put("changedAt", snapshot.getChangedAt() == null ? "" : snapshot.getChangedAt().toString());
        payload.put("timestamp", timestamp == null ? "" : timestamp);
        return payload;
    }

    private Map<String, Object> buildSongPayload(SongPlaybackSummaryResponse song) {
        if (song == null) {
            return null;
        }
        Map<String, Object> songPayload = new LinkedHashMap<>();
        songPayload.put("songId", song.getSongId() == null ? "" : song.getSongId().toString());
        songPayload.put("ownerUserId", song.getOwnerUserId() == null ? "" : song.getOwnerUserId().toString());
        songPayload.put("title", song.getTitle() == null ? "" : song.getTitle());
        songPayload.put("artist", song.getArtist() == null ? "" : song.getArtist());
        songPayload.put("album", song.getAlbum() == null ? "" : song.getAlbum());
        songPayload.put("durationSeconds", song.getDurationSeconds() == null ? 0 : song.getDurationSeconds());
        songPayload.put("format", song.getFormat() == null ? "" : song.getFormat());
        songPayload.put("processable", song.isProcessable());
        return songPayload;
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

    private void sendToUser(String userId, String destination, Object payload, String type, String roomCode) {
        try {
            messagingTemplate.convertAndSendToUser(userId, destination, payload);
            log.debug("Push {}: roomCode={}, userId={}", type, roomCode, userId);
        } catch (Exception ex) {
            log.warn("Failed to push {} for roomCode={}, userId={}: {}",
                    type, roomCode, userId, ex.getMessage());
        }
    }
}
