package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionCancelledEvent(
        UUID userId,
        String email,
        String fullName,
        Instant reactivatedAt
) {
}
