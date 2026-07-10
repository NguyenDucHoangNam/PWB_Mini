package com.pwb.backend.shared.outbox.scheduler;

import com.pwb.backend.shared.outbox.config.OutboxProperties;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

  private final OutboxProperties properties;
  private final Function<String, String> payloadDecryptor;

  private OutboxSchedulerDelegate delegate;

  public interface OutboxSchedulerDelegate {
    <T extends OutboxEvent> List<T> findPendingEvents(int batchSize);
    <T extends OutboxEvent> void processEvent(T event, String decryptedPayload);
  }

  public void configure(OutboxSchedulerDelegate delegate) {
    this.delegate = delegate;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:30000}")
  @Transactional
  public void pollPendingOutboxEvents() {
    if (!properties.isEnabled()) {
      return;
    }

    if (delegate == null) {
      log.debug("Outbox scheduler not yet configured, skipping");
      return;
    }

    List<? extends OutboxEvent> pendingEvents =
        delegate.findPendingEvents(properties.getBatchSize());

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("Outbox scheduler found {} pending events", pendingEvents.size());

    for (OutboxEvent event : pendingEvents) {
      try {
        String decryptedPayload = payloadDecryptor.apply(event.getPayload());
        delegate.processEvent(event, decryptedPayload);
      } catch (Exception ex) {
        log.error("Failed to process outbox event: eventId={}", event.getId(), ex);
      }
    }
  }
}
