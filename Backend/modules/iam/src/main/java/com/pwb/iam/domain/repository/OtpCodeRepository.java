package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpCode.OtpStatus;
import com.pwb.iam.domain.model.OtpPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository {

    OtpCode save(OtpCode otpCode);

    Optional<OtpCode> findLatestByUserIdAndPurposeAndStatus(UUID userId, OtpPurpose purpose, OtpStatus status);

    int markVerified(UUID id, OtpStatus newStatus, Instant now);

    int markLocked(UUID id, int attempts, Instant now);

    int markExpiredFromLocked(UUID id, OtpStatus newStatus, Instant now);
}
