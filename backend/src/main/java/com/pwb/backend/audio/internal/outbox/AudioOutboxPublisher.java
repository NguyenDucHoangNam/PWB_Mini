package com.pwb.backend.audio.internal.outbox;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioOutboxPublisher implements OutboxEventProcessor<AudioOutboxEvent> {

  private static final String AUDIO_TOPIC = "notification-events";

  private final AudioOutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxPayloadCipher cipher;
  private final OutboxService outboxService;
  private final AudioProperties audioProperties;

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