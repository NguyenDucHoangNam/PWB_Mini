package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.audit.AuditEventType;
import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.iam.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogMapperTest {

    private final AuditLogMapper mapper = new AuditLogMapper();

    @Test
    @DisplayName("toEntity should map fields from domain")
    void should_map_to_entity() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
        AuditLogEntry entry = new AuditLogEntry(eventId, AuditEventType.LOGIN_SUCCESS,
                UUID.randomUUID(), "user@example.com", null, "10.0.0.1", "ua",
                true, null, Map.of("k", "v"), occurredAt);

        AuditLogJpaEntity entity = mapper.toEntity(entry);

        assertThat(entity.getId()).isEqualTo(eventId);
        assertThat(entity.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(entity.getActorEmail()).isEqualTo("user@example.com");
        assertThat(entity.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(entity.isSuccess()).isTrue();
        assertThat(entity.getMetadata()).containsEntry("k", "v");
        assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("toEntity should generate random id when null")
    void should_generate_id_when_null() {
        AuditLogEntry entry = AuditLogEntry.of(AuditEventType.LOGIN_FAILED, null, "user@example.com", "10.0.0.1", false, "bad");

        AuditLogJpaEntity entity = mapper.toEntity(entry);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getFailureReason()).isEqualTo("bad");
    }

    @Test
    @DisplayName("toDomain should map back")
    void should_map_to_domain() {
        UUID id = UUID.randomUUID();
        AuditLogJpaEntity entity = AuditLogJpaEntity.builder()
                .id(id)
                .eventType(AuditEventType.LOGOUT)
                .actorId(UUID.randomUUID())
                .actorEmail("user@example.com")
                .clientIp("10.0.0.1")
                .userAgent("ua")
                .success(true)
                .metadata(Map.of())
                .occurredAt(Instant.now())
                .build();

        AuditLogEntry entry = mapper.toDomain(entity);

        assertThat(entry.eventId()).isEqualTo(id);
        assertThat(entry.eventType()).isEqualTo(AuditEventType.LOGOUT);
        assertThat(entry.success()).isTrue();
        assertThat(entry.failureReason()).isNull();
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

    @Test
    @DisplayName("toEntity should default null metadata to empty map")
    void should_default_null_metadata() {
        AuditLogEntry entry = AuditLogEntry.of(AuditEventType.LOGIN_FAILED, null, "user@example.com", "10.0.0.1", false, "bad");

        AuditLogJpaEntity entity = mapper.toEntity(entry);

        assertThat(entity.getMetadata()).isEmpty();
    }
}