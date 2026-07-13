package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.enums.RoomMode;

import java.util.Set;
import java.util.UUID;

public interface RoomLifecycleService {

    CreateRoomResponse createRoom(RoomMode mode, UUID hostId, String hostDisplayName);

    void activateRoomPhase2(String roomCode);

    void markHostDisconnected(String sessionId);

    void markHostInactive(String roomCode);

    void markHostActive(String roomCode);

    void scheduleEmptyRoomCleanup(String roomCode);

    void cancelEmptyRoomCleanup(String roomCode);

    Set<String> findExpiredCleanupCandidates(long scoreCeilingExclusive, int limit);

    boolean isStatusStillInactiveOrEmpty(String roomCode);

    void forceBroadcastEviction(String roomCode);

    void closeRoom(String roomCode);

    void closeRoomByHost(String roomCode, UUID hostId);
}
