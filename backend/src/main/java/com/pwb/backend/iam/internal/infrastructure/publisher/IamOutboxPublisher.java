package com.pwb.backend.iam.internal.infrastructure.publisher;

import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.infrastructure.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
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
public class IamOutboxPublisher implements OutboxEventProcessor<IamOutboxEvent> {

  private static final String NOTIFICATION_TOPIC = "notification-events";
  private static final String IAM_ACCOUNT_EVENTS_TOPIC = "iam-account-events";

  private final IamOutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxPayloadCipher cipher;
  private final OutboxService outboxService;
  private final IamProperties properties;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    repository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
  }

  @Override
  @Transactional
  public void processOutboxEvent(IamOutboxEvent outboxEvent) {
    String topic = resolveTopic(outboxEvent.getEventType());
    String payload = cipher.decrypt(outboxEvent.getPayload());
    try {
      kafkaTemplate.send(topic, outboxEvent.getId(), payload)
          .whenComplete((result, ex) -> handlePublishResult(outboxEvent, ex));
    } catch (Exception ex) {
      handlePublishFailure(outboxEvent, ex);
    }
  }

  private void handlePublishResult(IamOutboxEvent outboxEvent, Throwable ex) {
    if (ex == null) {
      outboxService.markAsProcessed(outboxEvent);
      repository.save(outboxEvent);
      log.info("IAM outbox event {} published successfully", outboxEvent.getId());
      return;
    }
    handlePublishFailure(outboxEvent, ex);
  }

  @Transactional
  public void handlePublishFailure(IamOutboxEvent outboxEvent, Throwable ex) {
    outboxService.markAsFailed(outboxEvent, ex);
    repository.save(outboxEvent);
  }

  private String resolveTopic(String eventType) {
    return "ACCOUNT_ANONYMIZED".equals(eventType) ? IAM_ACCOUNT_EVENTS_TOPIC : NOTIFICATION_TOPIC;
  }
}
