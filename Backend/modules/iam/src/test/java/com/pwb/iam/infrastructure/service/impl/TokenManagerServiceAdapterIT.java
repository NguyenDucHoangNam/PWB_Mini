package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService.AccessTokenInfo;
import com.pwb.iam.domain.exception.RefreshTokenExpiredException;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.domain.service.TokenManagerService.RefreshTokenInfo;
import com.pwb.iam.infrastructure.crypto.Hashes;
import com.pwb.iam.testsupport.AbstractRedisIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenManagerServiceAdapter — Redis integration")
class TokenManagerServiceAdapterIT extends AbstractRedisIT {

    @Autowired private TokenManagerServiceAdapter adapter;
    @Autowired private StringRedisTemplate redis;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        cleanRedis();
    }

    @AfterEach
    void cleanUp() {
        cleanRedis();
    }

    private void cleanRedis() {
        redis.delete(redis.keys("iam:*"));
    }

    /**
     * Whether the adapter would still accept this refresh token.
     *
     * <p>Reads the stored entry rather than calling the adapter, because the only public way to ask
     * is {@code rotateRefreshToken}, and rotating <em>consumes</em> the token — a probe written that
     * way would change the state it is supposed to be observing, and the assertions before a revoke
     * below would themselves invalidate the token. The key scheme mirrors the adapter: a token is
     * held under its SHA-256, never in the clear, so this also pins that the raw value never
     * reaches Redis.
     */
    private boolean refreshTokenLives(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey("iam:refresh:token:" + Hashes.sha256Hex(rawToken)));
    }

    private User sampleUser() {
        return User.createLocal(
                EmailAddress.of("user@example.com"),
                Password.fromHash("$2a$10$dummy.hash.for.test.only.not.real.bcrypt.value"),
                "Test User",
                RoleName.USER
        );
    }

    @Test
    @DisplayName("should_issue_access_token_with_jti_and_expiration")
    void should_issue_access_token_with_jti_and_expiration() {
        User user = sampleUser();

        AccessTokenInfo info = adapter.issueAccessToken(user);

        assertThat(info).isNotNull();
        assertThat(info.tokenValue()).isNotBlank();
        assertThat(info.jti()).isNotBlank();
        assertThat(info.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(info.expiresInSeconds()).isPositive();
    }

    @Test
    @DisplayName("should_issue_refresh_token_and_persist_in_redis")
    void should_issue_refresh_token_and_persist_in_redis() {
        RefreshTokenInfo info = adapter.issueRefreshToken(userId);

        assertThat(info).isNotNull();
        assertThat(info.rawToken()).isNotBlank();
        assertThat(info.userId()).isEqualTo(userId);

        assertThat(refreshTokenLives(info.rawToken())).isTrue();
    }

    @Test
    @DisplayName("should_blacklist_access_token_and_check")
    void should_blacklist_access_token_and_check() {
        String jti = UUID.randomUUID().toString();

        assertThat(adapter.isAccessTokenBlacklisted(jti)).isFalse();

        adapter.blacklistAccessToken(jti, 60);

        assertThat(adapter.isAccessTokenBlacklisted(jti)).isTrue();
    }

    @Test
    @DisplayName("should_not_blacklist_when_jti_null_or_blank")
    void should_not_blacklist_when_jti_null_or_blank() {
        adapter.blacklistAccessToken(null, 60);
        adapter.blacklistAccessToken("", 60);
        adapter.blacklistAccessToken("   ", 60);

        assertThat(adapter.isAccessTokenBlacklisted("anything")).isFalse();
    }

    @Test
    @DisplayName("should_rotate_refresh_token_and_invalidate_old")
    void should_rotate_refresh_token_and_invalidate_old() {
        RefreshTokenInfo original = adapter.issueRefreshToken(userId);

        RefreshTokenInfo rotated = adapter.rotateRefreshToken(original.rawToken());

        assertThat(rotated).isNotNull();
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.rawToken()).isNotEqualTo(original.rawToken());

        assertThat(refreshTokenLives(original.rawToken())).isFalse();
        assertThat(refreshTokenLives(rotated.rawToken())).isTrue();
    }

    @Test
    @DisplayName("should_throw_when_rotating_invalid_refresh_token")
    void should_throw_when_rotating_invalid_refresh_token() {
        // A token nobody ever issued is indistinguishable from one that timed out, and is reported
        // as expired. Only a token this adapter previously retired is treated as a replay — that
        // path is REFRESH_TOKEN_REUSED and revokes the account's sessions.
        assertThatThrownBy(() -> adapter.rotateRefreshToken("invalid-token"))
                .isInstanceOf(RefreshTokenExpiredException.class);
    }

    @Test
    @DisplayName("should_revoke_refresh_token")
    void should_revoke_refresh_token() {
        RefreshTokenInfo info = adapter.issueRefreshToken(userId);
        assertThat(refreshTokenLives(info.rawToken())).isTrue();

        adapter.revokeRefreshToken(info.rawToken());

        assertThat(refreshTokenLives(info.rawToken())).isFalse();
    }

    @Test
    @DisplayName("should_revoke_all_refresh_tokens_for_user")
    void should_revoke_all_refresh_tokens_for_user() {
        RefreshTokenInfo t1 = adapter.issueRefreshToken(userId);
        RefreshTokenInfo t2 = adapter.issueRefreshToken(userId);

        adapter.revokeAllRefreshTokensForUser(userId);

        assertThat(refreshTokenLives(t1.rawToken())).isFalse();
        assertThat(refreshTokenLives(t2.rawToken())).isFalse();
    }

    @Test
    @DisplayName("should_reject_blank_or_null_refresh_token")
    void should_reject_blank_or_null_refresh_token() {
        // Rejected before the value is hashed or looked up, so a caller that lost its cookie can
        // never reach Redis with an empty key.
        assertThatThrownBy(() -> adapter.rotateRefreshToken(null))
                .isInstanceOf(RefreshTokenInvalidException.class);
        assertThatThrownBy(() -> adapter.rotateRefreshToken(""))
                .isInstanceOf(RefreshTokenInvalidException.class);
        assertThatThrownBy(() -> adapter.rotateRefreshToken("   "))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }
}