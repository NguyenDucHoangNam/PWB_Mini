package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.audit.AuditEventType;
import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.iam.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditLogMapper {

    public AuditLogJpaEntity toEntity(AuditLogEntry entry) {
        if (entry == null) {
            return null;
        }
        return AuditLogJpaEntity.builder()
                .id(entry.eventId() == null ? UUID.randomUUID() : entry.eventId())
                .eventType(entry.eventType())
                .actorId(entry.actorId())
                .actorEmail(entry.actorEmail())
                .targetId(entry.targetId())
                .clientIp(entry.clientIp())
                .success(entry.success())
                .failureReason(entry.failureReason())
                .metadata(entry.metadata() == null ? Map.of() : entry.metadata())
                .occurredAt(entry.occurredAt())
                .build();
    }

    public AuditLogEntry toDomain(AuditLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        AuditEventType type = entity.getEventType();
        return new AuditLogEntry(
                entity.getId(),
                type,
                entity.getActorId(),
                entity.getActorEmail(),
                entity.getTargetId(),
                entity.getClientIp(),
                entity.isSuccess(),
                entity.getFailureReason(),
                entity.getMetadata() == null ? Map.of() : entity.getMetadata(),
                entity.getOccurredAt()
        );
    }
}