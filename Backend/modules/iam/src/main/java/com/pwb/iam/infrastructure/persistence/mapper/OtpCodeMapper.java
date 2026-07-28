package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OtpCodeMapper {

    public OtpCodeJpaEntity toEntity(OtpCode domain) {
        if (domain == null) return null;
        return OtpCodeJpaEntity.builder()
                .userId(domain.getUserId())
                .purpose(domain.getPurpose())
                .status(domain.getStatus())
                .attempts(domain.getAttempts())
                .expiresAt(domain.getExpiresAt())
                .verifiedAt(domain.getVerifiedAt())
                .lockedAt(domain.getLockedAt())
                .build();
    }

    public OtpCode toDomain(OtpCodeJpaEntity entity) {
        if (entity == null) return null;
        return OtpCode.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getPurpose(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getExpiresAt(),
                entity.getVerifiedAt(),
                entity.getLockedAt()
        );
    }
}