package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OtpCodeMapper {

    public OtpCodeJpaEntity toEntity(OtpCode domain) {
        if (domain == null) {
            return null;
        }
        return OtpCodeJpaEntity.builder()
                .userId(domain.getUserId())
                .purpose(domain.getPurpose())
                .codeHash(domain.getCodeHash())
                .status(domain.getStatus())
                .attempts(domain.getAttempts())
                .expiresAt(domain.getExpiresAt())
                .verifiedAt(domain.getVerifiedAt())
                .build();
    }

    public OtpCodeJpaEntity toEntity(OtpCode domain, OtpCodeJpaEntity existing) {
        if (domain == null || existing == null) {
            return toEntity(domain);
        }
        existing.setStatus(domain.getStatus());
        existing.setAttempts(domain.getAttempts());
        existing.setVerifiedAt(domain.getVerifiedAt());
        return existing;
    }

    public OtpCode toDomain(OtpCodeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OtpCode.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getPurpose() == null ? OtpPurpose.REGISTER : entity.getPurpose(),
                entity.getCodeHash(),
                entity.getStatus() == null ? OtpCode.OtpStatus.PENDING : entity.getStatus(),
                entity.getAttempts(),
                entity.getExpiresAt() == null ? Instant.now() : entity.getExpiresAt(),
                entity.getVerifiedAt()
        );
    }

    public UUID getId(OtpCodeJpaEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
