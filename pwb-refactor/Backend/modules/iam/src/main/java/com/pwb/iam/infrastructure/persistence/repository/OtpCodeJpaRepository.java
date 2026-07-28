package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeJpaRepository extends IamJpaRepository<OtpCodeJpaEntity> {

    @EntityGraph(attributePaths = {})
    Optional<OtpCodeJpaEntity> findFirstByUserIdAndPurposeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, OtpPurpose purpose, OtpCode.OtpStatus status);

    List<OtpCodeJpaEntity> findAllByUserIdAndPurposeAndDeletedFalse(UUID userId, OtpPurpose purpose);

    long deleteByUserIdAndPurpose(UUID userId, OtpPurpose purpose);
}
