package com.pwb.backend.audio.internal.api;

import java.util.UUID;

public record DistributeDemoResponse(
    String distributionId,
    String threadId,
    UUID shareToken,
    String recipientEmail,
    boolean allowDownload,
    String shareLink
) {}
