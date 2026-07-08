package com.pwb.backend.iam.internal.enums;

public enum OutboxEventStatus {
  PENDING,
  PROCESSED,
  FAILED,
  DEAD_LETTERED
}
