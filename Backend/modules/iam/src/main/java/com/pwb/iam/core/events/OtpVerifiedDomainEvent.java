package com.pwb.iam.core.events;

import com.pwb.iam.core.model.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

public record OtpVerifiedDomainEvent(
        UUID userId,
        String email,
        OtpPurpose purpose,
        Instant verifiedAt
) {
}
