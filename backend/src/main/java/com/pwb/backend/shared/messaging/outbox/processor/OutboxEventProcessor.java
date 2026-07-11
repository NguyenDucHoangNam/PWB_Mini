package com.pwb.backend.shared.messaging.outbox.processor;

import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;

public interface OutboxEventProcessor<T extends OutboxEvent> {
    void processOutboxEvent(T event);
}
