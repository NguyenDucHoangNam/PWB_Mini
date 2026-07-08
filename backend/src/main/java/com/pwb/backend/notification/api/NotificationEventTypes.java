package com.pwb.backend.notification.api;

/**
 * Catalog of supported notification event types. Centralising the strings
 * here avoids typos and lets the notification module decide which
 * template to render.
 */
public final class NotificationEventTypes {

  private NotificationEventTypes() {}

  public static final String REGISTRATION_OTP = "REGISTRATION_OTP";
  public static final String WELCOME_EMAIL = "WELCOME_EMAIL";
  public static final String PASSWORD_RESET = "PASSWORD_RESET";
  public static final String ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
  public static final String ANOMALOUS_LOGIN = "ANOMALOUS_LOGIN";
}