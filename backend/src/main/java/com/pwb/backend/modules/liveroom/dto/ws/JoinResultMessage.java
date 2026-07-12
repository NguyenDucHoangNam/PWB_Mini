package com.pwb.backend.modules.liveroom.dto.ws;

import java.time.Instant;

public record JoinResultMessage(
        String status,
        String roomCode,
        Instant timestamp) {
}