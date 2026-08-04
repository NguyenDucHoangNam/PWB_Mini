package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.ParticipantView;

import java.util.UUID;

public interface LeaveRoomUseCase {

    ParticipantView execute(UUID actorId, UUID roomId);
}