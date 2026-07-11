package com.pwb.backend.common.outbox.scheduler;

import com.pwb.backend.common.enums.OutboxStatus;
import com.pwb.backend.common.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRetryResultHandler {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSuccess(java.util.UUID rowId, java.time.Instant when, OutboxEventRepository repository) {
        repository.findById(rowId).ifPresent(row -> {
            row.markProcessed(when);
            row.setLastError(null);
            repository.save(row);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(java.util.UUID rowId,
                              String errorMessage,
                              int maxAttempts,
                              java.time.Instant now,
                              BackoffCalculator backoffCalculator,
                              org.slf4j.Logger log,
                              OutboxEventRepository repository) {
        repository.findById(rowId).ifPresent(row -> {
            row.incrementAttemptCount();
            row.setLastError(truncate(errorMessage, 1000));
            if (row.getAttemptCount() >= maxAttempts) {
                row.setStatus(OutboxStatus.DEAD_LETTER);
                log.error("Outbox event {} moved to DEAD_LETTER after {} attempts",
                        row.getId(), row.getAttemptCount());
            } else {
                row.setStatus(OutboxStatus.FAILED);
                row.setNextAttemptAt(now.plus(backoffCalculator.nextDelay(row.getAttemptCount())));
            }
            repository.save(row);
        });
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
