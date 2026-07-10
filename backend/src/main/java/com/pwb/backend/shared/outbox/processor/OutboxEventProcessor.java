package com.pwb.backend.shared.outbox.processor;

import com.pwb.backend.shared.outbox.model.OutboxEvent;

public interface OutboxEventProcessor<T extends OutboxEvent> {
    void processOutboxEvent(T event);
}