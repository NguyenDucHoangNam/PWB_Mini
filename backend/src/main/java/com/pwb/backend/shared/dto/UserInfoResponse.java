package com.pwb.backend.shared.dto;

public record UserInfoResponse(
    String username,
    String email,
    String fullName,
    String role,
    String status,
    String oauthProvider
) {}