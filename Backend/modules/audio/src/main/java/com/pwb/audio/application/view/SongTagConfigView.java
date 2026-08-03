package com.pwb.audio.application.view;

import java.time.Instant;
import java.util.UUID;

public record SongTagConfigView(
        UUID id,
        UUID songId,
        UUID voiceTagId,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer fadeInDurationMs,
        Integer fadeOutDurationMs,
        Integer startOffsetSeconds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
