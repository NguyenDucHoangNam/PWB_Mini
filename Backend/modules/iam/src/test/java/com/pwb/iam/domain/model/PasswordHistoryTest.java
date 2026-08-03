package com.pwb.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHistoryTest {

    @Test
    @DisplayName("should create history entry with random id and createdAt")
    void should_create_entry() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        PasswordHistory history = PasswordHistory.create(userId, "hashed:pwd");

        assertThat(history.getId()).isNotNull();
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getPasswordHash()).isEqualTo("hashed:pwd");
        assertThat(history.getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("should reject null userId")
    void should_reject_null_user() {
        assertThatThrownBy(() -> PasswordHistory.create(null, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null or blank password hash")
    void should_reject_blank_hash() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> PasswordHistory.create(userId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHistory.create(userId, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHistory.create(userId, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MAX_HISTORY_SIZE should be 5")
    void should_expose_max_history_size() {
        assertThat(PasswordHistory.MAX_HISTORY_SIZE).isEqualTo(5);
    }

    @Test
    @DisplayName("rehydrate should preserve provided fields")
    void should_rehydrate() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        PasswordHistory history = PasswordHistory.rehydrate(id, userId, "hash", createdAt);

        assertThat(history.getId()).isEqualTo(id);
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getPasswordHash()).isEqualTo("hash");
        assertThat(history.getCreatedAt()).isEqualTo(createdAt);
    }
}