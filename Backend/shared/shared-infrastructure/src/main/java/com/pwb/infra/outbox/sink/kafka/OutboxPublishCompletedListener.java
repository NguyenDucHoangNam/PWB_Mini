package com.pwb.infra.outbox.sink.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishCompletedListener {

    private final KafkaOutboxPublisher publisher;

    @EventListener
    public void handle(KafkaOutboxPublisher.OutboxPublishCompleted event) {
        try {
            publisher.handleCompleted(event);
        } catch (Exception ex) {
            log.warn("OUTBOX.completed.handler failed: outboxId={} reason={}",
                    event.outboxId(), ex.getMessage());
        }
    }
}
