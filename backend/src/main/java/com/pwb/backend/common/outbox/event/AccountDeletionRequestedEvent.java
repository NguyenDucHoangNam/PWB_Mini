package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionRequestedEvent(
        UUID userId,
        String email,
        String fullName,
        Instant deletionRequestedAt,
        Instant scheduledPermanentDeletionAt,
        int graceDays
) {
}
