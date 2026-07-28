package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository repository;
    private final PasswordResetTokenMapper mapper;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = mapper.toEntity(token);
        PasswordResetTokenJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PasswordResetToken> findById(UUID tokenId) {
        return repository.findByIdAndDeletedFalse(tokenId).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID tokenId) {
        repository.deleteById(tokenId);
    }
}
