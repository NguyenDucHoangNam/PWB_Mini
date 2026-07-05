package com.pwb.backend.iam.internal.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.iam.internal.service.OtpService;
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
  private final OtpService otpService;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOutboxCreated(OutboxCreatedEvent event) {
    outboxEventRepository.findById(event.outboxEventId()).ifPresent(this::processOutboxEvent);
  }

  public void processOutboxEvent(OutboxEvent outboxEvent) {
    try {
      JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
      String email = payload.get("email").asText();
      String otpCode = payload.get("otpCode").asText();

      if ("REGISTRATION_OTP".equals(outboxEvent.getEventType())) {
        otpService.storeOtp(email, otpCode);
      }

      kafkaTemplate.send(NOTIFICATION_TOPIC, outboxEvent.getPayload())
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.warn("Failed to publish outbox event to Kafka: eventId={}, error={}",
                  outboxEvent.getId(), ex.getMessage());
            } else {
              outboxEvent.setStatus(OutboxEventStatus.PROCESSED);
              outboxEventRepository.save(outboxEvent);
            }
          });
    } catch (JsonProcessingException ex) {
      log.error("Failed to parse outbox event payload: eventId={}", outboxEvent.getId(), ex);
    } catch (Exception ex) {
      log.warn("Failed to process outbox event: eventId={}, error={}",
          outboxEvent.getId(), ex.getMessage());
    }
  }
}
