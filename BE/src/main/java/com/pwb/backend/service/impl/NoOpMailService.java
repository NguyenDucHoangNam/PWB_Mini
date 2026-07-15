package com.pwb.backend.service.impl;

import com.pwb.backend.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false")
public class NoOpMailService implements MailService {

    @Override
    public void send(String to, String subjectKey, String bodyKey, Object... args) {
        log.warn("[MAIL_DISABLED] app.mail.enabled=false — skip mail to={} subjectKey={} args={}",
                to, subjectKey, args == null ? 0 : args.length);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody, String textFallback) {
        log.warn("[MAIL_DISABLED] app.mail.enabled=false — skip HTML mail to={} subject={} htmlLen={}",
                to, subject, htmlBody == null ? 0 : htmlBody.length());
    }

    @Override
    public void sendHtmlWithLogo(String to, String subject, String htmlBody, String textFallback) {
        log.warn("[MAIL_DISABLED] app.mail.enabled=false — skip HTML mail with logo to={} subject={} htmlLen={}",
                to, subject, htmlBody == null ? 0 : htmlBody.length());
    }
}
