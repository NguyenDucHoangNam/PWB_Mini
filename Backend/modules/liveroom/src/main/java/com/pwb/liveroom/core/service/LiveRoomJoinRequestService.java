package com.pwb.liveroom.core.service;

import com.pwb.liveroom.core.model.JoinRequestStatus;
import com.pwb.liveroom.core.model.LiveRoomJoinRequest;

import java.util.List;
import java.util.UUID;

public interface LiveRoomJoinRequestService {

    LiveRoomJoinRequest createOrReturnPending(UUID userId, String roomCode, String displayName, String message);

    List<LiveRoomJoinRequest> listByRoom(String roomCode, UUID hostUserId, JoinRequestStatus status);

    LiveRoomJoinRequest approve(UUID hostUserId, UUID requestId, String reason);

    LiveRoomJoinRequest reject(UUID hostUserId, UUID requestId, String reason);

    void cancel(UUID ownerUserId, UUID requestId);
}
