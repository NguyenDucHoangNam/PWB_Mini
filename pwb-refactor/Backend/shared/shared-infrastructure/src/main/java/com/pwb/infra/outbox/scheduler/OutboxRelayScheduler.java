package com.pwb.infra.outbox.scheduler;

import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import com.pwb.infra.outbox.properties.OutboxProperties;
import com.pwb.infra.outbox.sink.OutboxPublishException;
import com.pwb.infra.outbox.sink.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventJpaRepository repository;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${pwb.outbox.relay.poll-interval-ms:5000}")
    @Transactional
    public void relay() {
        Instant now = Instant.now();
        int batchSize = properties.getRelay().getBatchSize();
        Instant claimAt = now.plusSeconds(properties.getRetry().getBackoffSeconds()[0]);

        int claimed = repository.claimBatch(batchSize, now, claimAt);
        if (claimed == 0) {
            return;
        }

        List<OutboxEventJpaEntity> events = repository.findByStatus(OutboxStatus.PROCESSING);
        for (OutboxEventJpaEntity event : events) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxEventJpaEntity event) {
        try {
            publisher.publish(event);
            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(Instant.now());
            event.setLastError(null);
        } catch (OutboxPublishException ex) {
            handleFailure(event, ex);
        }
    }

    private void handleFailure(OutboxEventJpaEntity event, OutboxPublishException ex) {
        int currentRetry = event.getRetryCount();
        event.setRetryCount(currentRetry + 1);
        event.setLastError(ex.getMessage());

        if (currentRetry + 1 >= properties.getRetry().getMaxAttempts()) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("OUTBOX.publish permanently failed: id={} retries={} error={}",
                    event.getId(), event.getRetryCount(), ex.getMessage());
        } else {
            int[] backoffSeconds = properties.getRetry().getBackoffSeconds();
            int attemptIndex = Math.min(currentRetry, backoffSeconds.length - 1);
            long backoff = backoffSeconds[attemptIndex];
            event.setNextAttemptAt(Instant.now().plusSeconds(backoff));
            event.setStatus(OutboxStatus.PENDING);
            log.warn("OUTBOX.publish failed: id={} retry={} backoff={}s error={}",
                    event.getId(), event.getRetryCount(), backoff, ex.getMessage());
        }
    }
}