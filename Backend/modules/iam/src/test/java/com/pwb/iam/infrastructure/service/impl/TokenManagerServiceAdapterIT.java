package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService.AccessTokenInfo;
import com.pwb.iam.domain.service.TokenManagerService.RefreshTokenInfo;
import com.pwb.iam.testsupport.AbstractRedisIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(adapter.isRefreshTokenRevoked(info.rawToken())).isFalse();
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

        assertThat(adapter.isRefreshTokenRevoked(original.rawToken())).isTrue();
        assertThat(adapter.isRefreshTokenRevoked(rotated.rawToken())).isFalse();
    }

    @Test
    @DisplayName("should_throw_when_rotating_invalid_refresh_token")
    void should_throw_when_rotating_invalid_refresh_token() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> adapter.rotateRefreshToken("invalid-token")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_revoke_refresh_token")
    void should_revoke_refresh_token() {
        RefreshTokenInfo info = adapter.issueRefreshToken(userId);
        assertThat(adapter.isRefreshTokenRevoked(info.rawToken())).isFalse();

        adapter.revokeRefreshToken(info.rawToken());

        assertThat(adapter.isRefreshTokenRevoked(info.rawToken())).isTrue();
    }

    @Test
    @DisplayName("should_revoke_all_refresh_tokens_for_user")
    void should_revoke_all_refresh_tokens_for_user() {
        RefreshTokenInfo t1 = adapter.issueRefreshToken(userId);
        RefreshTokenInfo t2 = adapter.issueRefreshToken(userId);

        adapter.revokeAllRefreshTokensForUser(userId);

        assertThat(adapter.isRefreshTokenRevoked(t1.rawToken())).isTrue();
        assertThat(adapter.isRefreshTokenRevoked(t2.rawToken())).isTrue();
    }

    @Test
    @DisplayName("should_return_true_when_refresh_token_blank_or_null")
    void should_return_true_when_refresh_token_blank_or_null() {
        assertThat(adapter.isRefreshTokenRevoked(null)).isTrue();
        assertThat(adapter.isRefreshTokenRevoked("")).isTrue();
        assertThat(adapter.isRefreshTokenRevoked("   ")).isTrue();
    }
}