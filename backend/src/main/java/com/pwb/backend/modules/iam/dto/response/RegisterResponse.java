package com.pwb.backend.modules.iam.dto.response;

import java.time.Instant;

public record RegisterResponse(
        String email,
        String status,
        Instant expiresAt
) {

    public static RegisterResponse verificationSent(String email, Instant expiresAt) {
        return new RegisterResponse(email, "VERIFICATION_SENT", expiresAt);
    }
}