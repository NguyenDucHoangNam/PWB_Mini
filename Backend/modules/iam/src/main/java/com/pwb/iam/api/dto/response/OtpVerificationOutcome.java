package com.pwb.iam.api.dto.response;

import com.pwb.iam.core.model.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

public record OtpVerificationOutcome(
        Outcome outcome,
        UUID userId,
        String email,
        OtpPurpose purpose,
        Instant verifiedAt
) {

    public enum Outcome {
        OK,
        INVALID,
        EXPIRED_OR_MISSING,
        LOCKED
    }

    public boolean isOk() {
        return outcome == Outcome.OK;
    }
}
