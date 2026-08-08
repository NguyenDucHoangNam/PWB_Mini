package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.CreateRoomCommand;
import com.pwb.liveroom.application.view.RoomView;

public interface CreateRoomUseCase {

    RoomView execute(CreateRoomCommand command);
}