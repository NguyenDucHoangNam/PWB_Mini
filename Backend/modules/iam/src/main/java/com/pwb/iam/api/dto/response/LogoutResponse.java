package com.pwb.iam.api.dto.response;

import java.util.UUID;

public record LogoutResponse(UUID userId, String message) {

    public static LogoutResponse of(UUID userId, String message) {
        return new LogoutResponse(userId, message);
    }
}