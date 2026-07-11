package com.pwb.backend.common.outbox.event;

import java.util.UUID;

public record OutboxCreatedEvent(
        UUID outboxId,
        String topic,
        String aggregateType
) {}