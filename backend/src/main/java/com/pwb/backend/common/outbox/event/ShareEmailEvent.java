package com.pwb.backend.common.outbox.event;

import java.time.Instant;
import java.util.UUID;

public record ShareEmailEvent(
        UUID distributionId,
        UUID threadId,
        UUID shareToken,
        String recipientEmail,
        boolean allowDownload,
        UUID demoId,
        UUID producerId,
        Instant issuedAt
) {
}