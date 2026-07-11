package com.pwb.backend.audio.internal.application.helper;

import java.time.Instant;

public record UploadClaim(
    String userId,
    long expectedSizeBytes,
    String expectedContentType,
    Instant issuedAt
) {}
