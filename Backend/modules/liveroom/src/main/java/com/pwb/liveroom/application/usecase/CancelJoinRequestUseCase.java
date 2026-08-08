package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.JoinRequestView;

import java.util.UUID;

public interface CancelJoinRequestUseCase {

    JoinRequestView execute(UUID actorId, UUID roomId, UUID requestId);
}