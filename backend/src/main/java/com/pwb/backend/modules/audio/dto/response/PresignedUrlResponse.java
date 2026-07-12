package com.pwb.backend.modules.audio.dto.response;

import java.time.Instant;

public record PresignedUrlResponse(
        String uploadUrl,
        String s3Key,
        long expiresInSeconds,
        Instant issuedAt,
        long maxSizeBytes,
        String contentType) {
}