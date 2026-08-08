package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.JoinRequestView;

import java.util.List;
import java.util.UUID;

public interface ListPendingJoinRequestsUseCase {


    List<JoinRequestView> execute(UUID actorId, UUID roomId);
}