package com.pwb.audio.application.command;

import java.util.UUID;

/**
 * The file size is deliberately absent: it is read from object storage rather than taken from the client,
 * which has no way to prove what it actually uploaded.
 */
public record CreateSongCommand(
        UUID userId,
        String title,
        String originalS3Key,
        Integer durationSeconds,
        String format,
        VoiceTagSettings voiceTagConfig
) {
}
