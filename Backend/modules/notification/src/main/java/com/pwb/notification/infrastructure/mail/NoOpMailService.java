package com.pwb.notification.infrastructure.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"dev", "test", "local"})
public class NoOpMailService implements MailService {

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("[NOOP-MAIL] to={}, subject={}, bodyLength={}", to, subject,
                htmlBody != null ? htmlBody.length() : 0);
    }
}
