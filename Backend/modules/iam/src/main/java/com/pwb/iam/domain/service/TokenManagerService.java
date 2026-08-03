package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.User;
import io.jsonwebtoken.Claims;

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

    ParseResult parseAccessTokenWithResult(String token);

    record AccessTokenInfo(String tokenValue, String jti, java.time.Instant expiresAt, long expiresInSeconds) {
    }

    record RefreshTokenInfo(String rawToken, UUID userId, java.time.Instant expiresAt, Duration ttl) {
    }

    record ParseResult(boolean valid, Claims claims, JwtError error) {
        public static ParseResult success(Claims claims) {
            return new ParseResult(true, claims, null);
        }

        public static ParseResult failure(JwtError error) {
            return new ParseResult(false, null, error);
        }
    }

    enum JwtError {
        MISSING,
        MALFORMED,
        INVALID_SIGNATURE,
        EXPIRED,
        UNSUPPORTED,
        UNKNOWN
    }
}
