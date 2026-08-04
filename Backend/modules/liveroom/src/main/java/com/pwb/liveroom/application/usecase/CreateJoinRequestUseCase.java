package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.CreateJoinRequestCommand;
import com.pwb.liveroom.application.view.JoinRequestView;

public interface CreateJoinRequestUseCase {

    JoinRequestView execute(CreateJoinRequestCommand command);
}