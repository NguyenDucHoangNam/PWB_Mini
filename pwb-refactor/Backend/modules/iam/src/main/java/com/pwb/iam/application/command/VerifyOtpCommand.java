package com.pwb.iam.application.command;

import java.util.UUID;

public record VerifyOtpCommand(
        UUID userId,
        String code
) {

    public VerifyOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
