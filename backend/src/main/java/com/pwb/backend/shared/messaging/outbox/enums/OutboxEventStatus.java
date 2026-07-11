package com.pwb.backend.shared.messaging.outbox.enums;

public enum OutboxEventStatus {
  PENDING,
  IN_FLIGHT,
  PROCESSED,
  FAILED,
  DEAD_LETTERED
}
