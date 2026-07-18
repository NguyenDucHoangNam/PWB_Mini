package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.core.model.OtpCode;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OtpCodeMapper {

    default OtpCodeJpaEntity toEntity(OtpCode domain) {
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

    default OtpCode toDomain(OtpCodeJpaEntity entity) {
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
