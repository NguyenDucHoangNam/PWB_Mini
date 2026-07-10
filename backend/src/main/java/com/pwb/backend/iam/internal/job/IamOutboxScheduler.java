package com.pwb.backend.iam.internal.job;

import com.pwb.backend.iam.internal.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.publisher.IamOutboxPublisher;
import com.pwb.backend.iam.internal.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher;
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

  private final IamOutboxEventRepository repository;
  private final IamOutboxPublisher publisher;
  private final OutboxPayloadCipher cipher;

  @Scheduled(fixedDelay = 30000)
  @Transactional
  public void pollPendingOutboxEvents() {
    List<IamOutboxEvent> pendingEvents = repository.findPendingEventsForUpdate(BATCH_SIZE);

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
