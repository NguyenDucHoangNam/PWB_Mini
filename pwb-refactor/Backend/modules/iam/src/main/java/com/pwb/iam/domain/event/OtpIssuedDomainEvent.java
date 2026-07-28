package com.pwb.iam.domain.event;

import com.pwb.iam.domain.model.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

public record OtpIssuedDomainEvent(
        UUID userId,
        String email,
        OtpPurpose purpose,
        Instant expiresAt
) {
}
