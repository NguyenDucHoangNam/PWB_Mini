package com.pwb.backend.modules.iam.dto.response;

public record AvatarUploadResponse(
        String avatarUrl,
        long sizeBytes,
        String contentType
) {
}
