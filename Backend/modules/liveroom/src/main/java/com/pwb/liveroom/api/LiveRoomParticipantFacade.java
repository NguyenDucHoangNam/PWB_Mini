package com.pwb.liveroom.api;

import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface LiveRoomParticipantFacade {

    ParticipantSummaryResponse joinPublicRoom(UUID userId, String roomCode, String displayName, String role);

    void leaveRoom(UUID userId, String roomCode);

    List<ParticipantSummaryResponse> listParticipants(String roomCode);

    ParticipantSummaryResponse updateMediaState(UUID userId, String roomCode, boolean micMuted, boolean cameraOff);
}
