package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.view.ParticipantView;

import java.util.UUID;

public interface JoinRoomUseCase {


    ParticipantView execute(Actor actor, UUID roomId);
}