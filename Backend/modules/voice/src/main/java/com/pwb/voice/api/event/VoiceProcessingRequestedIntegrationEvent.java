package com.pwb.voice.api.event;

import java.time.Instant;
import java.util.UUID;

public record VoiceProcessingRequestedIntegrationEvent(
    String eventId,
    UUID songId,
    UUID userId,
    Instant occurredAt
) {}