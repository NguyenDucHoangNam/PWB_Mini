package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @NotBlank @Size(max = 1024) String refreshToken,
        String accessToken,
        String accessJti,
        Long accessExpiresInSeconds
) {
}