package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.response.WaitingListResponse;

import java.util.UUID;

public interface WaitingListService {

    WaitingListResponse listWaiting(String roomCode, UUID hostId);

    void approve(String roomCode, UUID hostId, UUID listenerId);

    void reject(String roomCode, UUID hostId, UUID listenerId);

    void kick(String roomCode, UUID hostId, UUID listenerId);

    void notifyMembersSnapshot(String roomCode);
}