package com.pwb.backend.iam.api.dto.response;

public record UserInfoResponse(
    String username,
    String email,
    String fullName,
    String role,
    String status,
    String oauthProvider
) {}
