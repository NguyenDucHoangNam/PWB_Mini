package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        String status,
        String avatarUrl,
        String phone,
        Instant deletionRequestedAt
) {
}
