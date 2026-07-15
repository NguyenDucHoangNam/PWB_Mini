package com.pwb.backend.service;

public interface MailService {

    void send(String to, String subjectKey, String bodyKey, Object... args);

    void sendHtml(String to, String subject, String htmlBody, String textFallback);
}
