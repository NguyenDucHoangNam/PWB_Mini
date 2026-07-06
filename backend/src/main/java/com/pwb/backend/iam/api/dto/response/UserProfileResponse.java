package com.pwb.backend.iam.api.dto.response;

public record UserProfileResponse(
    String username,
    String email,
    String fullName,
    String role,
    String status,
    String avatarUrl,
    String phone
) {}
