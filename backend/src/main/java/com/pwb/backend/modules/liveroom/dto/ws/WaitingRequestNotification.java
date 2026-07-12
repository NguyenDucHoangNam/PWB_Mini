package com.pwb.backend.modules.liveroom.dto.ws;

import java.time.Instant;
import java.util.UUID;

public record WaitingRequestNotification(
        UUID listenerId,
        String displayName,
        Instant requestedAt,
        String action) {
}