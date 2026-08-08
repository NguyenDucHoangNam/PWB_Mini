package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.view.RoomView;

import java.util.UUID;

public interface ReopenRoomUseCase {

    RoomView execute(Actor actor, UUID roomId);
}