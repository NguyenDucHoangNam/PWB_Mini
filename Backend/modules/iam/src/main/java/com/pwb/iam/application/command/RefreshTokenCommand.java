package com.pwb.iam.application.command;

public record RefreshTokenCommand(
        String refreshToken
) {
    public RefreshTokenCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }
}
