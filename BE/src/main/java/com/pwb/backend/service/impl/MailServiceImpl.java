package com.pwb.backend.service.impl;

import com.pwb.backend.utils.helper.MessageHelper;
import com.pwb.backend.config.MailProperties;
import com.pwb.backend.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailServiceImpl implements MailService {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final MessageHelper messageHelper;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void send(String to, String subjectKey, String bodyKey, Object... args) {
        String subject = messageHelper.get(subjectKey, args);
        String body = messageHelper.get(bodyKey, args);
        doSend(to, subject, body, false);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody, String textFallback) {
        doSend(to, subject, htmlBody, true);
        log.debug("HTML mail sent (text fallback len={}) to={}", textFallback == null ? 0 : textFallback.length(), to);
    }

    private void doSend(String to, String subject, String body, boolean html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, UTF_8);
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress, mailProperties.getFromName());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, html);
            mailSender.send(message);
            log.info("Email sent: to={} subject={} html={}", to, subject, html);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new IllegalStateException("Cannot send email", ex);
        }
    }
}
