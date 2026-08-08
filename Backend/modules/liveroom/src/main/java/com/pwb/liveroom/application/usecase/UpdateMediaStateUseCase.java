package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.UpdateMediaStateCommand;
import com.pwb.liveroom.application.view.ParticipantView;

public interface UpdateMediaStateUseCase {


    ParticipantView execute(UpdateMediaStateCommand command);
}