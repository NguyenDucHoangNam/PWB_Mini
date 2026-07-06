package com.pwb.backend.iam.api.dto.response;

import java.time.Instant;

public record ActiveSessionResponse(
    String sessionUuid,
    String ipAddress,
    String deviceInfo,
    String location,
    Instant createdAt,
    boolean isCurrent
) {}
