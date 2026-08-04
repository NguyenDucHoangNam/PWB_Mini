package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.PlaybackViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.usecase.GetPlaybackStateUseCase;
import com.pwb.liveroom.application.view.PlaybackStateView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class GetPlaybackStateUseCaseImpl implements GetPlaybackStateUseCase {

    private final Playbacks playbacks;
    private final PlaybackViewFactory viewFactory;

    @Override
    @Transactional(readOnly = true)
    public PlaybackStateView execute(UUID actorId, UUID roomId) {
        LiveRoom room = playbacks.requireActiveRoom(roomId);
        playbacks.requireInRoom(room, actorId);

        Instant now = Instant.now();
        PlaybackState state = playbacks.loadOrSilent(roomId, now);
        playbacks.announce(room, state, false, now);
        return viewFactory.toView(room, state, now);
    }
}