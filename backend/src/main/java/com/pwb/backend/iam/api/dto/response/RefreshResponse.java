package com.pwb.backend.iam.api.dto.response;

public record RefreshResponse(
    String accessToken,
    long expiresIn
) {}
