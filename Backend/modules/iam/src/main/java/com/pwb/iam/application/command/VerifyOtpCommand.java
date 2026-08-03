package com.pwb.iam.application.command;

import java.util.UUID;

public record VerifyOtpCommand(
        UUID userId,
        String code,
        String clientIp
) {

    public VerifyOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
    }

    public VerifyOtpCommand(UUID userId, String code) {
        this(userId, code, "unknown");
    }
}