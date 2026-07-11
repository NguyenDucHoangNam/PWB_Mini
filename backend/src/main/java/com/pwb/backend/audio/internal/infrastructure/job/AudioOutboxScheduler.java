package com.pwb.backend.audio.internal.infrastructure.job;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.publisher.AudioOutboxPublisher;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls and re-publishes any audio outbox events that the
 * {@link TransactionalEventListener} path may have missed
 * (e.g. {@code AFTER_COMMIT} hook not fired because the call site was
 * not in a Spring-managed transaction, or Kafka was unreachable when the
 * reactive listener tried to push).
 *
 * <p>Mirrors IAM's {@code IamOutboxScheduler} so each module gets its own
 * shedlock-guarded batch processor without sharing state across modules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioOutboxScheduler {

    private final AudioOutboxEventRepository repository;
    private final AudioOutboxPublisher publisher;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:30000}")
    @SchedulerLock(name = "audioOutboxScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")
    @Transactional
    public void pollPendingOutboxEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        List<AudioOutboxEvent> pendingEvents =
            repository.findPendingEventsForUpdate(properties.getBatchSize());

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Audio Outbox scheduler found {} pending events", pendingEvents.size());

        for (AudioOutboxEvent event : pendingEvents) {
            try {
                publisher.processOutboxEvent(event);
            } catch (Exception ex) {
                log.error("Failed to process Audio outbox event: eventId={}", event.getId(), ex);
            }
        }
    }
}
