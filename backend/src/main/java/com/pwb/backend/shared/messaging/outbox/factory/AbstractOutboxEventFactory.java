package com.pwb.backend.shared.messaging.outbox.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.messaging.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.messaging.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.shared.messaging.outbox.exception.OutboxEventSerializationException;
import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOutboxEventFactory {

  private static final Pattern IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile("^[A-Za-z0-9_.:\\-]{1,100}$");

  protected final ObjectMapper objectMapper;
  protected final OutboxPayloadCipher cipher;
  protected final ApplicationEventPublisher eventPublisher;

  protected abstract String getAggregateType();

  protected static String requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException(
          "idempotencyKey is required; supply a deterministic business key instead of "
              + "relying on a generated UUID");
    }
    if (idempotencyKey.length() > 100 || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
      throw new IllegalArgumentException(
          "idempotencyKey must match " + IDEMPOTENCY_KEY_PATTERN.pattern());
    }
    return idempotencyKey;
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
    event.setIdempotencyKey(requireIdempotencyKey(idempotencyKey));
    event.setPayload(encryptedPayload);
    event.setStatus(OutboxEventStatus.PENDING);
    event.setAvailableAt(Instant.now());
    event.setRetryCount(0);
    event.setProcessingStartedAt(null);

    if (cipher.isEnabled()) {
      event.setPayloadKeyVersion(cipher.keyVersion());
    }

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