package com.pwb.notification.api.event;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public record EmailRequestedIntegrationEvent(
    String eventId,
    String to,
    String subjectKey,
    String templateName,
    Map<String, Object> model,
    Locale locale,
    Instant occurredAt
) {
}