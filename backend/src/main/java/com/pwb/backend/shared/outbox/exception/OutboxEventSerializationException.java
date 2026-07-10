package com.pwb.backend.shared.outbox.exception;

public class OutboxEventSerializationException extends RuntimeException {

  public OutboxEventSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
