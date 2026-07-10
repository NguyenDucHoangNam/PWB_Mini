package com.pwb.backend.shared.outbox.service;

import com.pwb.backend.shared.outbox.config.OutboxProperties;
import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxProperties properties;

  @Transactional
  public void markAsProcessed(OutboxEvent event) {
    event.setStatus(OutboxEventStatus.PROCESSED);
    event.setProcessedAt(Instant.now());
    event.setLastError(null);
    log.debug("Marked outbox event {} as processed", event.getId());
  }

  @Transactional
  public void markAsFailed(OutboxEvent event, Throwable ex) {
    int newCount = event.getRetryCount() + 1;
    event.setRetryCount(newCount);
    String errorMessage = ex != null && ex.getMessage() != null
        ? ex.getMessage().substring(0, Math.min(1000, ex.getMessage().length()))
        : ex != null ? ex.getClass().getSimpleName() : "Unknown error";
    event.setLastError(errorMessage);

    int deadLetterThreshold = properties.getDeadLetterAfterRetries();
    if (newCount >= deadLetterThreshold) {
      event.setStatus(OutboxEventStatus.DEAD_LETTERED);
      event.setDeadLetteredAt(Instant.now());
      log.error("Outbox event {} dead-lettered after {} retries. lastError={}",
          event.getId(), newCount, errorMessage);
    } else {
      event.setStatus(OutboxEventStatus.FAILED);
      event.setAvailableAt(Instant.now().plusSeconds(computeBackoffSeconds(newCount)));
      log.warn("Outbox event {} publish failed (attempt {}/{}). lastError={}",
          event.getId(), newCount, deadLetterThreshold, errorMessage);
    }
  }

  public long computeBackoffSeconds(int attempt) {
    int initial = properties.getInitialBackoffSeconds();
    int max = properties.getMaxBackoffSeconds();
    long base = Math.min(max, initial * (1L << Math.min(attempt - 1, 10)));
    long jitter = (long) (base * 0.2 * (Math.random() - 0.5) * 2);
    return Math.max(1, base + jitter);
  }
}
