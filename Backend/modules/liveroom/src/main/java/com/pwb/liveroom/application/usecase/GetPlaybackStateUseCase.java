package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.PlaybackStateView;

import java.util.UUID;

public interface GetPlaybackStateUseCase {

    PlaybackStateView execute(UUID actorId, UUID roomId);
}