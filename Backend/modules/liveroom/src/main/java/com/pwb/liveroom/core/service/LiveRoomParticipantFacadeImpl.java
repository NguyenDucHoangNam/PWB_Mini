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
                .build();
    }
}
