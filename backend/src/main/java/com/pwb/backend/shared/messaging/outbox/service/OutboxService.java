package com.pwb.backend.shared.messaging.outbox.service;

import com.pwb.backend.shared.messaging.outbox.config.OutboxProperties;
import com.pwb.backend.shared.messaging.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

  private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

  private final OutboxProperties properties;

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean tryClaim(OutboxEvent event) {
    if (event.getStatus() != OutboxEventStatus.PENDING) {
      return false;
    }
    if (event.getProcessingStartedAt() != null) {
      return false;
    }
    event.setStatus(OutboxEventStatus.IN_FLIGHT);
    event.setProcessingStartedAt(Instant.now());
    return true;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseClaim(OutboxEvent event) {
    if (event.getStatus() == OutboxEventStatus.IN_FLIGHT) {
      event.setStatus(OutboxEventStatus.PENDING);
    }
    event.setProcessingStartedAt(null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void markAsProcessed(OutboxEvent event) {
    event.setStatus(OutboxEventStatus.PROCESSED);
    event.setProcessedAt(Instant.now());
    event.setProcessingStartedAt(null);
    event.setLastError(null);
    log.debug("Marked outbox event {} as processed", event.getId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void markAsFailed(OutboxEvent event, Throwable ex) {
    int newCount = event.getRetryCount() + 1;
    event.setRetryCount(newCount);
    event.setProcessingStartedAt(null);
    String errorMessage = sanitizeErrorMessage(ex);
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

  static String sanitizeErrorMessage(Throwable ex) {
    if (ex == null) {
      return "Unknown error";
    }
    String raw = ex.getMessage();
    String base = (raw == null || raw.isBlank()) ? ex.getClass().getSimpleName() : raw;
    String singleLine = base.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    if (singleLine.length() > MAX_ERROR_MESSAGE_LENGTH) {
      singleLine = singleLine.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
    return singleLine;
  }

  long computeBackoffSeconds(int attempt) {
    int initial = properties.getInitialBackoffSeconds();
    int max = properties.getMaxBackoffSeconds();
    long base = Math.min(max, initial * (1L << Math.min(attempt - 1, 10)));

    long jitter = (long) (base * 0.2
        * (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 2);
    return Math.max(1, base + jitter);
  }
}