package com.pwb.backend.common.model;

import com.pwb.backend.common.enums.OutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Column(name = "processed_at")
    @Setter(AccessLevel.NONE)
    private Instant processedAt;

    public OutboxEvent(UUID id,
                       String aggregateType,
                       UUID aggregateId,
                       String eventType,
                       String payloadKey,
                       String payload,
                       Instant nextAttemptAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadKey = payloadKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void incrementAttemptCount() {
        this.attemptCount += 1;
    }

    public void markProcessed(Instant when) {
        this.processedAt = when;
        this.status = OutboxStatus.SENT;
    }
}
