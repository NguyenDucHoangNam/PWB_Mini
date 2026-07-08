package com.pwb.backend.notification.api;

import java.util.Map;

/**
 * Public notification request published by other modules. Carries the
 * event type, recipient and a free-form template variables bag so the
 * notification module can render the appropriate message without
 * leaking internal types.
 */
public record NotificationEvent(
    String eventType,
    String email,
    String fullName,
    String locale,
    Map<String, Object> variables
) {}