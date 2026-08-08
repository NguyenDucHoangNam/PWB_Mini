package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.OtpPurpose;

import java.util.UUID;

public record VerifyOtpCommand(
        UUID userId,
        String code,
        OtpPurpose purpose,
        String clientIp
) {

    public VerifyOtpCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (purpose == null) {
            purpose = OtpPurpose.REGISTER;
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
    }

    public VerifyOtpCommand(UUID userId, String code, String clientIp) {
        this(userId, code, OtpPurpose.REGISTER, clientIp);
    }

    public VerifyOtpCommand(UUID userId, String code) {
        this(userId, code, OtpPurpose.REGISTER, "unknown");
    }
}
