package com.pwb.liveroom.application.view;

import java.time.Instant;
import java.util.UUID;


public record RoomAudioUrlView(
        UUID songId,
        String url,
        Instant expiresAt
) {
}