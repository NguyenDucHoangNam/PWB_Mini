package com.pwb.audio.api.dto.request;

import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.VoiceTagSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Registers a song whose audio is already in object storage. The file size is not accepted here — the
 * server reads it back from storage, so a client cannot understate what it uploaded.
 */
public record CreateSongRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 512) String originalS3Key,
        @NotNull(message = "{validation.song.durationSeconds.notNull}") @Positive(message = "{validation.song.durationSeconds.positive}") Integer durationSeconds,
        @NotBlank @Size(max = 16) String format,
        @Valid SongVoiceTagRequest voiceTagConfig
) {

    public CreateSongCommand toCommand(UUID userId) {
        return new CreateSongCommand(
                userId,
                title,
                originalS3Key,
                durationSeconds,
                format,
                voiceTagConfig == null ? null : voiceTagConfig.toSettings()
        );
    }
}
