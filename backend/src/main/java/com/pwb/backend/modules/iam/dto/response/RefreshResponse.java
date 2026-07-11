package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;

public record RefreshResponse(
        String accessToken,
        long expiresIn,
        Instant accessTokenExpiresAt,
        String refreshToken,
        long refreshTokenMaxAgeSeconds) {
}
