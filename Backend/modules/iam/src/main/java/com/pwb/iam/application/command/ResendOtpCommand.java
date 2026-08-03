package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.OtpPurpose;

import java.util.UUID;

public record ResendOtpCommand(
        UUID userId,
        OtpPurpose purpose
) {

    public ResendOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose must not be null");
        }
    }
}
