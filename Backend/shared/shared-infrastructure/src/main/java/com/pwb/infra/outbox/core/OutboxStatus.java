package com.pwb.infra.outbox.core;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}