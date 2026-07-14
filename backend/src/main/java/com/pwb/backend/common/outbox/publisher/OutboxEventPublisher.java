package com.pwb.backend.common.outbox.publisher;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRepository outboxRepository;
    private final OutboxEventTopics outboxEventTopics;
    private final OutboxEventSerializer outboxEventSerializer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent event) {
        outboxRepository.findById(event.outboxId()).ifPresent(row -> publish(row, event.topic()));
    }

    private void publish(OutboxEvent row, String overrideTopic) {
        String topic = overrideTopic != null ? overrideTopic : outboxEventTopics.resolveTopic(row.getEventType());
        String payload = outboxEventSerializer.serialize(row);
        String key = row.getPayloadKey() != null ? row.getPayloadKey() : row.getAggregateId().toString();

        CompletableFuture<?> future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Kafka publish failed for outbox {} topic {}: {}",
                        row.getId(), topic, ex.getMessage());
            } else {
                log.debug("Kafka publish OK for outbox {} topic {}", row.getId(), topic);
            }
        });
    }
}