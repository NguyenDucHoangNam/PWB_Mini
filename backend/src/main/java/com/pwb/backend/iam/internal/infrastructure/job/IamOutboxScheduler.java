package com.pwb.backend.iam.internal.infrastructure.job;

import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.infrastructure.publisher.IamOutboxPublisher;
import com.pwb.backend.iam.internal.infrastructure.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.outbox.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamOutboxScheduler {

    private final IamOutboxEventRepository repository;
    private final IamOutboxPublisher publisher;
    private final OutboxProperties properties;

    /**
     * Polls and processes pending IAM outbox events.
     *
     * <p>H8: shedlock guards against the second instance racing us on the
     * same batch. The query itself uses {@code FOR UPDATE SKIP LOCKED} so a
     * second instance that grabs the lock between two polls still cannot
     * double-process rows it did not fetch.
     *
     * <p>H3: marked {@link Transactional} so each batch runs in a single
     * transaction; the row lock acquired by the native query is held until
     * commit, and the publisher can safely update {@code status} +
     * {@code processed_at} on the same entity instance.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:30000}")
    @SchedulerLock(name = "iamOutboxScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")
    @Transactional
    public void pollPendingOutboxEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        List<IamOutboxEvent> pendingEvents =
            repository.findPendingEventsForUpdate(properties.getBatchSize());

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("IAM Outbox scheduler found {} pending events", pendingEvents.size());

        for (IamOutboxEvent event : pendingEvents) {
            try {
                publisher.processOutboxEvent(event);
            } catch (Exception ex) {
                log.error("Failed to process IAM outbox event: eventId={}", event.getId(), ex);
            }
        }
    }
}
