package com.pwb.backend.modules.liveroom.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WaitingMemberResponse(
        UUID listenerId,
        String displayName,
        Instant requestedAt) {
}