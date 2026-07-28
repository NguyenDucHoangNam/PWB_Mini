package com.pwb.iam.application.facade;

import java.util.UUID;

public record AuthView(
        UUID userId,
        String email,
        String username,
        String status,
        String role,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {

    public static AuthView profileOnly(UUID userId, String email, String username, String status, String role) {
        return new AuthView(userId, email, username, status, role, null, null, 0L);
    }

    public static AuthView withTokens(UUID userId, String email, String username, String status, String role,
                                      String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthView(userId, email, username, status, role, accessToken, refreshToken, expiresInSeconds);
    }
}