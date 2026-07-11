package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;
import java.util.UUID;

public record VerifyOtpResponse(
        UUID userId,
        String email,
        String role,
        String accessToken,
        Instant accessTokenExpiresAt
) {}