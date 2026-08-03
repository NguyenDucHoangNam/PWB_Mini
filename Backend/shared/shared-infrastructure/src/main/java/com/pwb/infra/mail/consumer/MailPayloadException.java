package com.pwb.infra.mail.consumer;

public class MailPayloadException extends RuntimeException {

    public MailPayloadException(String message) {
        super(message);
    }

    public MailPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}