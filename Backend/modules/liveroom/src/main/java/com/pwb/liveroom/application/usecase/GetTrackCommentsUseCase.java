package com.pwb.liveroom.application.usecase;

import java.util.UUID;


public interface GetTrackCommentsUseCase {

    void execute(UUID actorId, UUID roomId);
}