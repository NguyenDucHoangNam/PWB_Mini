package com.pwb.backend.audio.api.dto.response;

import java.time.Instant;

public record PresignedUrlResponse(
    String uploadUrl,
    String s3Key,
    int expiresInSeconds,
    Instant issuedAt,
    long maxSizeBytes
) {}
