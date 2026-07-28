package com.pwb.iam.domain.service;

import java.time.Duration;
import java.util.UUID;

public interface RefreshTokenManager {

    RefreshToken issue(UUID userId);

    RefreshToken rotate(String rawToken);

    void revoke(String rawToken);

    boolean isRevoked(String rawToken);

    record RefreshToken(String rawToken, UUID userId, java.time.Instant expiresAt, Duration ttl) {
    }
}