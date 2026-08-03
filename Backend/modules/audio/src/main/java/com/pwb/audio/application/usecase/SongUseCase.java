package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.AudioVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.UUID;

public interface SongUseCase {

    UploadUrlView createUploadUrl(UUID userId, String format);

    SongView createSong(CreateSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    Page<SongView> listSongs(UUID userId, Pageable pageable);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongView triggerProcessing(UUID userId, UUID songId);

    AudioUrlView getAudioUrl(UUID userId, UUID songId, AudioVariant variant, Duration expiration);
}
