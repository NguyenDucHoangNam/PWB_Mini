package com.pwb.backend.audio.internal.model;

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
    name = "demo_downloads",
    indexes = {
        @Index(name = "idx_dl_dist_time", columnList = "distribution_id, downloaded_at"),
        @Index(name = "idx_dl_demo_time", columnList = "demo_id, downloaded_at"),
        @Index(name = "idx_dl_session", columnList = "session_id_hash")
    }
)
public class DemoDownload {

  @jakarta.persistence.Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "distribution_id", nullable = false, length = 36)
  private String distributionId;

  @Column(name = "demo_id", nullable = false, length = 36)
  private String demoId;

  @Column(name = "session_id_hash", nullable = false, length = 64)
  private String sessionIdHash;

  @Column(name = "ip_subnet_hash", length = 64)
  private String ipSubnetHash;

  @Column(name = "downloaded_at", nullable = false)
  private Instant downloadedAt;

  @Column(name = "s3_key", nullable = false, length = 255)
  private String s3Key;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @jakarta.persistence.PrePersist
  void prePersist() {
    if (id == null) {
      id = java.util.UUID.randomUUID().toString();
    }
    if (downloadedAt == null) {
      downloadedAt = Instant.now();
    }
  }
}