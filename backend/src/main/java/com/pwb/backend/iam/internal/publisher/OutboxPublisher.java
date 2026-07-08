package com.pwb.backend.iam.internal.publisher;

import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
public class OutboxPublisher {

  private static final String NOTIFICATION_TOPIC = "notification-events";

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final int deadLetterAfterRetries;

  public OutboxPublisher(OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate, IamProperties properties) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.deadLetterAfterRetries = properties.getOutbox().getDeadLetterAfterRetries();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    outboxEventRepository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
  }

  @Transactional
  public void processOutboxEvent(OutboxEvent outboxEvent) {
    String topic = resolveTopic(outboxEvent.getEventType());
    try {
      kafkaTemplate.send(topic, outboxEvent.getId(), outboxEvent.getPayload())
          .whenComplete((result, ex) -> handlePublishResult(outboxEvent, ex));
    } catch (Exception ex) {
      handlePublishFailure(outboxEvent, ex);
    }
  }

  private void handlePublishResult(OutboxEvent outboxEvent, Throwable ex) {
    if (ex == null) {
      outboxEvent.setStatus(OutboxEventStatus.PROCESSED);
      outboxEvent.setLastError(null);
      outboxEventRepository.save(outboxEvent);
      log.info("Outbox event {} published successfully", outboxEvent.getId());
      return;
    }
    handlePublishFailure(outboxEvent, ex);
  }

  @Transactional
  protected void handlePublishFailure(OutboxEvent outboxEvent, Throwable ex) {
    int newCount = outboxEvent.getRetryCount() + 1;
    outboxEvent.setRetryCount(newCount);
    String errorMessage = ex.getMessage() != null
        ? ex.getMessage().substring(0, Math.min(1000, ex.getMessage().length()))
        : ex.getClass().getSimpleName();
    outboxEvent.setLastError(errorMessage);

    if (newCount >= deadLetterAfterRetries) {
      outboxEvent.setStatus(OutboxEventStatus.DEAD_LETTERED);
      outboxEvent.setDeadLetteredAt(Instant.now());
      log.error("Outbox event {} dead-lettered after {} retries. lastError={}",
          outboxEvent.getId(), newCount, errorMessage);
    } else {
      outboxEvent.setStatus(OutboxEventStatus.FAILED);
      log.warn("Outbox event {} publish failed (attempt {}/{}). lastError={}",
          outboxEvent.getId(), newCount, deadLetterAfterRetries, errorMessage);
    }
    outboxEventRepository.save(outboxEvent);
  }

  private String resolveTopic(String eventType) {
    return "ACCOUNT_ANONYMIZED".equals(eventType) ? "iam-account-events" : NOTIFICATION_TOPIC;
  }
}
