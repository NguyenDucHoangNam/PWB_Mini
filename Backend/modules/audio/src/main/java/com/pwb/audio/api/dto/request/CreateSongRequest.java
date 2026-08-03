package com.pwb.audio.api.dto.request;

import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.CreateSongCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSongRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 512) String originalS3Key,
        @NotNull @Positive Long fileSizeBytes,
        @NotNull @Positive Integer durationSeconds,
        @NotBlank @Size(max = 16) String format,
        @Valid ConfigureVoiceTagRequest voiceTagConfig
) {

    public CreateSongCommand toCommand(UUID userId) {
        return new CreateSongCommand(
                userId,
                title,
                originalS3Key,
                fileSizeBytes,
                durationSeconds,
                format,
                toVoiceTagCommand()
        );
    }

    private ConfigureVoiceTagCommand toVoiceTagCommand() {
        if (voiceTagConfig == null) {
            return null;
        }
        return new ConfigureVoiceTagCommand(
                null,
                voiceTagConfig.voiceTagId(),
                voiceTagConfig.intervalSeconds(),
                voiceTagConfig.volumePercentage(),
                voiceTagConfig.duckingPercentage(),
                voiceTagConfig.startOffsetSeconds(),
                voiceTagConfig.enabled()
        );
    }
}
