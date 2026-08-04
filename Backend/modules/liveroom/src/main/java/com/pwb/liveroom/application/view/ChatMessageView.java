package com.pwb.liveroom.application.view;

import java.time.Instant;
import java.util.UUID;


public record ChatMessageView(
        UUID id,
        UUID roomId,
        UUID cycleId,
        UUID userId,
        String userEmail,
        String content,
        Instant sentAt
) {
}