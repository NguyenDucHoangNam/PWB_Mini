package com.pwb.liveroom.domain.service;

import java.time.Instant;
import java.util.UUID;


public record PlayableSongAudio(
        UUID songId,
        String url,
        Instant expiresAt
) {
}