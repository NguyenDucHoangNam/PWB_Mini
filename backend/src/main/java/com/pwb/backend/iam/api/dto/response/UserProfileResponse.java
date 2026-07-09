package com.pwb.backend.iam.api.dto.response;

import java.time.Instant;

public record UserProfileResponse(
    String username,
    String email,
    String fullName,
    String role,
    String status,
    String avatarUrl,
    String phone,
    String oauthProvider,
    Instant deletionRequestedAt
) {}