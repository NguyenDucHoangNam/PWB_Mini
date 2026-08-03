package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends IamJpaRepository<PasswordResetTokenJpaEntity> {

    Optional<PasswordResetTokenJpaEntity> findByIdAndDeletedFalse(UUID id);

    Optional<PasswordResetTokenJpaEntity> findFirstByTokenHashAndUsedFalseAndDeletedFalseAndExpiresAtAfter(
            String tokenHash, Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE PasswordResetTokenJpaEntity t SET t.used = true, t.usedAt = :now, t.deleted = true, t.updatedAt = :now " +
            "WHERE t.userId = :userId AND t.deleted = false AND t.used = false")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}