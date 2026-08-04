package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.AudioVariant;
import com.pwb.audio.domain.enums.SongStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SongUseCase {

    UploadUrlView createUploadUrl(UUID userId, String format);

    SongView createSong(CreateSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    /**
     * @param status narrows the listing; {@code null} lists every status. Filtering happens in the query so
     *               the page counts describe the filtered set rather than the whole library.
     */
    Page<SongView> listSongs(UUID userId, SongStatus status, Pageable pageable);

    /** @return empty when the song has no voice tag configured yet — a normal state, not a failure. */
    Optional<SongTagConfigView> getVoiceTagConfig(UUID userId, UUID songId);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongTagConfigView configureVoiceTag(ConfigureVoiceTagCommand command);

    SongView triggerProcessing(UUID userId, UUID songId);

    AudioUrlView getAudioUrl(UUID userId, UUID songId, AudioVariant variant, Duration expiration);
}
