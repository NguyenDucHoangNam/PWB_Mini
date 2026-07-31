package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHistoryMapperTest {

    private final PasswordHistoryMapper mapper = new PasswordHistoryMapper();

    @Test
    @DisplayName("toEntity should map fields from domain")
    void should_map_to_entity() {
        PasswordHistory history = PasswordHistory.create(UUID.randomUUID(), "hash");

        PasswordHistoryJpaEntity entity = mapper.toEntity(history);

        assertThat(entity.getUserId()).isEqualTo(history.getUserId());
        assertThat(entity.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("toDomain should map back")
    void should_map_to_domain() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        PasswordHistoryJpaEntity entity = PasswordHistoryJpaEntity.builder()
                .userId(userId)
                .passwordHash("hash")
                .build();
        entity.setId(id);
        entity.setCreatedAt(createdAt);

        PasswordHistory history = mapper.toDomain(entity);

        assertThat(history.getId()).isEqualTo(id);
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getPasswordHash()).isEqualTo("hash");
        assertThat(history.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("toEntity with null domain returns null")
    void should_handle_null_domain() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    @DisplayName("toDomain with null entity returns null")
    void should_handle_null_entity() {
        assertThat(mapper.toDomain(null)).isNull();
    }
}