package com.pwb.iam.api.dto.response;

import com.pwb.iam.domain.model.OtpPurpose;

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

    public static OtpVerificationOutcome ok(UUID userId, String email, OtpPurpose purpose, Instant verifiedAt) {
        return new OtpVerificationOutcome(Outcome.OK, userId, email, purpose, verifiedAt);
    }

    public static OtpVerificationOutcome invalid(UUID userId, String email, OtpPurpose purpose) {
        return new OtpVerificationOutcome(Outcome.INVALID, userId, email, purpose, null);
    }

    public static OtpVerificationOutcome expiredOrMissing(UUID userId, String email, OtpPurpose purpose) {
        return new OtpVerificationOutcome(Outcome.EXPIRED_OR_MISSING, userId, email, purpose, null);
    }

    public static OtpVerificationOutcome locked(UUID userId, String email, OtpPurpose purpose) {
        return new OtpVerificationOutcome(Outcome.LOCKED, userId, email, purpose, null);
    }
}
