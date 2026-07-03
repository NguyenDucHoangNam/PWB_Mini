package com.pwb.backend.modules.iam.api.dto.response;

public record CheckUsernameResponse(
    String username,
    boolean available
) {}
