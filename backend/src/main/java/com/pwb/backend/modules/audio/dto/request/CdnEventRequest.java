package com.pwb.backend.modules.audio.dto.request;

import java.time.Instant;
import java.util.UUID;

public record CdnEventRequest(
        String event,
        UUID shareToken,
        String cdnNodeIp,
        String rejectionReason,
        Instant timestamp) {
}