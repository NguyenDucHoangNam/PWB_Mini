package com.pwb.liveroom.application.view;

import com.pwb.liveroom.domain.enums.PlaybackStatus;

import java.time.Instant;
import java.util.UUID;


public record PlaybackStateView(
        UUID roomId,
        UUID songId,
        UUID songOwnerId,
        String songTitle,
        String songArtist,
        Integer songDurationSeconds,
        PlaybackStatus status,
        double positionSeconds,
        int volumePercent,
        Instant startedAt,
        Instant lastUpdatedAt,
        UUID lastUpdatedBy,
        long sequenceNumber,
        boolean ownerAbsent
) {
}