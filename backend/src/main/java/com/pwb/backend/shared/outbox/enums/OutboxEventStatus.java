package com.pwb.backend.shared.outbox.enums;

public enum OutboxEventStatus {
  PENDING,
  PROCESSED,
  FAILED,
  DEAD_LETTERED
}
