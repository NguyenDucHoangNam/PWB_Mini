package com.pwb.backend.audio.internal.infrastructure.publisher;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.messaging.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.shared.messaging.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioOutboxPublisher implements OutboxEventProcessor<AudioOutboxEvent> {

  private static final String AUDIO_TOPIC = "notification-events";

  private final AudioOutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxPayloadCipher cipher;
  private final OutboxService outboxService;

  /**
   * Reactive path: the factory already saved the event inside the original
   * transaction. After COMMIT, look it up and publish it immediately, so we
   * don't have to wait for the next poll.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    repository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
  }

  @Override
  @Transactional
  public void processOutboxEvent(AudioOutboxEvent outboxEvent) {
    String payload = cipher.decrypt(outboxEvent.getPayload());
    try {
      kafkaTemplate.send(AUDIO_TOPIC, outboxEvent.getId(), payload)
          .whenComplete((result, ex) -> handlePublishResult(outboxEvent, ex));
    } catch (Exception ex) {
      handlePublishFailure(outboxEvent, ex);
    }
  }

  private void handlePublishResult(AudioOutboxEvent outboxEvent, Throwable ex) {
    if (ex == null) {
      outboxService.markAsProcessed(outboxEvent);
      repository.save(outboxEvent);
      log.info("Audio outbox event {} published successfully", outboxEvent.getId());
      return;
    }
    handlePublishFailure(outboxEvent, ex);
  }

  @Transactional
  public void handlePublishFailure(AudioOutboxEvent outboxEvent, Throwable ex) {
    outboxService.markAsFailed(outboxEvent, ex);
    repository.save(outboxEvent);
  }
}
