package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;
    private final PasswordResetTokenMapper passwordResetTokenMapper;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = passwordResetTokenMapper.toEntity(token);
        PasswordResetTokenJpaEntity saved = passwordResetTokenJpaRepository.save(entity);
        return passwordResetTokenMapper.toDomain(saved);
    }

    @Override
    public Optional<PasswordResetToken> findActiveByHash(String tokenHash, Instant now) {
        return passwordResetTokenJpaRepository.findActiveByHash(tokenHash, now)
                .map(passwordResetTokenMapper::toDomain);
    }

    @Override
    public int invalidateAllForUser(UUID userId, Instant now) {
        return passwordResetTokenJpaRepository.invalidateAllForUser(userId, now);
    }
}
