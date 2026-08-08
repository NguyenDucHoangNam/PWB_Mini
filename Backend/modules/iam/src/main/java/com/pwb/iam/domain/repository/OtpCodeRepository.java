package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository {

    OtpCode save(OtpCode otpCode);

    Optional<OtpCode> findActiveByUserAndPurpose(UUID userId, OtpPurpose purpose);

    /**
     * Retires every still-pending code for the pair, so only the newest one can be redeemed.
     * <p>
     * Deliberately an update rather than a delete: {@link #countIssuedSince} derives the daily
     * quota from these rows, and removing them would reset the quota on every resend.
     */
    void invalidateAllByUserAndPurpose(UUID userId, OtpPurpose purpose);

    int countIssuedSince(UUID userId, OtpPurpose purpose, Instant since);

    Optional<OtpCode> findById(UUID id);

    boolean markLockedIfNotAlready(UUID id, int maxAttempts);

    boolean incrementAttempts(UUID id);
}
