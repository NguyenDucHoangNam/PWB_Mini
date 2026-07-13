package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.ws.JoinResultMessage;
import com.pwb.backend.modules.liveroom.dto.ws.MembersSnapshotMessage;
import com.pwb.backend.modules.liveroom.dto.ws.PlaybackChangeMessage;
import com.pwb.backend.modules.liveroom.dto.ws.WaitingRequestNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRoomMembershipNotifier {

    private static final String HOST_TOPIC = "/topic/rooms/%s/host";
    private static final String MEMBERS_TOPIC = "/topic/rooms/%s/members";
    private static final String PLAYBACK_TOPIC = "/topic/rooms/%s/playback";
    private static final String LISTENER_USER_DESTINATION = "/queue/rooms/join-result";

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyJoinResult(UUID userId, JoinResultMessage payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), LISTENER_USER_DESTINATION, payload);
        } catch (Exception ex) {
            log.warn("WS_JOIN_RESULT_DELIVERY_FAILED userId={} reason={}", userId, ex.getMessage());
        }
    }

    public void notifyHostWaitingListChange(String roomCode, WaitingRequestNotification notification) {
        try {
            messagingTemplate.convertAndSend(String.format(HOST_TOPIC, roomCode), notification);
        } catch (Exception ex) {
            log.warn("WS_HOST_NOTIFY_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
        }
    }

    public void notifyMembersChange(String roomCode, MembersSnapshotMessage snapshot) {
        try {
            messagingTemplate.convertAndSend(String.format(MEMBERS_TOPIC, roomCode), snapshot);
        } catch (Exception ex) {
            log.warn("WS_MEMBERS_BROADCAST_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
        }
    }

    public void notifyPlaybackChange(String roomCode, PlaybackChangeMessage payload) {
        try {
            messagingTemplate.convertAndSend(String.format(PLAYBACK_TOPIC, roomCode), payload);
        } catch (Exception ex) {
            log.warn("WS_BROADCAST_SOURCE_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
        }
    }
}