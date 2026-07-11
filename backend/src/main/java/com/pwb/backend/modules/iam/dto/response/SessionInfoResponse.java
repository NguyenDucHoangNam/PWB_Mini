package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;

public record SessionInfoResponse(
        String sessionUuid,
        String ipAddress,
        String deviceInfo,
        String location,
        Instant createdAt,
        boolean isCurrent) {
}
