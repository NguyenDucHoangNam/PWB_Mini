package com.pwb.infra.outbox.sink;

public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}