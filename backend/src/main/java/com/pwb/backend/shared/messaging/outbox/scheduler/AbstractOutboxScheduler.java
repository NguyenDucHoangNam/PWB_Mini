package com.pwb.backend.shared.messaging.outbox.scheduler;

import com.pwb.backend.shared.messaging.outbox.config.OutboxProperties;
import com.pwb.backend.shared.messaging.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import com.pwb.backend.shared.messaging.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.messaging.outbox.repository.OutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.Instant;
import java.util.List;

@Slf4j
public abstract class AbstractOutboxScheduler<T extends OutboxEvent> {

  protected final OutboxEventRepository<T> repository;
  protected final OutboxEventProcessor<T> processor;
  protected final OutboxService outboxService;
  protected final OutboxProperties properties;

  protected AbstractOutboxScheduler(OutboxEventRepository<T> repository,
                                    OutboxEventProcessor<T> processor,
                                    OutboxService outboxService,
                                    OutboxProperties properties) {
    this.repository = repository;
    this.processor = processor;
    this.outboxService = outboxService;
    this.properties = properties;
  }

  protected abstract String moduleName();

  protected abstract String lockName();

  protected java.time.Duration staleClaimThreshold() {
    return java.time.Duration.ofMinutes(5);
  }

  @SchedulerLock(
      name = "abstract-outbox-scheduler-default",
      lockAtLeastFor = "PT5S",
      lockAtMostFor = "PT50S"
  )
  public void pollAndProcess() {
    if (!properties.isEnabled()) {
      return;
    }
    recoverStaleClaims();
    List<T> pendingEvents = repository.findPendingEventsForUpdate(properties.getBatchSize());
    if (pendingEvents.isEmpty()) {
      return;
    }
    log.info("{} outbox scheduler found {} pending events",
        moduleName(), pendingEvents.size());
    for (T event : pendingEvents) {
      try {
        processor.processOutboxEvent(event);
      } catch (Exception ex) {
        log.error("Failed to process outbox event in {} scheduler: eventId={}",
            moduleName(), event.getId(), ex);
      }
    }
  }

  private void recoverStaleClaims() {
    Instant threshold = Instant.now().minus(staleClaimThreshold());
    List<String> staleIds = repository.findStaleInFlightIds(threshold, properties.getBatchSize());
    if (staleIds.isEmpty()) {
      return;
    }
    log.warn("{} outbox scheduler found {} stale IN_FLIGHT events; resetting",
        moduleName(), staleIds.size());
    for (String id : staleIds) {
      try {
        repository.findById(id).ifPresent(event -> {
          if (event.getStatus() == OutboxEventStatus.IN_FLIGHT) {
            outboxService.releaseClaim(event);
            repository.save(event);
          }
        });
      } catch (Exception ex) {
        log.error("Failed to reset stale outbox event {}", id, ex);
      }
    }
  }
}