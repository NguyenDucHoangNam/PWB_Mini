package com.pwb.backend.audio.internal.worker;

import com.pwb.backend.audio.internal.model.AudioProcessingJob;
import com.pwb.backend.audio.internal.repository.AudioProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Separate bean so that @Transactional on markJob* methods actually starts
 * a transaction. Calling them from AudioProcessingWorker.process() directly
 * skips the Spring proxy (self-invocation), so a separate proxy is required.
 */
@Component
@RequiredArgsConstructor
public class WorkerJobStateUpdater {

  private final AudioProcessingJobRepository jobRepository;

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobRunning(AudioProcessingJob job) {
    job.setStatus(com.pwb.backend.audio.internal.enums.JobStatus.RUNNING);
    job.setAttemptCount(job.getAttemptCount() + 1);
    job.setStartedAt(Instant.now());
    jobRepository.save(job);
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobCompleted(AudioProcessingJob job) {
    job.setStatus(com.pwb.backend.audio.internal.enums.JobStatus.COMPLETED);
    job.setCompletedAt(Instant.now());
    jobRepository.save(job);
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobFailed(AudioProcessingJob job, String error) {
    job.setStatus(com.pwb.backend.audio.internal.enums.JobStatus.FAILED);
    job.setLastError(error);
    job.setCompletedAt(Instant.now());
    jobRepository.save(job);
  }
}