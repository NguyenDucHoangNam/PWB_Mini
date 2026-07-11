package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String fullName,
        String otp,
        Instant issuedAt
) {}