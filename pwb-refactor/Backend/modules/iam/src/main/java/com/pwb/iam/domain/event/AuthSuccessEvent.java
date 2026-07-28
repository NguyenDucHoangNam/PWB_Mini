package com.pwb.iam.domain.event;

import java.util.UUID;

public record AuthSuccessEvent(
        UUID userId,
        String email
) {
    public static AuthSuccessEvent of(UUID userId, String email) {
        return new AuthSuccessEvent(userId, email);
    }
}
