package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.SelectSongCommand;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.PlaybackViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.usecase.SelectSongUseCase;
import com.pwb.liveroom.application.view.PlaybackStateView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.service.PlayableSong;
import com.pwb.liveroom.domain.service.SongCatalogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class SelectSongUseCaseImpl implements SelectSongUseCase {

    private final Playbacks playbacks;
    private final SongCatalogPort songCatalog;
    private final PlaybackViewFactory viewFactory;

    @Override
    @Transactional
    public PlaybackStateView execute(SelectSongCommand command) {
        LiveRoom room = playbacks.requireActiveRoom(command.roomId());
        playbacks.requireInRoom(room, command.actorId());

        PlayableSong song = songCatalog.findById(command.songId())


                .filter(candidate -> candidate.ownerId().equals(command.actorId()))
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.MUSIC_NOT_OWN_SONG));
        if (!song.ready()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.MUSIC_NOT_READY);
        }

        Instant now = Instant.now();


        boolean startPlaying = playbacks.canStartAudio(room, command.actorId());

        PlaybackState state = playbacks.loadOrSilent(room.getId(), now);
        boolean songChanged = !command.songId().equals(state.getSongId());
        state.selectSong(song.id(), song.ownerId(), song.title(), song.artist(),
                song.durationSeconds(), startPlaying, command.actorId(), now);
        PlaybackState saved = playbacks.save(state);

        playbacks.announce(room, saved, songChanged, now);

        log.debug("Song selected: roomId={} userId={} songId={} startPlaying={} seq={}",
                room.getId(), command.actorId(), song.id(), startPlaying, saved.getSequenceNumber());
        return viewFactory.toView(room, saved, now);
    }
}