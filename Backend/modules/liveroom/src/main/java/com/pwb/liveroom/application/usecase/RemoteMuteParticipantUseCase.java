package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.ModerateParticipantCommand;
import com.pwb.liveroom.application.view.ParticipantView;

public interface RemoteMuteParticipantUseCase {


    ParticipantView execute(ModerateParticipantCommand command);
}