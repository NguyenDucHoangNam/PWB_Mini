package com.pwb.infra.mail.consumer;

public class MailTemplateException extends RuntimeException {

    public MailTemplateException(String message) {
        super(message);
    }

    public MailTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}