package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.core.model.PasswordResetToken;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PasswordResetTokenMapper {

    default PasswordResetTokenJpaEntity toEntity(PasswordResetToken domain) {
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

    default PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
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