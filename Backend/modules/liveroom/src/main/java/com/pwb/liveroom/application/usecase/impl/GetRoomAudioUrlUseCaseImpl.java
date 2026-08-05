package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.usecase.GetRoomAudioUrlUseCase;
import com.pwb.liveroom.application.view.RoomAudioUrlView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.service.PlayableSongAudio;
import com.pwb.liveroom.domain.service.SongCatalogPort;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetRoomAudioUrlUseCaseImpl implements GetRoomAudioUrlUseCase {

    private final Playbacks playbacks;
    private final SongCatalogPort songCatalog;
    private final LiveroomConfig config;

    @Override
    @Transactional(readOnly = true)
    public RoomAudioUrlView execute(UUID actorId, UUID roomId) {
        LiveRoom room = playbacks.requireActiveRoom(roomId);
        playbacks.requireInRoom(room, actorId);

        PlaybackState state = playbacks.requireWithSong(roomId);

        PlayableSongAudio audio = songCatalog
                .presignPlayback(state.getSongId(), config.getMusic().getAudioUrlTtl())
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.MUSIC_SONG_LOAD_FAILED));

        log.debug("Issued room audio url: roomId={} userId={} songId={}",
                roomId, actorId, state.getSongId());
        return new RoomAudioUrlView(audio.songId(), audio.url(), audio.expiresAt());
    }
}