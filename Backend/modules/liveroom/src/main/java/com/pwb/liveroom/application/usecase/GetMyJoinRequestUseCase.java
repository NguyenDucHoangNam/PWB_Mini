package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.JoinRequestView;

import java.util.UUID;

public interface GetMyJoinRequestUseCase {


    JoinRequestView execute(UUID actorId, UUID roomId);
}