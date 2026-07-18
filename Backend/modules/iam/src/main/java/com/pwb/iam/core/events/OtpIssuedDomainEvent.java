package com.pwb.iam.core.events;

import com.pwb.iam.core.model.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

public record OtpIssuedDomainEvent(
        UUID userId,
        String email,
        String otpCode,
        OtpPurpose purpose,
        Instant issuedAt
) {
}
