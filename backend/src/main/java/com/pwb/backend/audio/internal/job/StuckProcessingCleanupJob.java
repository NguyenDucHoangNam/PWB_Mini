package com.pwb.backend.audio.internal.job;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.enums.DemoStatus;
import com.pwb.backend.audio.internal.enums.JobStatus;
import com.pwb.backend.audio.internal.model.AudioProcessingJob;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.repository.AudioProcessingJobRepository;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckProcessingCleanupJob {

  private final AudioProcessingJobRepository jobRepository;
  private final DemoRepository demoRepository;
  private final StorageService storageService;
  private final AudioProperties audioProperties;

  @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
  @SchedulerLock(name = "audio-stuck-cleanup", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  @Transactional
  public void cleanupStuckJobs() {
    int timeoutSeconds = audioProperties.getWorker().getProcessingTimeoutSeconds();
    Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
    List<JobStatus> stuckStatuses = List.of(JobStatus.PENDING, JobStatus.RUNNING);
    List<AudioProcessingJob> stuckJobs = jobRepository.findStuckJobsByStatuses(stuckStatuses, cutoff);
    if (stuckJobs.isEmpty()) {
      return;
    }
    log.info("Found {} stuck audio jobs to clean up", stuckJobs.size());
    for (AudioProcessingJob job : stuckJobs) {
      processStuckJob(job);
    }
  }

  private void processStuckJob(AudioProcessingJob job) {
    String demoId = job.getDemoId();
    log.warn("STUCK_PROCESSING_CLEANED demoId={} attempts={}", demoId, job.getAttemptCount());
    job.setStatus(JobStatus.FAILED);
    job.setLastError("Stuck processing timeout (cleanup job)");
    job.setCompletedAt(Instant.now());
    jobRepository.save(job);

    demoRepository.findByIdAndDeletedFalse(demoId).ifPresent(demo -> {
      markDemoFailedAndCleanupS3(demo, "Stuck processing timeout (cleanup job)");
    });
  }

  private void markDemoFailedAndCleanupS3(Demo demo, String reason) {
    demo.setStatus(DemoStatus.FAILED);
    demo.setErrorMessage(reason);
    demoRepository.save(demo);
    if (demo.getConfirmedS3Key() != null) {
      try {
        storageService.deleteFile(demo.getConfirmedS3Key());
        log.info("Deleted S3 confirmed key {} for stuck demo {}", demo.getConfirmedS3Key(), demo.getId());
      } catch (Exception ex) {
        log.warn("Failed to delete S3 confirmed key {} for stuck demo {}",
            demo.getConfirmedS3Key(), demo.getId(), ex);
      }
    }
  }
}
