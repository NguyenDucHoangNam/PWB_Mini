package com.pwb.iam.core.events;

import java.time.Instant;
import java.util.UUID;

public record AuthSuccessEvent(
        UUID userId,
        String email,
        String refreshToken,
        Instant occurredAt
) {
    public static AuthSuccessEvent of(UUID userId, String email, String refreshToken) {
        return new AuthSuccessEvent(userId, email, refreshToken, Instant.now());
    }
}
