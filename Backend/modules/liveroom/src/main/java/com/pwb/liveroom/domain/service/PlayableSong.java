package com.pwb.liveroom.domain.service;

import java.util.UUID;


public record PlayableSong(
        UUID id,
        UUID ownerId,
        String title,
        String artist,
        Integer durationSeconds,
        boolean ready
) {
}