package com.pwb.backend.audio.internal.model;

import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
    name = "shared_threads",
    indexes = {
        @Index(name = "idx_threads_producer_email_prefix", columnList = "producer_id, recipient_email"),
        @Index(name = "idx_threads_producer_last_interacted", columnList = "producer_id, last_interacted_at")
    }
)
public class SharedThread extends BaseEntity {

  @Column(name = "producer_id", nullable = false, length = 36)
  private String producerId;

  @Column(name = "recipient_email", nullable = false, length = 100)
  private String recipientEmail;

  @Column(name = "recipient_email_hash", nullable = false, length = 64)
  private String recipientEmailHash;

  @Column(name = "last_interacted_at", nullable = false)
  private Instant lastInteractedAt;
}
