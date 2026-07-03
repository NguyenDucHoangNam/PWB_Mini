package com.pwb.backend.modules.iam.api.dto.response;

public record RegisterResponse(
    String username,
    String email,
    String fullName,
    String status
) {}
