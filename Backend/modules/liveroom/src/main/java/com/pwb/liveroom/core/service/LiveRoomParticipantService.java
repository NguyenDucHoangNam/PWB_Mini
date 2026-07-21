package com.pwb.liveroom.core.service;

import com.pwb.liveroom.core.model.LiveRoomParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomParticipantService {

    Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode);

    List<LiveRoomParticipant> listActiveParticipants(String roomCode);
}
