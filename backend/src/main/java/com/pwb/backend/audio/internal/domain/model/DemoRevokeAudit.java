package com.pwb.backend.audio.internal.domain.model;

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
    name = "demo_revoke_audit",
    indexes = {
        @Index(name = "idx_audit_dist", columnList = "distribution_id"),
        @Index(name = "idx_audit_time", columnList = "revoked_at"),
        @Index(name = "idx_audit_demo", columnList = "demo_id")
    }
)
public class DemoRevokeAudit {

  @jakarta.persistence.Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "distribution_id", nullable = false, length = 36)
  private String distributionId;

  @Column(name = "demo_id", nullable = false, length = 36)
  private String demoId;

  @Column(name = "revoked_by_user_id", nullable = false, length = 36)
  private String revokedByUserId;

  @Column(name = "revoked_at", nullable = false)
  private Instant revokedAt;

  @Column(name = "reason", length = 50)
  private String reason;

  @Column(name = "ip_subnet_hash", length = 64)
  private String ipSubnetHash;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @jakarta.persistence.PrePersist
  void prePersist() {
    if (id == null) {
      id = java.util.UUID.randomUUID().toString();
    }
    if (revokedAt == null) {
      revokedAt = Instant.now();
    }
  }
}