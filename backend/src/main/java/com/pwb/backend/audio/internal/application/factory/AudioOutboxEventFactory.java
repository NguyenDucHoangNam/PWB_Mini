package com.pwb.backend.audio.internal.application.factory;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.factory.AbstractOutboxEventFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Slf4j
@Component
public class AudioOutboxEventFactory extends AbstractOutboxEventFactory {

  public static final String AGGREGATE_TYPE_AUDIO_DISTRIBUTION = "AUDIO_DISTRIBUTION";
  public static final String EVENT_TYPE_SEND_SHARE_EMAIL = "SEND_SHARE_EMAIL";

  private final AudioOutboxEventRepository repository;

  public AudioOutboxEventFactory(
      AudioOutboxEventRepository repository,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher,
      com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher cipher
  ) {
    super(objectMapper, cipher, eventPublisher);
    this.repository = repository;
  }

  @Override
  protected String getAggregateType() {
    return AGGREGATE_TYPE_AUDIO_DISTRIBUTION;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AudioOutboxEvent create(String eventType, String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    requireActiveTransaction();
    AudioOutboxEvent event = new AudioOutboxEvent();
    createEvent(event, eventType, aggregateId, payload, idempotencyKey);
    AudioOutboxEvent saved = repository.save(event);
    publishOutboxCreatedEvent(saved.getId());
    return saved;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AudioOutboxEvent createOrReuse(String eventType, String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    requireActiveTransaction();
    String validatedKey = requireIdempotencyKey(idempotencyKey);
    return repository.findByIdempotencyKey(validatedKey)
        .orElseGet(() -> create(eventType, aggregateId, payload, validatedKey));
  }

  private void requireActiveTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "AudioOutboxEventFactory.create requires an active transaction. "
              + "Wrap the caller in @Transactional or TransactionTemplate.execute(...).");
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      log.warn("AudioOutboxEventFactory.create called without transaction synchronization — "
          + "afterCommit hooks may not fire. Use TransactionTemplate.execute(...) "
          + "instead of plain PlatformTransactionManager.getTransaction(...).");
    }
  }

  public AudioOutboxEvent sendShareEmail(String aggregateId, Map<String, Object> payload, String idempotencyKey) {
    return createOrReuse(EVENT_TYPE_SEND_SHARE_EMAIL, aggregateId, payload, idempotencyKey);
  }
}
