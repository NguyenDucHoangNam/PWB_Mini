package com.pwb.backend.common.outbox.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    DEAD_LETTER
}
