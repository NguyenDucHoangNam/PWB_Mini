package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        Instant accessTokenExpiresAt,
        UserInfo user,
        String refreshToken,
        long refreshTokenMaxAgeSeconds,
        String redirectTo,
        String redirectEmail) {
}
