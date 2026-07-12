package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.enums.RoomMode;

import java.util.UUID;

public interface RoomLifecycleService {

    CreateRoomResponse createRoom(RoomMode mode, UUID hostId, String hostDisplayName);

    void activateRoomPhase2(String roomCode);

    void markHostDisconnected(String sessionId);

    void closeRoom(String roomCode);
}