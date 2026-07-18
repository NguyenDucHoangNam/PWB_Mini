package com.pwb.iam.infrastructure.security.service;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {

    void store(String jti, UUID userId, long ttlSeconds);

    Optional<UUID> findUserId(String jti);

    void revoke(String jti);

    void revokeAllForUser(UUID userId);
}
