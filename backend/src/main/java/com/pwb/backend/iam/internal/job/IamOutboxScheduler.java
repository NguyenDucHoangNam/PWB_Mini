package com.pwb.backend.iam.internal.job;

import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.iam.internal.publisher.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamOutboxScheduler {

  private static final int BATCH_SIZE = 20;

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxPublisher outboxPublisher;

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void pollPendingOutboxEvents() {
    List<OutboxEvent> pendingEvents = outboxEventRepository
        .findPendingEventsForUpdate(BATCH_SIZE);

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("Outbox scheduler found {} pending events", pendingEvents.size());

    for (OutboxEvent event : pendingEvents) {
      try {
        outboxPublisher.processOutboxEvent(event);
      } catch (Exception ex) {
        log.error("Failed to process outbox event: eventId={}", event.getId(), ex);
      }
    }
  }
}
