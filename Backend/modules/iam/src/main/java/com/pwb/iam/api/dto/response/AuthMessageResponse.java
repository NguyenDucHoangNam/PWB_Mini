package com.pwb.iam.api.dto.response;

import java.util.UUID;

public record AuthMessageResponse(
        UUID userId,
        String message
) {

    public static AuthMessageResponse of(UUID userId, String message) {
        return new AuthMessageResponse(userId, message);
    }
}
