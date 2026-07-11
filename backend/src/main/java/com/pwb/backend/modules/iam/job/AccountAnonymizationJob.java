package com.pwb.backend.modules.iam.job;

import com.pwb.backend.modules.iam.service.AccountAnonymizationService;
import com.pwb.backend.modules.iam.service.AnonymizationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class AccountAnonymizationJob {

    private static final String LOCK_NAME = "anonymization_job_lock";
    private static final String HOSTNAME_FALLBACK = "unknown-instance";

    private final AccountAnonymizationService anonymizationService;

    @Scheduled(cron = "${app.iam.account-anonymization.cron:0 0 2 * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void runScheduled() {
        runInternal(resolveBatchSize());
    }

    public AnonymizationReport runManual(int batchSize) {
        return runInternal(Math.max(batchSize, 1));
    }

    private AnonymizationReport runInternal(int batchSize) {
        Instant start = Instant.now();
        String instance = resolveInstanceName();
        log.info("ANONYMIZATION_JOB_STARTED instance={} batchSize={}", instance, batchSize);
        try {
            AnonymizationReport report = anonymizationService.runOnce(batchSize);
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("ANONYMIZATION_JOB_FINISHED processedUsers={} durationMs={} status={}",
                    report.processedCount(), durationMs, report.status());
            return report;
        } catch (Exception ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("ANONYMIZATION_JOB_FAILED durationMs={} error={}", durationMs, ex.getMessage(), ex);
            throw ex;
        }
    }

    private int resolveBatchSize() {
        return 100;
    }

    private String resolveInstanceName() {
        String hostname = System.getenv().get("HOSTNAME");
        return hostname == null || hostname.isBlank() ? HOSTNAME_FALLBACK : hostname;
    }
}