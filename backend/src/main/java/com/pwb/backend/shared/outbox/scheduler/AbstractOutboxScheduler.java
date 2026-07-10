package com.pwb.backend.shared.outbox.scheduler;

import com.pwb.backend.shared.outbox.config.OutboxProperties;
import com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import com.pwb.backend.shared.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.util.List;

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

    protected abstract void processSingleEvent(T event);

    protected abstract String moduleName();
}