package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.ControlPlaybackCommand;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.PlaybackViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.usecase.ControlPlaybackUseCase;
import com.pwb.liveroom.application.view.PlaybackStateView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaybackUseCaseImpl implements ControlPlaybackUseCase {

    private final Playbacks playbacks;
    private final PlaybackViewFactory viewFactory;

    @Override
    @Transactional
    public PlaybackStateView execute(ControlPlaybackCommand command) {
        LiveRoom room = playbacks.requireActiveRoom(command.roomId());
        playbacks.requireInRoom(room, command.actorId());

        Instant now = Instant.now();
        PlaybackState state = switch (command.action()) {
            case RESUME -> resume(room, command, now);
            case PAUSE -> pause(room, command, now);
            case SEEK -> seek(room, command, now);
            case VOLUME -> changeVolume(room, command, now);
        };
        PlaybackState saved = playbacks.save(state);

        playbacks.announce(room, saved, false, now);

        log.debug("Playback controlled: roomId={} userId={} action={} status={} seq={}",
                room.getId(), command.actorId(), command.action(),
                saved.getStatus(), saved.getSequenceNumber());
        return viewFactory.toView(room, saved, now);
    }

    private PlaybackState resume(LiveRoom room, ControlPlaybackCommand command, Instant now) {
        playbacks.requireCanStartAudio(room, command.actorId());
        PlaybackState state = playbacks.requireWithSong(room.getId());
        state.resume(command.actorId(), now);
        return state;
    }

    private PlaybackState pause(LiveRoom room, ControlPlaybackCommand command, Instant now) {
        PlaybackState state = playbacks.requireWithSong(room.getId());
        state.pause(command.actorId(), now);
        return state;
    }

    private PlaybackState seek(LiveRoom room, ControlPlaybackCommand command, Instant now) {
        PlaybackState state = playbacks.requireWithSong(room.getId());
        Double position = command.positionSeconds();


        if (position == null || position < 0
                || (state.getSongDurationSeconds() != null && position > state.getSongDurationSeconds())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.MUSIC_INVALID_POSITION);
        }
        state.seekTo(position, command.actorId(), now);
        return state;
    }


    private PlaybackState changeVolume(LiveRoom room, ControlPlaybackCommand command, Instant now) {
        Integer volume = command.volumePercent();
        if (volume == null
                || volume < PlaybackState.MIN_VOLUME_PERCENT
                || volume > PlaybackState.MAX_VOLUME_PERCENT) {
            throw new LiveroomBusinessException(LiveroomErrorCode.MUSIC_INVALID_VOLUME);
        }
        PlaybackState state = playbacks.loadOrSilent(room.getId(), now);
        state.changeVolume(volume, command.actorId(), now);
        return state;
    }
}