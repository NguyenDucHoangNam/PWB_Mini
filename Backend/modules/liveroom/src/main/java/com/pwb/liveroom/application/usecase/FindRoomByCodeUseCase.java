package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.FindRoomByCodeCommand;
import com.pwb.liveroom.application.view.RoomLookupView;

public interface FindRoomByCodeUseCase {

    RoomLookupView execute(FindRoomByCodeCommand command);
}