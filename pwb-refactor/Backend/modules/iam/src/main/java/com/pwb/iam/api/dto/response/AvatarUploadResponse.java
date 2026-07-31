package com.pwb.iam.api.dto.response;

import java.util.UUID;

public record AvatarUploadResponse(
        UUID userId,
        String avatarUrl,
        String message
) {

    public static AvatarUploadResponse of(UUID userId, String avatarUrl, String message) {
        return new AvatarUploadResponse(userId, avatarUrl, message);
    }
}
