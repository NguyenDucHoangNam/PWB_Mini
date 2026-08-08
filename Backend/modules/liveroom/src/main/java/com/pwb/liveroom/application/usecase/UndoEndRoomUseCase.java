package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RoomView;

import java.util.UUID;

public interface UndoEndRoomUseCase {

    RoomView execute(UUID actorId, UUID roomId);
}