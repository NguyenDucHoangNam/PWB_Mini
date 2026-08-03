package com.pwb.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenTest {

    @Test
    @DisplayName("should create unused token with expiresAt")
    void should_create_token() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        PasswordResetToken token = PasswordResetToken.create(userId, "hash-value", expiresAt);

        assertThat(token.getTokenId()).isNotNull();
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getTokenHash()).isEqualTo("hash-value");
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.isUsable()).isTrue();
    }

    @Test
    @DisplayName("should reject null userId")
    void should_reject_null_user() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        assertThatThrownBy(() -> PasswordResetToken.create(null, "hash", expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null or blank tokenHash")
    void should_reject_blank_hash() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> PasswordResetToken.create(userId, null, expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordResetToken.create(userId, "", expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null expiresAt")
    void should_reject_null_expires() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> PasswordResetToken.create(userId, "hash", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markUsed should set used and usedAt")
    void should_mark_used() {
        PasswordResetToken token = PasswordResetToken.create(
                UUID.randomUUID(), "hash", Instant.now().plus(1, ChronoUnit.HOURS));
        Instant now = Instant.now();

        token.markUsed(now);

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(now);
        assertThat(token.isUsable()).isFalse();
    }

    @Test
    @DisplayName("isExpired should report true for past expiresAt")
    void should_report_expired() {
        PasswordResetToken token = PasswordResetToken.create(
                UUID.randomUUID(), "hash", Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(token.isExpired(Instant.now())).isTrue();
        assertThat(token.isUsable()).isFalse();
    }

    @Test
    @DisplayName("rehydrate should restore used flag")
    void should_rehydrate_used() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant usedAt = Instant.now().minus(1, ChronoUnit.MINUTES);

        PasswordResetToken token = PasswordResetToken.rehydrate(
                tokenId, userId, "hash", expiresAt, true, usedAt);

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.getTokenId()).isEqualTo(tokenId);
    }

    @Test
    @DisplayName("rehydrate should preserve unused state")
    void should_rehydrate_unused() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        PasswordResetToken token = PasswordResetToken.rehydrate(
                tokenId, userId, "hash", expiresAt, false, null);

        assertThat(token.isUsed()).isFalse();
        assertThat(token.getUsedAt()).isNull();
    }
}