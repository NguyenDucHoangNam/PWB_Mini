package com.pwb.iam.domain.service;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenManager {

    void store(String jti, UUID userId, long ttlSeconds);

    Optional<UUID> findUserIdByJti(String jti);

    void revoke(String jti);

    void revokeAllForUser(UUID userId);
}
