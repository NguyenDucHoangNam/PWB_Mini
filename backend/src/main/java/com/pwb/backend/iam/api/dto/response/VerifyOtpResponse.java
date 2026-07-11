package com.pwb.backend.iam.api.dto.response;

import com.pwb.backend.iam.api.dto.response.UserInfoResponse;

public record VerifyOtpResponse(
    String accessToken,
    long expiresIn,
    UserInfoResponse user
) {}