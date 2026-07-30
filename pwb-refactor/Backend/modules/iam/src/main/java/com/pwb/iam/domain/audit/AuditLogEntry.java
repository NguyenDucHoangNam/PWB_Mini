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
        String userAgent,
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
                null,
                success,
                failureReason,
                Map.of(),
                Instant.now()
        );
    }

    public static AuditLogEntry of(
            AuditEventType eventType,
            UUID actorId,
            String actorEmail,
            String clientIp,
            String userAgent,
            boolean success,
            String failureReason,
            Map<String, Object> metadata
    ) {
        return new AuditLogEntry(
                UUID.randomUUID(),
                eventType,
                actorId,
                actorEmail,
                null,
                clientIp,
                userAgent,
                success,
                failureReason,
                metadata != null ? metadata : Map.of(),
                Instant.now()
        );
    }
}