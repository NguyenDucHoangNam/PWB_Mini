package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.UploadSongCommand;
import com.pwb.audio.application.view.PresignedUploadUrlView;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SongUseCase {

    PresignedUploadUrlView createUploadUrl(UUID userId, String format);

    SongView uploadSong(UploadSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    Page<SongView> listSongs(UUID userId, Pageable pageable);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongView triggerProcessing(UUID userId, UUID songId);

    PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds);
}
