package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenMapper {

    public PasswordResetTokenJpaEntity toEntity(PasswordResetToken domain) {
        if (domain == null) {
            return null;
        }
        return PasswordResetTokenJpaEntity.builder()
                .userId(domain.getUserId())
                .tokenHash(domain.getTokenHash())
                .expiresAt(domain.getExpiresAt())
                .used(domain.isUsed())
                .usedAt(domain.getUsedAt())
                .build();
    }

    /**
     * Mutates the row the token was loaded from rather than building a fresh entity. The builder
     * above cannot carry an id — {@code id} is generated — so re-saving a rehydrated token through
     * it inserts a second row and leaves the original behind, still {@code used = false} and still
     * matched by {@code findActiveByHash}. That is what made a spent reset link keep working for
     * the rest of its TTL.
     */
    public PasswordResetTokenJpaEntity toEntity(PasswordResetToken domain, PasswordResetTokenJpaEntity existing) {
        if (domain == null || existing == null) {
            return toEntity(domain);
        }
        existing.setUsed(domain.isUsed());
        existing.setUsedAt(domain.getUsedAt());
        existing.setExpiresAt(domain.getExpiresAt());
        return existing;
    }

    public PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PasswordResetToken.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.isUsed(),
                entity.getUsedAt()
        );
    }
}
