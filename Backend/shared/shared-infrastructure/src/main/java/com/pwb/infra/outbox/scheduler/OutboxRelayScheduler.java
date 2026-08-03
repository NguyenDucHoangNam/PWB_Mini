package com.pwb.infra.outbox.scheduler;

import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import com.pwb.infra.outbox.properties.OutboxProperties;
import com.pwb.infra.outbox.sink.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(60);

    private final OutboxEventJpaRepository repository;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${pwb.outbox.relay.poll-interval-ms:5000}")
    @Transactional
    public void relay() {
        Instant now = Instant.now();
        int batchSize = properties.getRelay().getBatchSize();
        Instant nextAttempt = now.plusSeconds(properties.getRetry().getBackoffSeconds()[0]);
        Instant leaseUntil = now.plus(DEFAULT_LEASE);

        List<UUID> reclaimed = repository.reclaimExpiredLease(batchSize, now, nextAttempt, leaseUntil);
        int remaining = Math.max(0, batchSize - reclaimed.size());
        List<UUID> fresh = remaining == 0
                ? List.of()
                : repository.claimBatch(remaining, now, nextAttempt, leaseUntil);

        List<UUID> claimedIds = new ArrayList<>(reclaimed.size() + fresh.size());
        claimedIds.addAll(reclaimed);
        claimedIds.addAll(fresh);

        if (claimedIds.isEmpty()) {
            return;
        }

        List<OutboxEventJpaEntity> events =
                repository.findAllByIdInAndStatusOrderByCreatedAtAsc(claimedIds,
                        com.pwb.infra.outbox.core.OutboxStatus.PROCESSING);
        if (events.isEmpty()) {
            return;
        }

        Runnable submit = () -> events.forEach(ev -> {
            try {
                publisher.publish(ev);
            } catch (Exception ex) {
                log.error("OUTBOX.relay.submit failed: id={} reason={}",
                        ev.getId(), ex.getMessage());
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }
}