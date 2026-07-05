package com.pwb.backend.iam.api.event;

public record OutboxCreatedEvent(
    String outboxEventId
) {}
