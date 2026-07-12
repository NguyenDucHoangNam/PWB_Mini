package com.pwb.backend.modules.audio.entity;

import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.modules.audio.enums.AudioJobStatus;
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
@Table(name = "audio_processing_jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class AudioProcessingJob extends BaseEntity {

    public static final int MAX_ATTEMPTS = 3;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "demo_id", nullable = false, unique = true, updatable = false)
    private UUID demoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AudioJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AudioProcessingJob(UUID id, UUID demoId) {
        this.id = id;
        this.demoId = demoId;
        this.status = AudioJobStatus.PENDING;
        this.attemptCount = 0;
    }

    public void markRunning(Instant when) {
        this.status = AudioJobStatus.RUNNING;
        this.startedAt = when;
    }

    public void markCompleted(Instant when) {
        this.status = AudioJobStatus.COMPLETED;
        this.completedAt = when;
    }

    public void markFailed(String error, Instant when) {
        this.status = AudioJobStatus.FAILED;
        this.lastError = error;
        this.completedAt = when;
    }

    public void incrementAttempt(String error) {
        this.attemptCount += 1;
        this.lastError = error;
    }
}