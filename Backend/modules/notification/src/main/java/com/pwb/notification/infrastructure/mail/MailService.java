package com.pwb.notification.infrastructure.mail;

public interface MailService {

    void send(String to, String subject, String htmlBody);
}
