package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record UserVerifiedEvent(
        UUID userId,
        String email,
        Instant verifiedAt
) {}