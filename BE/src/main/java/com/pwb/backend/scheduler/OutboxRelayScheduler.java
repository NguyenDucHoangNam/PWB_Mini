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

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void relay() {
        if (!enabled) {
            return;
        }

        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<OutboxEvent> snapshots = outboxEventRepository.claimPendingIds(OutboxStatus.PENDING, size, Instant.now());
        if (snapshots.isEmpty()) {
            return;
        }

        log.info("Claimed {} outbox events for relay", snapshots.size());
        for (OutboxEvent snapshot : snapshots) {
            publishOne(snapshot);
        }
    }

    private void publishOne(OutboxEvent snapshot) {
        String envelope;
        try {
            envelope = buildEnvelope(snapshot);
        } catch (Exception ex) {
            log.error("Failed to build envelope for outbox event id={}", snapshot.getId(), ex);
            outboxEventProcessor.markRetry(snapshot.getId(), ex);
            return;
        }

        try {
            kafkaTemplate.send(KafkaTopicConfig.USER_EVENTS, snapshot.getAggregateId(), envelope)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxEventProcessor.markPublished(snapshot.getId());
            log.debug("Outbox event published: id={} type={}", snapshot.getId(), snapshot.getEventType());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing outbox event id={}", snapshot.getId());
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            log.warn("Failed to publish outbox event id={}: {}", snapshot.getId(), ex.getMessage());
            outboxEventProcessor.markRetry(snapshot.getId(), ex);
        } catch (Exception ex) {
            log.warn("Unexpected error publishing outbox event id={}: {}", snapshot.getId(), ex.getMessage());
            outboxEventProcessor.markRetry(snapshot.getId(), ex);
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
