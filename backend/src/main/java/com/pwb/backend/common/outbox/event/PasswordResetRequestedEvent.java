package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetRequestedEvent(
        UUID userId,
        String email,
        String fullName,
        String token,
        Instant issuedAt) {
}
