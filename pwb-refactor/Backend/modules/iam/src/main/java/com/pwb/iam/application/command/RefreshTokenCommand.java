package com.pwb.iam.application.command;

public record RefreshTokenCommand(
        String rawRefreshToken,
        String clientIp
) {

    public RefreshTokenCommand {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("rawRefreshToken must not be blank");
        }
    }
}