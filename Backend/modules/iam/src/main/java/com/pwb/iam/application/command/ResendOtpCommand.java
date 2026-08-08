package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.OtpPurpose;

import java.util.UUID;

public record ResendOtpCommand(
        UUID userId,
        OtpPurpose purpose,
        String locale
) {

    public ResendOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (purpose == null) {
            purpose = OtpPurpose.REGISTER;
        }
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
    }

    public ResendOtpCommand(UUID userId, OtpPurpose purpose) {
        this(userId, purpose, "vi");
    }
}
