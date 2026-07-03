package com.pwb.backend.modules.iam.api.event;

public record OutboxCreatedEvent(
    String outboxEventId
) {}
