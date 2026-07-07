package com.pwb.backend.iam.internal.publisher;

import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

  private static final String NOTIFICATION_TOPIC = "notification-events";

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    outboxEventRepository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
  }

  public void processOutboxEvent(OutboxEvent outboxEvent) {
    try {
      String topic = NOTIFICATION_TOPIC;
      if ("ACCOUNT_ANONYMIZED".equals(outboxEvent.getEventType())) {
        topic = "iam-account-events";
      }

      kafkaTemplate.send(topic, outboxEvent.getPayload())
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.warn("Failed to publish outbox event to Kafka: eventId={}, error={}",
                  outboxEvent.getId(), ex.getMessage());
            } else {
              outboxEvent.setStatus(OutboxEventStatus.PROCESSED);
              outboxEventRepository.save(outboxEvent);
            }
          });
    } catch (Exception ex) {
      log.warn("Failed to process outbox event: eventId={}, error={}",
          outboxEvent.getId(), ex.getMessage());
    }
  }
}
