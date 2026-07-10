package com.pwb.backend.shared.outbox.model;

import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class OutboxEvent extends com.pwb.backend.shared.model.BaseEntity {

  @Column(name = "aggregate_type", length = 50, nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", length = 36, nullable = false)
  private String aggregateId;

  @Column(name = "event_type", length = 50, nullable = false)
  private String eventType;

  @Column(name = "idempotency_key", length = 100, unique = true, nullable = false)
  private String idempotencyKey;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private OutboxEventStatus status = OutboxEventStatus.PENDING;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "dead_lettered_at")
  private Instant deadLetteredAt;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @PrePersist
  @Override
  public void prePersist() {
    super.prePersist();
    if (this.availableAt == null) {
      this.availableAt = Instant.now();
    }
  }
}