package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record AccountAnonymizedEvent(
        UUID userId,
        Instant anonymizedAt,
        Instant deletionRequestedAt
) {
}