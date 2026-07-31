package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeJpaRepository extends IamJpaRepository<OtpCodeJpaEntity> {

    @EntityGraph(attributePaths = {})
    Optional<OtpCodeJpaEntity> findFirstByUserIdAndPurposeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, OtpPurpose purpose, OtpCode.OtpStatus status);

    List<OtpCodeJpaEntity> findAllByUserIdAndPurposeAndDeletedFalse(UUID userId, OtpPurpose purpose);

    long deleteByUserIdAndPurpose(UUID userId, OtpPurpose purpose);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpCodeJpaEntity o SET o.status = 'LOCKED', o.attempts = :maxAttempts " +
            "WHERE o.id = :id AND o.status = 'PENDING' AND o.attempts < :maxAttempts")
    int markLockedIfNotAlready(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpCodeJpaEntity o SET o.attempts = o.attempts + 1 " +
            "WHERE o.id = :id AND o.status = 'PENDING'")
    int incrementAttempts(@Param("id") UUID id);

    @Query("SELECT COUNT(o) FROM OtpCodeJpaEntity o " +
            "WHERE o.userId = :userId AND o.purpose = :purpose AND o.createdAt >= :since")
    long countIssuedSince(@Param("userId") UUID userId,
                          @Param("purpose") OtpPurpose purpose,
                          @Param("since") Instant since);
}
