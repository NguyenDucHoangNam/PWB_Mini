package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.modules.iam.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Profile("!test")
@Slf4j
public class MailServiceImpl implements MailService {

    private static final String OTP_TEMPLATE = "email/otp-registration";
    private static final String SUBJECT_PREFIX = "[PWB Mini] ";

    private final JavaMailSender mailSender;
    private final TemplateEngine emailTemplateEngine;

    @Value("${mail.from:PWB Mini <noreply@pwb-mini.dev>}")
    private String mailFrom;

    @Value("${app.iam.otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    public MailServiceImpl(JavaMailSender mailSender,
                           @Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine) {
        this.mailSender = mailSender;
        this.emailTemplateEngine = emailTemplateEngine;
    }

    @Override
    public void sendOtpEmail(String toEmail, String fullName, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT_PREFIX + "Your verification code");

            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("appName", "PWB Mini");
            ctx.setVariable("fullName", fullName == null ? "" : fullName);
            ctx.setVariable("otp", otp);
            ctx.setVariable("expiresMinutes", otpTtlSeconds / 60);
            String html = emailTemplateEngine.process(OTP_TEMPLATE, ctx);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (MailException | MessagingException ex) {
            log.warn("Failed to send OTP email to {}: {}", toEmail, ex.getMessage());
            throw new IllegalStateException("Failed to send OTP email", ex);
        }
    }
}