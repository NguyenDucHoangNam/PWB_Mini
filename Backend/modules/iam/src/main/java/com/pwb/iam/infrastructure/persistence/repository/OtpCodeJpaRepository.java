package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.core.model.OtpCode.OtpStatus;
import com.pwb.iam.core.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeJpaRepository extends IamJpaRepository<OtpCodeJpaEntity> {

    @Query("""
            SELECT o FROM OtpCodeJpaEntity o
            WHERE o.userId = :userId
              AND o.purpose = :purpose
              AND o.status = :status
            ORDER BY o.createdAt DESC
            """)
    Optional<OtpCodeJpaEntity> findLatestByUserIdAndPurposeAndStatus(
            @Param("userId") UUID userId,
            @Param("purpose") OtpPurpose purpose,
            @Param("status") OtpStatus status);

    @Modifying
    @Query("""
            UPDATE OtpCodeJpaEntity o
            SET o.status = :newStatus, o.verifiedAt = :now
            WHERE o.id = :id AND o.status = com.pwb.iam.core.model.OtpCode.OtpStatus.PENDING
            """)
    int markVerified(@Param("id") UUID id,
                     @Param("newStatus") OtpStatus newStatus,
                     @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE OtpCodeJpaEntity o
            SET o.status = com.pwb.iam.core.model.OtpCode.OtpStatus.LOCKED,
                o.lockedAt = :now,
                o.attempts = :attempts
            WHERE o.id = :id
            """)
    int markLocked(@Param("id") UUID id,
                   @Param("attempts") int attempts,
                   @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE OtpCodeJpaEntity o
            SET o.status = :newStatus,
                o.verifiedAt = :now
            WHERE o.id = :id
              AND o.status = com.pwb.iam.core.model.OtpCode.OtpStatus.LOCKED
            """)
    int markExpiredFromLocked(@Param("id") UUID id,
                              @Param("newStatus") OtpStatus newStatus,
                              @Param("now") Instant now);
}
