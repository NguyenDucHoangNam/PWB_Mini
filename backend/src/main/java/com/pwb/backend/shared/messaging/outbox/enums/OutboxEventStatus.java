package com.pwb.backend.shared.messaging.outbox.enums;

public enum OutboxEventStatus {
  PENDING,
  PROCESSED,
  FAILED,
  DEAD_LETTERED
}
