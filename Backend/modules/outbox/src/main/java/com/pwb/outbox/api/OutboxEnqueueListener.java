package com.pwb.outbox.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEnqueueListener {
    private final OutboxWriter outboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OutboxEnqueueRequested event) {
        outboxWriter.enqueue(event.topic(), event.key(), event.aggregateType(), event.payload(), event.headers());
    }
}
