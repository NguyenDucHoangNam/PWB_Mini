package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.User;

import java.time.Duration;
import java.util.UUID;

public interface TokenManagerService {

    AccessTokenInfo issueAccessToken(User user);

    RefreshTokenInfo issueRefreshToken(UUID userId);

    RefreshTokenInfo rotateRefreshToken(String rawToken);

    void blacklistAccessToken(String jti, long expiresInSeconds);

    void revokeRefreshToken(String rawToken);

    void revokeAllRefreshTokensForUser(UUID userId);

    boolean isAccessTokenBlacklisted(String jti);

    boolean isRefreshTokenRevoked(String rawToken);

    record AccessTokenInfo(String tokenValue, String jti, java.time.Instant expiresAt, long expiresInSeconds) {
    }

    record RefreshTokenInfo(String rawToken, UUID userId, java.time.Instant expiresAt, Duration ttl) {
    }
}
