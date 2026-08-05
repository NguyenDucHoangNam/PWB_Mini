package com.pwb.liveroom.application.view;

import java.time.Instant;
import java.util.UUID;


public record TrackCommentView(
        UUID id,
        UUID songId,
        UUID userId,
        String userEmail,
        String content,
        double positionSeconds,
        Instant createdAt
) {
}