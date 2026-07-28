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
