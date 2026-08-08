package com.pwb.iam.application.command;

import java.util.UUID;

public record LogoutCommand(
        UUID userId,
        String rawRefreshToken,
        String accessJti,
        long accessExpiresInSeconds,
        String clientIp,
        String userAgent
) {
}
