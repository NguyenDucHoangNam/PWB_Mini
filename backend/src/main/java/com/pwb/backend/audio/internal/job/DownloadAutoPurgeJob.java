package com.pwb.backend.audio.internal.job;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.repository.DemoDownloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadAutoPurgeJob {

  private final DemoDownloadRepository repository;
  private final AudioProperties audioProperties;

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  public void purge() {
    int days = audioProperties.getDownload().getAuditRetentionDays();
    Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
    int deleted = repository.deleteOlderThan(cutoff);
    log.info("DOWNLOAD_AUTO_PURGE deleted={} cutoff={} retentionDays={}",
        deleted, cutoff, days);
  }
}