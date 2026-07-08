package com.pwb.backend.iam.internal.job;

import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAnonymizationJob {

  private final AccountLifecycleService accountLifecycleService;

  @Scheduled(cron = "${app.jobs.anonymization.cron:0 0 2 * * *}")
  @SchedulerLock(
      name = "anonymization_job_lock",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT30S"
  )
  public void runAnonymization() {
    log.info("ANONYMIZATION_JOB_STARTED");
    try {
      var response = accountLifecycleService.triggerAnonymization();
      log.info("ANONYMIZATION_JOB_FINISHED: processedUsers={}, durationMs={}",
          response.processedUsersCount(), response.executionTimeMs());
    } catch (Exception e) {
      log.error("ANONYMIZATION_JOB_FAILED", e);
    }
  }
}
