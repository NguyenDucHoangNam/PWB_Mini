package com.pwb.liveroom.api;

import com.pwb.liveroom.api.dto.request.CreateJoinRequestRequest;
import com.pwb.liveroom.api.dto.request.JoinRequestDecisionRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinRequestResponse;
import com.pwb.liveroom.core.model.JoinRequestStatus;

import java.util.List;
import java.util.UUID;

public interface LiveRoomJoinRequestFacade {

    LiveRoomJoinRequestResponse createOrReturnPending(UUID userId, String roomCode, CreateJoinRequestRequest request);

    List<LiveRoomJoinRequestResponse> listByRoom(UUID hostUserId, String roomCode, JoinRequestStatus status);

    LiveRoomJoinRequestResponse approve(UUID hostUserId, UUID requestId, JoinRequestDecisionRequest request);

    LiveRoomJoinRequestResponse reject(UUID hostUserId, UUID requestId, JoinRequestDecisionRequest request);

    void cancel(UUID ownerUserId, UUID requestId);
}
