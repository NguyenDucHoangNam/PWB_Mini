package com.pwb.iam.domain.event;

import java.util.UUID;

public record AuthSuccessEvent(
        UUID userId,
        String email,
        String clientIp
) {
    public static AuthSuccessEvent of(UUID userId, String email) {
        return new AuthSuccessEvent(userId, email, null);
    }

    public static AuthSuccessEvent of(UUID userId, String email, String clientIp) {
        return new AuthSuccessEvent(userId, email, clientIp);
    }
}