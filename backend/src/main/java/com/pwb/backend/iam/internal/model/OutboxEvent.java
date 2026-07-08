package com.pwb.backend.iam.internal.model;

import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends BaseEntity {

  @Column(name = "aggregate_type", length = 50, nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", length = 36, nullable = false)
  private String aggregateId;

  @Column(name = "event_type", length = 50, nullable = false)
  private String eventType;

  @Column(name = "idempotency_key", length = 36, unique = true, nullable = false)
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
}
