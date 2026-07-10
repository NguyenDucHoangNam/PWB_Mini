package com.pwb.backend.audio.internal.model;

import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "demo_distributions",
    indexes = {
        @Index(name = "idx_distributions_demo_created", columnList = "demo_id, created_at"),
        @Index(name = "idx_distributions_token_active", columnList = "share_token"),
        @Index(name = "idx_distributions_thread_created", columnList = "thread_id, created_at")
    }
)
public class DemoDistribution extends BaseEntity {

  @Column(name = "thread_id", nullable = false, length = 36)
  private String threadId;

  @Column(name = "demo_id", nullable = false, length = 36)
  private String demoId;

  @Column(name = "recipient_email", nullable = false, length = 100)
  private String recipientEmail;

  @Column(name = "share_token", nullable = false, unique = true, columnDefinition = "uuid")
  private UUID shareToken;

  @Column(name = "allow_download", nullable = false)
  private boolean allowDownload;

  @Column(name = "is_revoked", nullable = false)
  private boolean isRevoked;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoke_reason", length = 50)
  private String revokeReason;

  @Column(name = "play_count", nullable = false)
  private int playCount;

  @Column(name = "last_played_at")
  private Instant lastPlayedAt;
}
