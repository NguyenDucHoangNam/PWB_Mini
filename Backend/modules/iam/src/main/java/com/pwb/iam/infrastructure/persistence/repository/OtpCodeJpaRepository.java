package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeJpaRepository extends IamJpaRepository<OtpCodeJpaEntity> {

    Optional<OtpCodeJpaEntity> findFirstByUserIdAndPurposeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, OtpPurpose purpose, OtpCode.OtpStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpCodeJpaEntity o SET o.status = 'EXPIRED' " +
            "WHERE o.userId = :userId AND o.purpose = :purpose AND o.status = 'PENDING' AND o.deleted = false")
    int invalidatePending(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);

    /**
     * Locks the code once its counter has reached the ceiling. The {@code attempts >= :maxAttempts}
     * predicate is what makes this a lockout; matching on {@code <} would lock every pending code
     * on the first try.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpCodeJpaEntity o SET o.status = 'LOCKED' " +
            "WHERE o.id = :id AND o.status = 'PENDING' AND o.attempts >= :maxAttempts")
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
