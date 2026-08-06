package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository repository;
    private final PasswordResetTokenMapper mapper;

    @Override
    @Transactional
    public PasswordResetToken save(PasswordResetToken token) {
        UUID id = token.getTokenId();
        PasswordResetTokenJpaEntity target = id == null
                ? mapper.toEntity(token, null)
                : mapper.toEntity(token, repository.findById(id).orElse(null));
        return mapper.toDomain(repository.save(target));
    }

    @Override
    public Optional<PasswordResetToken> findById(UUID tokenId) {
        return repository.findByIdAndDeletedFalse(tokenId).map(mapper::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findActiveByHash(String tokenHash, Instant now) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByTokenHashAndUsedFalseAndDeletedFalseAndExpiresAtAfter(tokenHash, now)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public int invalidateAllForUser(UUID userId, Instant now) {
        return repository.invalidateAllForUser(userId, now);
    }

    @Override
    public void deleteById(UUID tokenId) {
        repository.deleteById(tokenId);
    }
}