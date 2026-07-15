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

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final MessageHelper messageHelper;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void send(String to, String subjectKey, String bodyKey, Object... args) {
        String subject = messageHelper.get(subjectKey, args);
        String body = messageHelper.get(bodyKey, args);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress, mailProperties.getFromName());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent: to={} subject={}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new IllegalStateException("Cannot send email", ex);
        }
    }
}
