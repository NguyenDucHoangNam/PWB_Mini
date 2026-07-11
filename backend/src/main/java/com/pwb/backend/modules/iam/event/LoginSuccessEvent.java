package com.pwb.backend.modules.iam.event;

import java.time.Instant;
import java.util.UUID;

public record LoginSuccessEvent(
        UUID userId,
        String email,
        String ip,
        String userAgent,
        Instant occurredAt) {
}
