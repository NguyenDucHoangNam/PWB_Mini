package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findActiveByHash(String tokenHash, Instant now);

    int invalidateAllForUser(UUID userId, Instant now);
}
