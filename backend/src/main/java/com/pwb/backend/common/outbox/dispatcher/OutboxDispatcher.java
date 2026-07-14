package com.pwb.backend.common.outbox.dispatcher;

import com.pwb.backend.common.outbox.enums.OutboxStatus;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventSerializer;
import com.pwb.backend.common.outbox.publisher.OutboxEventTopics;
import com.pwb.backend.common.outbox.scheduler.BackoffCalculator;
import com.pwb.backend.common.outbox.scheduler.OutboxRetryResultHandler;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventTopics outboxEventTopics;
    private final OutboxEventSerializer outboxEventSerializer;
    private final OutboxRetryResultHandler resultHandler;
    private final BackoffCalculator backoffCalculator;
    private final OutboxEventRepository outboxRepository;

    @Value("${app.outbox.max-attempts}")
    private int maxAttempts;

    public void dispatch(OutboxEvent event) {
        String topic = resolveTopic(event.getEventType());
        String payload = outboxEventSerializer.serialize(event);
        String key = resolveKey(event);

        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex == null) {
                resultHandler.handleSuccess(event.getId(), Instant.now(), outboxRepository);
            } else {
                log.warn("Kafka publish failed for outbox {} topic {}: {}",
                        event.getId(), topic, ex.getMessage());
                resultHandler.handleFailure(event.getId(), ex.getMessage(), maxAttempts, Instant.now(),
                        backoffCalculator, log, outboxRepository);
            }
        });
    }

    public void markProcessing(OutboxEvent event) {
        event.setStatus(OutboxStatus.PROCESSING);
        outboxRepository.save(event);
    }

    private String resolveTopic(String eventType) {
        return outboxEventTopics.resolveTopic(eventType);
    }

    private String resolveKey(OutboxEvent event) {
        return event.getPayloadKey() != null ? event.getPayloadKey() : event.getAggregateId().toString();
    }
}
