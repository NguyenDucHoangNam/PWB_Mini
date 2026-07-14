package com.pwb.backend.common.outbox.model;

import com.pwb.backend.common.outbox.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    @Setter(AccessLevel.NONE)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    @Setter(AccessLevel.NONE)
    private String eventType;

    @Column(name = "payload_key", length = 128)
    @Setter(AccessLevel.NONE)
    private String payloadKey;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    @Setter(AccessLevel.NONE)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Setter(AccessLevel.NONE)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "idempotency_key")
    @Setter(AccessLevel.NONE)
    private UUID idempotencyKey;

    @Column(name = "payload_key_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private int payloadKeyVersion;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "processed_at")
    @Setter(AccessLevel.NONE)
    private Instant processedAt;

    public OutboxEvent(UUID id,
                       String aggregateType,
                       UUID aggregateId,
                       String eventType,
                       String payloadKey,
                       String payload,
                       Instant nextAttemptAt,
                       UUID idempotencyKey,
                       int payloadKeyVersion) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadKey = payloadKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = nextAttemptAt;
        this.idempotencyKey = idempotencyKey;
        this.payloadKeyVersion = payloadKeyVersion;
    }

    public void incrementAttemptCount() {
        this.attemptCount += 1;
    }

    public void markProcessed(Instant when) {
        this.processedAt = when;
        this.status = OutboxStatus.SENT;
    }
}
