package com.pwb.backend.shared.outbox.scheduler;

import com.pwb.backend.shared.outbox.config.OutboxProperties;
import com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import com.pwb.backend.shared.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.util.List;

/**
 * Base class for module-specific outbox pollers.
 *
 * <p>H1 / H8: replaces the old {@code OutboxScheduler} that exposed a
 * {@code Function<String, String>} field-injection hack and a delegate that
 * could be {@code null} at runtime. Subclasses get the cipher injected via
 * constructor, the batch size from {@link OutboxProperties}, and
 * {@link SchedulerLock} for cross-instance safety.
 *
 * <p>H4: subclasses must query through {@link OutboxEventRepository}
 * which exposes a single native query with {@code FOR UPDATE SKIP LOCKED}.
 * The legacy JPQL {@code findReadyForProcessing} method is still available
 * for test slices but is no longer wired through this base class.
 */
@Slf4j
public abstract class AbstractOutboxScheduler<T extends OutboxEvent> {

    protected final OutboxEventRepository<T> repository;
    protected final OutboxPayloadCipher cipher;
    protected final OutboxProperties properties;

    protected AbstractOutboxScheduler(OutboxEventRepository<T> repository,
                                      OutboxPayloadCipher cipher,
                                      OutboxProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
    }

    /**
     * Implementations should call {@link #pollAndProcess()} from their
     * {@code @Scheduled} method so cross-instance locking and cipher
     * integration stay in one place.
     */
    @SchedulerLock(
        name = "abstract-outbox-scheduler",
        lockAtLeastFor = "PT5S",
        lockAtMostFor = "PT50S"
    )
    protected void pollAndProcess() {
        if (!properties.isEnabled()) {
            return;
        }
        List<T> pendingEvents = repository.findPendingEventsForUpdate(properties.getBatchSize());
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("{} outbox scheduler found {} pending events",
            moduleName(), pendingEvents.size());
        for (T event : pendingEvents) {
            try {
                processSingleEvent(event);
            } catch (Exception ex) {
                log.error("Failed to process outbox event in {} scheduler: eventId={}",
                    moduleName(), event.getId(), ex);
            }
        }
    }

    /**
     * Hook called for each event. Implementations are expected to mark the
     * event as processed / failed in their own way (see
     * {@link com.pwb.backend.shared.outbox.service.OutboxService}).
     */
    protected abstract void processSingleEvent(T event);

    /** Display name for logs. */
    protected abstract String moduleName();
}