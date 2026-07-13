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

    private static final String OTP_TEMPLATE = "otp-registration";
    private static final String PASSWORD_RESET_TEMPLATE = "password-reset";
    private static final String ACCOUNT_DELETION_TEMPLATE = "account-deletion-requested";
    private static final String SUBJECT_PREFIX = "[PWB Mini] ";
    private static final String PASSWORD_RESET_SUBJECT = SUBJECT_PREFIX + "Hướng dẫn khôi phục mật khẩu tài khoản";
    private static final String ACCOUNT_DELETION_SUBJECT = SUBJECT_PREFIX + "Xác nhận yêu cầu xóa tài khoản của bạn";

    private final JavaMailSender mailSender;
    private final TemplateEngine emailTemplateEngine;

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${mail.security-from}")
    private String securityFrom;

    @Value("${app.iam.otp.ttl-seconds}")
    private long otpTtlSeconds;

    @Value("${app.iam.password-reset.token-ttl-seconds}")
    private long passwordResetTtlSeconds;

    @Value("${app.iam.password-reset.support-email}")
    private String supportEmail;

    @Value("${app.iam.account-deletion.support-email}")
    private String accountDeletionSupportEmail;

    @Value("${app.iam.account-deletion.login-url}")
    private String accountDeletionLoginUrl;

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

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetUrl, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(securityFrom);
            helper.setTo(toEmail);
            helper.setSubject(PASSWORD_RESET_SUBJECT);

            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("appName", "PWB Mini");
            ctx.setVariable("fullName", fullName == null ? "" : fullName);
            ctx.setVariable("resetUrl", resetUrl);
            ctx.setVariable("expiresMinutes", ttlMinutes > 0 ? ttlMinutes : (passwordResetTtlSeconds / 60));
            ctx.setVariable("supportEmail", supportEmail);
            String html = emailTemplateEngine.process(PASSWORD_RESET_TEMPLATE, ctx);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MailException | MessagingException ex) {
            log.warn("Failed to send password reset email to {}: {}", toEmail, ex.getMessage());
            throw new IllegalStateException("Failed to send password reset email", ex);
        }
    }

    @Override
    public void sendAccountDeletionRequestedEmail(String toEmail,
                                                  String fullName,
                                                  java.time.Instant deletionRequestedAt,
                                                  java.time.Instant scheduledPermanentDeletionAt,
                                                  int graceDays,
                                                  String loginUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(securityFrom);
            helper.setTo(toEmail);
            helper.setSubject(ACCOUNT_DELETION_SUBJECT);

            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("appName", "PWB Mini");
            ctx.setVariable("fullName", fullName == null ? "" : fullName);
            ctx.setVariable("deletionRequestedAt", deletionRequestedAt);
            ctx.setVariable("scheduledPermanentDeletionAt", scheduledPermanentDeletionAt);
            ctx.setVariable("graceDays", graceDays);
            ctx.setVariable("loginUrl", loginUrl == null || loginUrl.isBlank() ? accountDeletionLoginUrl : loginUrl);
            ctx.setVariable("supportEmail", accountDeletionSupportEmail);
            String html = emailTemplateEngine.process(ACCOUNT_DELETION_TEMPLATE, ctx);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Account deletion requested email sent to {}", toEmail);
        } catch (MailException | MessagingException ex) {
            log.warn("Failed to send account deletion email to {}: {}", toEmail, ex.getMessage());
            throw new IllegalStateException("Failed to send account deletion email", ex);
        }
    }
}