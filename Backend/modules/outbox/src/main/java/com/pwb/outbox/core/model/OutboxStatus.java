package com.pwb.outbox.core.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
