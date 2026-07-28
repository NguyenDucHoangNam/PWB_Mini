package com.pwb.iam.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogEntry(
        UUID eventId,
        AuditEventType eventType,
        UUID actorId,
        String actorEmail,
        UUID targetId,
        String clientIp,
        boolean success,
        String failureReason,
        Map<String, Object> metadata,
        Instant occurredAt
) {

    public static AuditLogEntry of(
            AuditEventType eventType,
            UUID actorId,
            String actorEmail,
            String clientIp,
            boolean success,
            String failureReason
    ) {
        return new AuditLogEntry(
                UUID.randomUUID(),
                eventType,
                actorId,
                actorEmail,
                null,
                clientIp,
                success,
                failureReason,
                Map.of(),
                Instant.now()
        );
    }
}