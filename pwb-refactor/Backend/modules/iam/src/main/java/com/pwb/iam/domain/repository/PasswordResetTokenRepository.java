package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findById(UUID tokenId);

    void deleteById(UUID tokenId);
}
