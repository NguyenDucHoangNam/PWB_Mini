package com.pwb.audio.infrastructure.processor.event;

import java.time.Instant;
import java.util.UUID;

public record SongProcessingRequested(
        UUID songId,
        UUID userId,
        Instant requestedAt
) {
}