package com.pwb.backend.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final OutboxPayloadCipher outboxCipher;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public void publish(String aggregateType, UUID aggregateId, String eventType, String payloadKey, Object payload) {
        publish(aggregateType, aggregateId, eventType, payloadKey, payload, null);
    }

    public void publish(String aggregateType, UUID aggregateId, String eventType, String payloadKey, Object payload, UUID idempotencyKey) {
        String serializedPayload = serializePayload(payload);
        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payloadKey,
                serializedPayload,
                Instant.now(),
                idempotencyKey,
                1);
        outboxRepository.save(row);
        eventPublisher.publishEvent(new OutboxCreatedEvent(row.getId(), null, aggregateType));
        log.debug("Outbox event created: aggregateType={} aggregateId={} eventType={}",
                aggregateType, aggregateId, eventType);
    }

    private String serializePayload(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return outboxCipher.encrypt(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
