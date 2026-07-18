package com.pwb.outbox.infrastructure.scheduler;

import com.pwb.outbox.core.model.OutboxStatus;
import com.pwb.outbox.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.pwb.outbox.infrastructure.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 20;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5L;

    private final OutboxEventJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void relay() {
        Instant now = Instant.now();
        int claimed = repository.claimBatch(BATCH_SIZE, now, now);
        if (claimed == 0) {
            return;
        }

        List<OutboxEventJpaEntity> events = repository.findByStatus(OutboxStatus.PROCESSING);
        for (OutboxEventJpaEntity event : events) {
            publishToKafka(event);
        }
    }

    private void publishToKafka(OutboxEventJpaEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayloadKey(), event.getPayload())
                .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(Instant.now());
            event.setLastError(null);
        } catch (Exception ex) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(ex.getMessage());
            long backoffSeconds = (long) Math.pow(5, event.getRetryCount());
            event.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
            event.setStatus(OutboxStatus.PENDING);
            log.warn("OUTBOX.publish failed: id={} retry={} backoff={}s error={}",
                event.getId(), event.getRetryCount(), backoffSeconds, ex.getMessage());
        }
    }
}
