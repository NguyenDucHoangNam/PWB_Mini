package com.pwb.backend.shared.outbox.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.shared.outbox.exception.OutboxEventSerializationException;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOutboxEventFactory {

  protected final ObjectMapper objectMapper;
  protected final OutboxPayloadCipher cipher;
  protected final ApplicationEventPublisher eventPublisher;

  protected abstract String getAggregateType();

  protected void onEventCreated(OutboxEvent event) {
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public <T extends OutboxEvent> T createEvent(
      T event,
      String eventType,
      String aggregateId,
      Object payload,
      String idempotencyKey
  ) {
    String json = toJson(payload);
    String encryptedPayload = cipher.encrypt(json);

    event.setAggregateType(getAggregateType());
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    event.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : java.util.UUID.randomUUID().toString());
    event.setPayload(encryptedPayload);
    event.setStatus(OutboxEventStatus.PENDING);
    event.setAvailableAt(Instant.now());
    event.setRetryCount(0);

    onEventCreated(event);

    log.debug("Created outbox event: type={}, aggregateId={}, idempotencyKey={}",
        eventType, aggregateId, event.getIdempotencyKey());
    return event;
  }

  public String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new OutboxEventSerializationException("Failed to serialize outbox event payload", e);
    }
  }

  protected void publishOutboxCreatedEvent(String eventId) {
    eventPublisher.publishEvent(new OutboxCreatedEvent(eventId));
  }
}
