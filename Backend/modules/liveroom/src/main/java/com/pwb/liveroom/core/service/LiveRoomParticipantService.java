package com.pwb.liveroom.core.service;

import com.pwb.liveroom.core.model.LiveRoomParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomParticipantService {

    LiveRoomParticipant joinPublicRoom(UUID userId, String roomCode, String displayName, String role);

    Optional<LiveRoomParticipant> joinAsHost(UUID userId, String hostDisplayName, String roomCode);

    Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode);

    List<LiveRoomParticipant> listActiveParticipants(String roomCode);

    LiveRoomParticipant updateMediaState(UUID userId, String roomCode, boolean micMuted, boolean cameraOff);

    void requestRoomState(UUID userId, String roomCode);

    boolean isActiveParticipant(String roomCode, UUID userId);
}
