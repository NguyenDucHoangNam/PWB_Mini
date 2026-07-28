package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.OtpPurpose;

import java.util.UUID;

public record VerifyOtpCommand(
        UUID userId,
        OtpPurpose purpose,
        String code
) {

    public VerifyOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose must not be null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
