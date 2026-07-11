package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;

public record ResendOtpResponse(
        String email,
        Instant sentAt
) {

    public static ResendOtpResponse from(String email) {
        return new ResendOtpResponse(email, Instant.now());
    }
}