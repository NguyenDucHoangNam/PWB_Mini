package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository {

    OtpCode save(OtpCode otpCode);

    Optional<OtpCode> findActiveByUserAndPurpose(UUID userId, OtpPurpose purpose);

    void deleteAllByUserAndPurpose(UUID userId, OtpPurpose purpose);

    int countIssuedToday(UUID userId, OtpPurpose purpose, Instant since);

    Optional<OtpCode> findById(UUID id);

    boolean markLockedIfNotAlready(UUID id, int maxAttempts);

    boolean incrementAttempts(UUID id);
}
