package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record SuspiciousLoginEvent(
        UUID userId,
        String email,
        String fullName,
        String currentIp,
        String previousIp,
        String currentCountry,
        String previousCountry,
        String currentDevice,
        String previousDevice,
        String city,
        Instant occurredAt) {
}