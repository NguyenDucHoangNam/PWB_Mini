package com.pwb.backend.scheduler;

import com.pwb.backend.entity.rdbms.OutboxEvent;
import com.pwb.backend.enums.OutboxStatus;
import com.pwb.backend.repository.rdbms.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setLastError(null);
        event.setNextRetryAt(null);
        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID eventId, Exception ex) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastError(truncate(errorMessage(ex), 500));

        if (nextRetry >= MAX_RETRY_ATTEMPTS) {
            event.setStatus(OutboxStatus.FAILED);
            event.setNextRetryAt(null);
            log.error("Outbox event FAILED after {} retries: id={} type={}",
                    nextRetry, event.getId(), event.getEventType(), ex);
        } else {
            Duration backoff = BASE_BACKOFF.multipliedBy(1L << Math.min(nextRetry, 6));
            event.setNextRetryAt(Instant.now().plus(backoff));
            log.warn("Outbox event scheduled for retry {}/{}: id={} backoff={}s",
                    nextRetry, MAX_RETRY_ATTEMPTS, event.getId(), backoff.toSeconds());
        }
        outboxEventRepository.save(event);
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
