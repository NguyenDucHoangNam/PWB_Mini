package com.pwb.backend.iam.api.dto.response;

import com.pwb.backend.shared.dto.UserInfoResponse;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    UserInfoResponse user
) {}