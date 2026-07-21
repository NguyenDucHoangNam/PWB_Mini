package com.pwb.liveroom.api;

import com.pwb.liveroom.api.dto.request.JoinLiveRoomRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinResponse;
import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface LiveRoomParticipantFacade {

    LiveRoomJoinResponse joinRoom(UUID userId, String displayName, String role, String roomCode, JoinLiveRoomRequest request);

    void leaveRoom(UUID userId, String roomCode);

    List<ParticipantSummaryResponse> listParticipants(String roomCode);
}