package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends IamJpaRepository<PasswordResetTokenJpaEntity> {

    Optional<PasswordResetTokenJpaEntity> findByIdAndDeletedFalse(UUID id);
}
