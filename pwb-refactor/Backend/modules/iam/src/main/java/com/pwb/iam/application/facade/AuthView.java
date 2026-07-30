package com.pwb.iam.application.facade;

import com.pwb.iam.domain.model.AuthNextStep;

import java.util.UUID;

public record AuthView(
        UUID userId,
        String email,
        String fullName,
        String status,
        String role,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        AuthNextStep nextStep
) {

    public static AuthView profileOnly(UUID userId, String email, String fullName, String status, String role) {
        return new AuthView(userId, email, fullName, status, role, null, null, 0L, AuthNextStep.NONE);
    }

    public static AuthView withTokens(UUID userId, String email, String fullName, String status, String role,
                                      String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthView(userId, email, fullName, status, role, accessToken, refreshToken, expiresInSeconds, AuthNextStep.NONE);
    }

    public static AuthView withTokens(UUID userId, String email, String fullName, String status, String role,
                                      String accessToken, String refreshToken, long expiresInSeconds, AuthNextStep nextStep) {
        return new AuthView(userId, email, fullName, status, role, accessToken, refreshToken, expiresInSeconds, nextStep);
    }
}
