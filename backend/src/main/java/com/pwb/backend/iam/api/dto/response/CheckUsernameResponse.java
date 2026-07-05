package com.pwb.backend.iam.api.dto.response;

public record CheckUsernameResponse(
    String username,
    boolean available
) {}
