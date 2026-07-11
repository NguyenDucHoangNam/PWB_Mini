package com.pwb.backend.common.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    DEAD_LETTER
}
