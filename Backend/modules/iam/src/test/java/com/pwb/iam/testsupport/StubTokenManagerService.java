package com.pwb.iam.testsupport;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService;
import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class StubTokenManagerService implements TokenManagerService {

    private final AccessTokenInfo accessToken;
    private final RefreshTokenInfo refreshToken;
    private final List<UUID> revokedAllForUsers = new ArrayList<>();

    public StubTokenManagerService() {
        this.accessToken = new AccessTokenInfo(
                "access-token-stub",
                "jti-stub",
                Instant.now().plusSeconds(900L),
                900L
        );
        this.refreshToken = new RefreshTokenInfo(
                "refresh-token-stub",
                UUID.randomUUID(),
                Instant.now().plus(Duration.ofDays(14)),
                Duration.ofDays(14)
        );
    }

    public StubTokenManagerService(UUID userId) {
        this.accessToken = new AccessTokenInfo(
                "access-token-stub",
                "jti-stub",
                Instant.now().plusSeconds(900L),
                900L
        );
        this.refreshToken = new RefreshTokenInfo(
                "refresh-token-stub",
                userId,
                Instant.now().plus(Duration.ofDays(14)),
                Duration.ofDays(14)
        );
    }

    @Override
    public AccessTokenInfo issueAccessToken(User user) {
        return accessToken;
    }

    @Override
    public RefreshTokenInfo issueRefreshToken(UUID userId) {
        return refreshToken;
    }

    @Override
    public RefreshTokenInfo rotateRefreshToken(String rawToken) {
        return refreshToken;
    }

    @Override
    public void blacklistAccessToken(String jti, long expiresInSeconds) {
    }

    @Override
    public void revokeRefreshToken(String rawToken) {
    }

    @Override
    public void revokeAllRefreshTokensForUser(UUID userId) {
        revokedAllForUsers.add(userId);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return false;
    }

    /**
     * Accounts whose sessions were dropped wholesale. Recorded because "changing a password kicks
     * every other session" is a security property several use cases owe, and this stub is passed
     * where a Mockito mock would otherwise be — {@code verify()} does not work on it.
     */
    public List<UUID> revokedAllForUsers() {
        return Collections.unmodifiableList(revokedAllForUsers);
    }

    @Override
    public ParseResult parseAccessTokenWithResult(String token) {
        return ParseResult.failure(JwtError.MISSING);
    }

    public AccessTokenInfo accessToken() {
        return accessToken;
    }

    public RefreshTokenInfo refreshToken() {
        return refreshToken;
    }

    @SuppressWarnings("unused")
    private static Claims emptyClaims() {
        return null;
    }
}
