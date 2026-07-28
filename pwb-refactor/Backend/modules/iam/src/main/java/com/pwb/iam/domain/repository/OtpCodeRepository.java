package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository {

    OtpCode save(OtpCode otpCode);

    Optional<OtpCode> findActiveByUserAndPurpose(UUID userId, OtpPurpose purpose);

    void deleteAllByUserAndPurpose(UUID userId, OtpPurpose purpose);
}
