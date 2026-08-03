package com.pwb.audio.application.command;

import java.util.UUID;

public record ConfigureVoiceTagCommand(
        UUID songId,
        UUID voiceTagId,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer fadeInDurationMs,
        Integer fadeOutDurationMs,
        Integer startOffsetSeconds,
        boolean enabled
) {
}
