package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RoomAudioUrlView;

import java.util.UUID;

public interface GetRoomAudioUrlUseCase {


    RoomAudioUrlView execute(UUID actorId, UUID roomId);
}