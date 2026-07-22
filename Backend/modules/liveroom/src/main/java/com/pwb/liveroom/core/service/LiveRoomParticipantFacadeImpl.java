package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.LiveRoomParticipantFacade;
import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;
import com.pwb.liveroom.core.model.LiveRoomParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomParticipantFacadeImpl implements LiveRoomParticipantFacade {

    private final LiveRoomParticipantService participantService;

    @Override
    @Transactional
    public ParticipantSummaryResponse joinPublicRoom(UUID userId, String roomCode, String displayName, String role) {
        log.info("Facade joinPublicRoom: userId={}, roomCode={}", userId, roomCode);
        LiveRoomParticipant participant = participantService.joinPublicRoom(userId, roomCode, displayName, role);
        return toParticipantSummary(participant);
    }

    @Override
    @Transactional
    public void leaveRoom(UUID userId, String roomCode) {
        log.info("Facade leaveRoom: userId={}, roomCode={}", userId, roomCode);
        participantService.leaveRoom(userId, roomCode);
    }

    @Override
    public List<ParticipantSummaryResponse> listParticipants(String roomCode) {
        log.debug("Facade listParticipants: roomCode={}", roomCode);
        return participantService.listActiveParticipants(roomCode).stream()
                .map(this::toParticipantSummary)
                .toList();
    }

    @Override
    public ParticipantSummaryResponse updateMediaState(UUID userId, String roomCode, boolean micMuted, boolean cameraOff) {
        log.info("Facade updateMediaState: userId={}, roomCode={}, micMuted={}, cameraOff={}",
                userId, roomCode, micMuted, cameraOff);
        LiveRoomParticipant updated = participantService.updateMediaState(userId, roomCode, micMuted, cameraOff);
        return toParticipantSummary(updated);
    }

    private ParticipantSummaryResponse toParticipantSummary(LiveRoomParticipant participant) {
        if (participant == null) {
            return null;
        }
        return ParticipantSummaryResponse.builder()
                .participantId(participant.getId())
                .userId(participant.getUserId())
                .displayName(participant.getDisplayName())
                .roleAtJoin(participant.getRoleAtJoin())
                .joinedAt(participant.getJoinedAt())
                .micMuted(participant.isMicMuted())
                .cameraOff(participant.isCameraOff())
                .lastSeenAt(participant.getLastSeenAt())
                .build();
    }
}
