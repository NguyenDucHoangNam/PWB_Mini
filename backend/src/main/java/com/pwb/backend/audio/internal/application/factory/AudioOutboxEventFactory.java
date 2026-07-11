package com.pwb.backend.audio.internal.application.factory;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.factory.AbstractOutboxEventFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
public class AudioOutboxEventFactory extends AbstractOutboxEventFactory {

  public static final String AGGREGATE_TYPE_DISTRIBUTION = "AUDIO_DISTRIBUTION";
  public static final String EVENT_TYPE_SEND_SHARE_EMAIL = "SEND_SHARE_EMAIL";

  private final AudioOutboxEventRepository repository;

  public AudioOutboxEventFactory(
      AudioOutboxEventRepository repository,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper,
      org.springframework.context.ApplicationEventPublisher eventPublisher,
      com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher cipher
  ) {
    super(objectMapper, cipher, eventPublisher);
    this.repository = repository;
  }

  @Override
  protected String getAggregateType() {
    return AGGREGATE_TYPE_DISTRIBUTION;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AudioOutboxEvent create(String eventType, String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    AudioOutboxEvent event = new AudioOutboxEvent();
    createEvent(event, eventType, aggregateId, payload, idempotencyKey);
    AudioOutboxEvent saved = repository.save(event);
    publishOutboxCreatedEvent(saved.getId());
    return saved;
  }

  public AudioOutboxEvent sendShareEmail(String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    return create(EVENT_TYPE_SEND_SHARE_EMAIL, aggregateId, payload, idempotencyKey);
  }
}
