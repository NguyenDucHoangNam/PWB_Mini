package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.OtpCodeMapper;
import com.pwb.iam.infrastructure.persistence.repository.OtpCodeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OtpCodeRepositoryImpl implements OtpCodeRepository {

    private final OtpCodeJpaRepository otpCodeJpaRepository;
    private final OtpCodeMapper otpCodeMapper;

    @Override
    @Transactional
    public OtpCode save(OtpCode otpCode) {
        UUID id = otpCode.getId();
        OtpCodeJpaEntity target;
        if (id != null) {
            target = otpCodeJpaRepository.findById(id).orElse(null);
            target = otpCodeMapper.toEntity(otpCode, target);
        } else {
            target = otpCodeMapper.toEntity(otpCode, null);
        }
        OtpCodeJpaEntity saved = otpCodeJpaRepository.save(target);
        return otpCodeMapper.toDomain(saved);
    }

    @Override
    public Optional<OtpCode> findActiveByUserAndPurpose(UUID userId, OtpPurpose purpose) {
        return otpCodeJpaRepository
                .findFirstByUserIdAndPurposeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                        userId, purpose, OtpCode.OtpStatus.PENDING)
                .map(otpCodeMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteAllByUserAndPurpose(UUID userId, OtpPurpose purpose) {
        List<OtpCodeJpaEntity> existing = otpCodeJpaRepository
                .findAllByUserIdAndPurposeAndDeletedFalse(userId, purpose);
        for (OtpCodeJpaEntity entity : existing) {
            otpCodeJpaRepository.delete(entity);
        }
    }

    @Override
    public int countIssuedToday(UUID userId, OtpPurpose purpose, Instant since) {
        return (int) otpCodeJpaRepository.countIssuedSince(userId, purpose, since);
    }

    @Override
    public Optional<OtpCode> findById(UUID id) {
        return otpCodeJpaRepository.findById(id).map(otpCodeMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean markLockedIfNotAlready(UUID id, int maxAttempts) {
        return otpCodeJpaRepository.markLockedIfNotAlready(id, maxAttempts) > 0;
    }

    @Override
    @Transactional
    public boolean incrementAttempts(UUID id) {
        return otpCodeJpaRepository.incrementAttempts(id) > 0;
    }
}
