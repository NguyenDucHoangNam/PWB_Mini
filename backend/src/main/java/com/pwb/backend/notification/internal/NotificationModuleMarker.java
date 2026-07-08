package com.pwb.backend.notification.internal;

import org.springframework.modulith.ApplicationModule;

/**
 * Notification module. Owns outbound communication channels (email, push,
 * future SMS, etc.) and listens to events published by other modules
 * (iam.*, liveroom.*, audio.*).
 *
 * Other modules MUST NOT inject services from {@code .internal.**}; they
 * should publish events instead.
 */
@ApplicationModule(displayName = "Notification")
public class NotificationModuleMarker {
}