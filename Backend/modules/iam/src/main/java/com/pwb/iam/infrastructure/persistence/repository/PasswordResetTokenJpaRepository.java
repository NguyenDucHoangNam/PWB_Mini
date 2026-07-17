package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends IamJpaRepository<PasswordResetTokenJpaEntity> {

    @Query("""
            SELECT t FROM PasswordResetTokenJpaEntity t
            WHERE t.tokenHash = :tokenHash
              AND t.used = false
              AND t.expiresAt > :now
            """)
    Optional<PasswordResetTokenJpaEntity> findActiveByHash(@Param("tokenHash") String tokenHash,
                                                           @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetTokenJpaEntity t SET t.used = true, t.usedAt = :now WHERE t.userId = :userId AND t.used = false")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}