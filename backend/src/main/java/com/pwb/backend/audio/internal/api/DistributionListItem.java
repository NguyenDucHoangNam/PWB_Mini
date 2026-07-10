package com.pwb.backend.audio.internal.api;

import java.time.Instant;
import java.util.UUID;

public record DistributionListItem(
    String distributionId,
    String threadId,
    UUID shareToken,
    String recipientEmail,
    boolean allowDownload,
    boolean isRevoked,
    int playCount,
    Instant lastPlayedAt,
    Instant createdAt
) {}
