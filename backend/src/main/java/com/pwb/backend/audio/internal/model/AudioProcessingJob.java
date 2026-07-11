package com.pwb.backend.audio.internal.model;

import com.pwb.backend.audio.internal.enums.JobStatus;
import com.pwb.backend.shared.kernel.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "audio_processing_jobs")
public class AudioProcessingJob extends BaseEntity {

  @Column(name = "demo_id", nullable = false, length = 36, unique = true)
  private String demoId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobStatus status = JobStatus.PENDING;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount = 0;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
