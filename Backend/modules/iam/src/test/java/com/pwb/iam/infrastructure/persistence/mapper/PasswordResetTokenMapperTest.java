package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenMapperTest {

    private final PasswordResetTokenMapper mapper = new PasswordResetTokenMapper();

    @Test
    @DisplayName("toEntity should map fields from domain")
    void should_map_to_entity() {
        Instant expiresAt = Instant.parse("2026-12-31T00:00:00Z");
        PasswordResetToken token = PasswordResetToken.create(UUID.randomUUID(), "hash", expiresAt);

        PasswordResetTokenJpaEntity entity = mapper.toEntity(token);

        assertThat(entity.getUserId()).isEqualTo(token.getUserId());
        assertThat(entity.getTokenHash()).isEqualTo("hash");
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.isUsed()).isFalse();
        assertThat(entity.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("toEntity should preserve used flag when set")
    void should_preserve_used_flag() {
        PasswordResetToken token = PasswordResetToken.create(UUID.randomUUID(), "hash", Instant.now().plusSeconds(60));
        token.markUsed(Instant.now());

        PasswordResetTokenJpaEntity entity = mapper.toEntity(token);

        assertThat(entity.isUsed()).isTrue();
        assertThat(entity.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("toDomain should map back including used flag")
    void should_map_to_domain() {
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-12-31T00:00:00Z");
        Instant usedAt = Instant.parse("2026-12-30T00:00:00Z");
        PasswordResetTokenJpaEntity entity = PasswordResetTokenJpaEntity.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(expiresAt)
                .used(true)
                .usedAt(usedAt)
                .build();
        entity.setId(id);

        PasswordResetToken token = mapper.toDomain(entity);

        assertThat(token.getTokenId()).isEqualTo(id);
        assertThat(token.getTokenHash()).isEqualTo("hash");
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(usedAt);
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