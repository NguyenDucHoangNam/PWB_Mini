package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RoomView;

import java.util.UUID;

public interface EndRoomUseCase {

    RoomView execute(UUID actorId, UUID roomId);
}