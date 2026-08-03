package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SongUseCase {

    SongView uploadSong(UploadSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    Page<SongView> listSongs(UUID userId, Pageable pageable);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongView triggerProcessing(UUID userId, UUID songId);

    PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds);
}
