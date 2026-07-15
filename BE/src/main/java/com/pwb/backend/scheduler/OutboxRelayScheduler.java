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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void relay() {
        if (!enabled) {
            return;
        }

        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<OutboxEvent> batch = outboxEventRepository.claimPendingBatch(OutboxStatus.PENDING, size);
        if (batch.isEmpty()) {
            return;
        }

        log.info("Relaying {} outbox events", batch.size());
        for (OutboxEvent event : batch) {
            try {
                ObjectNode envelope = objectMapper.createObjectNode();
                envelope.put("eventType", event.getEventType());
                envelope.put("aggregateType", event.getAggregateType());
                envelope.put("aggregateId", event.getAggregateId());
                envelope.put("idempotencyKey", event.getIdempotencyKey());
                if (event.getPayload() != null && !event.getPayload().isBlank()) {
                    JsonNode data = objectMapper.readTree(event.getPayload());
                    envelope.set("data", data);
                }
                String body = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(KafkaTopicConfig.USER_EVENTS, event.getAggregateId(), body)
                        .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setStatus(OutboxStatus.PROCESSED);
                outboxEventRepository.save(event);
                log.debug("Outbox event relayed: id={} type={}", event.getId(), event.getEventType());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while relaying outbox event id={}", event.getId());
                break;
            } catch (ExecutionException | TimeoutException ex) {
                log.error("Failed to relay outbox event id={}: {}", event.getId(), ex.getMessage(), ex);
            } catch (Exception ex) {
                log.error("Unexpected error while relaying outbox event id={}: {}", event.getId(), ex.getMessage(), ex);
            }
        }
    }
}
