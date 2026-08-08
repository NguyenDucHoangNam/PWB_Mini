package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.SongStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SongUseCase {

    UploadUrlView createUploadUrl(UUID userId, String format);

    SongView createSong(CreateSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    /**
     * @param statuses narrows the listing; empty or {@code null} lists everything. A set rather than one
     *                 value because a single user-facing filter can cover several job states — "ready"
     *                 means both a plain upload and a finished merge. Filtering happens in the query, so
     *                 the page counts describe the filtered set rather than the whole library.
     */
    Page<SongView> listSongs(UUID userId, Collection<SongStatus> statuses, Pageable pageable);

    /**
     * @return empty when the song was uploaded without a voice tag — the ordinary state for a plain music
     *         upload, not a failure. The configuration is fixed at upload and never changes afterwards.
     */
    Optional<SongTagConfigView> getVoiceTagConfig(UUID userId, UUID songId);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    /** Re-runs a merge that failed. Rejected for any other status: a finished song is never re-rendered. */
    SongView retryProcessing(UUID userId, UUID songId);

    /** The song's only playable rendition — merged if it has a voice tag, the plain upload otherwise. */
    AudioUrlView getAudioUrl(UUID userId, UUID songId, Duration expiration);
}
