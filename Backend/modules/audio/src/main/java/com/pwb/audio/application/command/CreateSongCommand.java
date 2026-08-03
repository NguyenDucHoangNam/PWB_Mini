package com.pwb.audio.application.command;

import java.util.UUID;

public record CreateSongCommand(
        UUID userId,
        String title,
        String originalS3Key,
        Long fileSizeBytes,
        Integer durationSeconds,
        String format,
        ConfigureVoiceTagCommand voiceTagConfig
) {
}
