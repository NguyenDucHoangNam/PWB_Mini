package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.LiveRoomParticipantFacade;
import com.pwb.liveroom.api.dto.request.JoinLiveRoomRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinResponse;
import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;
import com.pwb.liveroom.core.model.LiveRoom;
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
    private final LiveRoomService liveRoomService;

    @Override
    @Transactional
    public LiveRoomJoinResponse joinRoom(
            UUID userId,
            String displayName,
            String role,
            String roomCode,
            JoinLiveRoomRequest request) {

        String effectiveDisplayName = request == null ? displayName : request.getDisplayName();
        log.info("Facade joinRoom: userId={}, roomCode={}", userId, roomCode);

        LiveRoomParticipant participant = participantService.joinRoom(
                userId,
                effectiveDisplayName,
                role,
                roomCode);

        LiveRoom room = liveRoomService.getRoomAsParticipant(roomCode);

        int availableSlots = Math.max(0, room.getMaxParticipants() - room.getCurrentParticipantCount());

        return LiveRoomJoinResponse.builder()
                .roomCode(room.getRoomCode())
                .title(room.getTitle())
                .hostUserId(room.getHostUserId())
                .participantId(participant.getId())
                .displayName(participant.getDisplayName())
                .roleAtJoin(participant.getRoleAtJoin())
                .joinedAt(participant.getJoinedAt())
                .currentParticipantCount(room.getCurrentParticipantCount())
                .maxParticipants(room.getMaxParticipants())
                .availableSlots(availableSlots)
                .build();
    }

    @Override
    @Transactional
    public void leaveRoom(UUID userId, String roomCode) {
        log.info("Facade leaveRoom: userId={}, roomCode={}", userId, roomCode);
        participantService.leaveRoom(userId, roomCode);
    }

    @Override
    public List<ParticipantSummaryResponse> listParticipants(String roomCode) {
        log.info("Facade listParticipants: roomCode={}", roomCode);
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