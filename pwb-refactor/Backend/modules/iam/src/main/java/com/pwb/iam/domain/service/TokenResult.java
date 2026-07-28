package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.UserStatus;

public record TokenResult(
        String accessToken,
        long expiresIn,
        UserStatus status
) {
}
