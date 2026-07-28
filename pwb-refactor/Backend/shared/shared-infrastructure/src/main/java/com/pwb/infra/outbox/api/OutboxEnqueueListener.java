package com.pwb.infra.outbox.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OutboxEnqueueListener {

    private final OutboxWriter outboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OutboxEnqueueRequested event) {
        outboxWriter.enqueue(event);
    }
}