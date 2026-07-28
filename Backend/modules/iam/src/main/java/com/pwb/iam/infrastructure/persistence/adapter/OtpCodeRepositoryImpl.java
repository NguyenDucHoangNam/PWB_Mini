package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpCode.OtpStatus;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.OtpCodeMapper;
import com.pwb.iam.infrastructure.persistence.repository.OtpCodeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OtpCodeRepositoryImpl implements OtpCodeRepository {

    private final OtpCodeJpaRepository otpCodeJpaRepository;
    private final OtpCodeMapper otpCodeMapper;

    @Override
    public OtpCode save(OtpCode otpCode) {
        OtpCodeJpaEntity entity = otpCodeMapper.toEntity(otpCode);
        OtpCodeJpaEntity saved = otpCodeJpaRepository.save(entity);
        return otpCodeMapper.toDomain(saved);
    }

    @Override
    public Optional<OtpCode> findLatestByUserIdAndPurposeAndStatus(UUID userId, OtpPurpose purpose, OtpStatus status) {
        return otpCodeJpaRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, status)
                .map(otpCodeMapper::toDomain);
    }

    @Override
    public int markVerified(UUID id, OtpStatus newStatus, Instant now) {
        return otpCodeJpaRepository.markVerified(id, newStatus, now);
    }

    @Override
    public int markLocked(UUID id, int attempts, Instant now) {
        return otpCodeJpaRepository.markLocked(id, attempts, now);
    }

    @Override
    public int markExpiredFromLocked(UUID id, OtpStatus newStatus, Instant now) {
        return otpCodeJpaRepository.markExpiredFromLocked(id, newStatus, now);
    }
}
