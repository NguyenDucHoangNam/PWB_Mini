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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
    private static final String LOGO_RESOURCE_PATH = "static/images/logo.jpeg";
    private static final String LOGO_CONTENT_ID = "pwb-logo";
    private static final String LOGO_MIME_TYPE = "image/jpeg";

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

    @Override
    public void sendHtmlWithLogo(String to, String subject, String htmlBody, String textFallback) {
        doSendWithLogo(to, subject, htmlBody, true);
        log.debug("HTML mail with logo sent (text fallback len={}) to={}", textFallback == null ? 0 : textFallback.length(), to);
    }

    private void doSend(String to, String subject, String body, boolean html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, UTF_8);
            applyFrom(helper);
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

    private void doSendWithLogo(String to, String subject, String body, boolean html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
            applyFrom(helper);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, html);
            Resource logoResource = new ClassPathResource(LOGO_RESOURCE_PATH);
            if (logoResource.exists()) {
                helper.addInline(LOGO_CONTENT_ID, logoResource, LOGO_MIME_TYPE);
            } else {
                log.warn("PWB logo resource not found at classpath:{}", LOGO_RESOURCE_PATH);
            }
            mailSender.send(message);
            log.info("Email sent: to={} subject={} html={} logo={}", to, subject, html, logoResource.exists());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new IllegalStateException("Cannot send email", ex);
        }
    }

    private void applyFrom(MimeMessageHelper helper) throws MessagingException, UnsupportedEncodingException {
        if (fromAddress != null && !fromAddress.isBlank()) {
            helper.setFrom(fromAddress, mailProperties.getFromName());
        }
    }
}
