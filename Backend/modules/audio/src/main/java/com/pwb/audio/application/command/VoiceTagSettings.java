package com.pwb.audio.application.command;

import java.util.UUID;

public record VoiceTagSettings(
        UUID voiceTagId,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer duckingPercentage,
        Integer startOffsetSeconds,
        boolean enabled
) {
}
