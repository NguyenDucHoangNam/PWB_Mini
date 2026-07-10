package com.pwb.backend.iam.api.dto.response;

import com.pwb.backend.shared.dto.UserInfoResponse;

public record VerifyOtpResponse(
    String accessToken,
    long expiresIn,
    UserInfoResponse user
) {}