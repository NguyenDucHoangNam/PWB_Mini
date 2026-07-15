package com.pwb.backend.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pwb.backend.entity.rdbms.OutboxEvent;
import com.pwb.backend.enums.OutboxStatus;
import com.pwb.backend.kafka.config.KafkaTopicConfig;
import com.pwb.backend.repository.rdbms.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 3L;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.enabled:true}")
    private boolean enabled;

    @Scheduled(
            fixedRateString = "${app.outbox.poll-interval-ms:5000}",
            initialDelayString = "${app.outbox.poll-initial-delay-ms:5000}"
    )
    public void relay() {
        if (!enabled) {
            return;
        }

        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<UUID> eventIds = outboxEventRepository.claimPendingIds(OutboxStatus.PENDING, size, Instant.now());
        if (eventIds.isEmpty()) {
            return;
        }

        log.info("Outbox relay: claimed {} events for publish", eventIds.size());
        for (UUID eventId : eventIds) {
            publishOne(eventId);
        }
    }

    private void publishOne(UUID eventId) {
        outboxEventProcessor.fetchPending(eventId).ifPresentOrElse(
                event -> doPublish(event),
                () -> log.debug("Outbox event already processed or not found: id={}", eventId)
        );
    }

    private void doPublish(OutboxEvent event) {
        String envelope;
        try {
            envelope = buildEnvelope(event);
        } catch (Exception ex) {
            log.error("Failed to build envelope for outbox event id={}", event.getId(), ex);
            outboxEventProcessor.markRetry(event.getId(), ex);
            return;
        }

        try {
            kafkaTemplate.send(KafkaTopicConfig.USER_EVENTS, event.getAggregateId(), envelope)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxEventProcessor.markPublished(event.getId());
            log.debug("Outbox event published: id={} type={}", event.getId(), event.getEventType());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing outbox event id={}", event.getId());
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            log.warn("Failed to publish outbox event id={}: {}", event.getId(), ex.getMessage());
            outboxEventProcessor.markRetry(event.getId(), ex);
        } catch (Exception ex) {
            log.warn("Unexpected error publishing outbox event id={}: {}", event.getId(), ex.getMessage());
            outboxEventProcessor.markRetry(event.getId(), ex);
        }
    }

    private String buildEnvelope(OutboxEvent event) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventType", event.getEventType());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("aggregateId", event.getAggregateId());
        envelope.put("idempotencyKey", event.getIdempotencyKey());
        if (event.getPayload() != null && !event.getPayload().isBlank()) {
            JsonNode data = objectMapper.readTree(event.getPayload());
            envelope.set("data", data);
        }
        return objectMapper.writeValueAsString(envelope);
    }
}
