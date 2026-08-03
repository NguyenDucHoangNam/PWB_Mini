package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        String refreshToken,
        String accessToken,
        String accessJti,
        Long accessExpiresInSeconds
) {
}