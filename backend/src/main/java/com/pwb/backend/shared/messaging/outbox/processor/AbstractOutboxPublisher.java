package com.pwb.backend.shared.messaging.outbox.processor;

import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.messaging.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
public abstract class AbstractOutboxPublisher<T extends OutboxEvent>
    implements OutboxEventProcessor<T> {

  protected final JpaRepository<T, String> repository;
  protected final KafkaTemplate<String, String> kafkaTemplate;
  protected final OutboxPayloadCipher cipher;
  protected final OutboxService outboxService;
  protected final TransactionTemplate transactionTemplate;

  protected AbstractOutboxPublisher(JpaRepository<T, String> repository,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    OutboxPayloadCipher cipher,
                                    OutboxService outboxService,
                                    PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.kafkaTemplate = kafkaTemplate;
    this.cipher = cipher;
    this.outboxService = outboxService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
  }

  protected String resolveTopic(T event) {
    return defaultTopic();
  }

  protected abstract String defaultTopic();

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    processOutboxEventById(event.outboxEventId());
  }

  @Override
  public void processOutboxEvent(T outboxEvent) {
    processOutboxEventById(outboxEvent.getId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processOutboxEventById(String eventId) {
    Optional<T> loaded = repository.findById(eventId);
    if (loaded.isEmpty()) {
      log.warn("Outbox event {} disappeared before processing", eventId);
      return;
    }
    T event = loaded.get();
    if (!outboxService.tryClaim(event)) {
      log.debug("Skipping outbox event {} — already claimed by another worker", eventId);
      return;
    }
    repository.save(event);
    String topic = resolveTopic(event);
    String payload = cipher.decrypt(event.getPayload());
    try {
      kafkaTemplate.send(topic, event.getId(), payload)
          .whenComplete((result, ex) -> finalizePublish(event.getId(), ex));
    } catch (Exception ex) {
      log.error("Synchronous Kafka send threw for outbox event {}", event.getId(), ex);
      markFailureInNewTransaction(event.getId(), ex);
    }
  }

  private void finalizePublish(String eventId, Throwable ex) {
    if (ex == null) {
      markProcessedInNewTransaction(eventId);
    } else {
      markFailureInNewTransaction(eventId, ex);
    }
  }

  protected void markProcessedInNewTransaction(String eventId) {
    transactionTemplate.executeWithoutResult(status ->
        repository.findById(eventId).ifPresent(event -> {
          outboxService.markAsProcessed(event);
          repository.save(event);
          log.info("Outbox event {} published successfully", eventId);
        }));
  }

  protected void markFailureInNewTransaction(String eventId, Throwable ex) {
    transactionTemplate.executeWithoutResult(status ->
        repository.findById(eventId).ifPresent(event -> {
          outboxService.markAsFailed(event, ex);
          repository.save(event);
        }));
  }
}