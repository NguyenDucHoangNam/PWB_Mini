package com.pwb.infra.outbox.sink.kafka;

import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.sink.OutboxPublishException;
import com.pwb.infra.outbox.sink.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.outbox.sink", havingValue = "kafka", matchIfMissing = true)
@RequiredArgsConstructor
public class KafkaOutboxPublisher implements OutboxPublisher {

    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(OutboxEventJpaEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayloadKey(), event.getPayload())
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("OUTBOX.published: id={} topic={} key={}",
                    event.getId(), event.getTopic(), event.getPayloadKey());
        } catch (Exception ex) {
            throw new OutboxPublishException("Failed to publish outbox event id=" + event.getId(), ex);
        }
    }
}