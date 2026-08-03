package com.pwb.iam.domain.event;

import java.util.UUID;

public record AuthSuccessEvent(
        UUID userId,
        String email,
        String clientIp,
        String userAgent
) {
    public static AuthSuccessEvent of(UUID userId, String email, String clientIp, String userAgent) {
        return new AuthSuccessEvent(userId, email, clientIp, userAgent);
    }
}
