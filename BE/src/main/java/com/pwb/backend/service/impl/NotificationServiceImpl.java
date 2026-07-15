package com.pwb.backend.service.impl;

import com.pwb.backend.config.OtpProperties;
import com.pwb.backend.service.MailService;
import com.pwb.backend.service.NotificationService;
import com.pwb.backend.utils.helper.MessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String SUBJECT_REGISTER = "auth.email.subject.register";
    private static final String TEXT_REGISTER = "auth.email.body.register";
    private static final String FOOTER_REGISTER = "auth.email.footer";
    private static final String TEMPLATE_REGISTER_OTP = "email/register-otp";

    private static final String SUBJECT_PASSWORD_RESET = "auth.email.subject.password_reset";
    private static final String TEXT_PASSWORD_RESET = "auth.email.body.password_reset";
    private static final String FAREWELL_PASSWORD_RESET = "auth.email.farewell.password_reset";
    private static final String TEMPLATE_PASSWORD_RESET = "email/password-reset";

    private static final String SUBJECT_PASSWORD_CHANGED = "auth.email.subject.password_changed";
    private static final String TEXT_PASSWORD_CHANGED = "auth.email.body.password_changed";
    private static final String TEMPLATE_PASSWORD_CHANGED = "email/password-changed";

    private static final String SUBJECT_WELCOME_GOOGLE = "auth.email.subject.welcome_google";
    private static final String TEXT_WELCOME_GOOGLE = "auth.email.body.welcome_google";
    private static final String TEMPLATE_WELCOME_GOOGLE = "email/welcome-google";

    private final MailService mailService;
    private final OtpProperties otpProperties;
    private final MessageHelper messageHelper;
    private final TemplateEngine templateEngine;

    @Override
    public void sendOtpEmail(UUID userId, String email, String otp) {
        long ttlMinutes = Math.max(1L, otpProperties.getTtlSeconds() / 60L);
        String subject = messageHelper.get(SUBJECT_REGISTER);
        String textBody = messageHelper.get(TEXT_REGISTER, otp, ttlMinutes);
        String footerText = messageHelper.get(FOOTER_REGISTER, ttlMinutes);
        String htmlBody = renderRegisterOtpHtml(subject, textBody, otp, footerText);

        mailService.sendHtml(email, subject, htmlBody, textBody);
        log.info("OTP email dispatched: userId={} email={} ttlMinutes={}", userId, email, ttlMinutes);
    }

    @Override
    public void sendPasswordResetLinkEmail(UUID userId, String email, String resetLink, long ttlMinutes) {
        String subject = messageHelper.get(SUBJECT_PASSWORD_RESET);
        String bodyText = messageHelper.get(TEXT_PASSWORD_RESET, ttlMinutes);
        String farewellText = messageHelper.get(FAREWELL_PASSWORD_RESET);
        String htmlBody = renderPasswordResetHtml(subject, bodyText, resetLink, farewellText, ttlMinutes);

        mailService.sendHtml(email, subject, htmlBody, bodyText);
        log.info("Password reset email dispatched: userId={} email={}", userId, email);
    }

    @Override
    public void sendPasswordChangedEmail(UUID userId, String email) {
        String subject = messageHelper.get(SUBJECT_PASSWORD_CHANGED);
        String bodyText = messageHelper.get(TEXT_PASSWORD_CHANGED);
        String htmlBody = renderPasswordChangedHtml(subject, bodyText);

        mailService.sendHtml(email, subject, htmlBody, bodyText);
        log.info("Password changed email dispatched: userId={} email={}", userId, email);
    }

    @Override
    public void sendWelcomeGoogleEmail(UUID userId, String email, String fullName) {
        String displayName = (fullName == null || fullName.isBlank()) ? email : fullName;
        String subject = messageHelper.get(SUBJECT_WELCOME_GOOGLE);
        String bodyText = messageHelper.get(TEXT_WELCOME_GOOGLE, displayName);
        String htmlBody = renderWelcomeGoogleHtml(subject, bodyText, displayName);

        mailService.sendHtml(email, subject, htmlBody, bodyText);
        log.info("Welcome Google email dispatched: userId={} email={}", userId, email);
    }

    private String renderRegisterOtpHtml(String subject, String bodyText, String otp, String footerText) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("bodyText", bodyText);
        context.setVariable("otp", otp);
        context.setVariable("footerText", footerText);
        return templateEngine.process(TEMPLATE_REGISTER_OTP, context);
    }

    private String renderPasswordResetHtml(String subject, String bodyText, String resetLink, String farewellText, long ttlMinutes) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("bodyText", bodyText);
        context.setVariable("resetLink", resetLink);
        context.setVariable("farewellText", farewellText);
        context.setVariable("ttlMinutes", ttlMinutes);
        return templateEngine.process(TEMPLATE_PASSWORD_RESET, context);
    }

    private String renderPasswordChangedHtml(String subject, String bodyText) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("bodyText", bodyText);
        return templateEngine.process(TEMPLATE_PASSWORD_CHANGED, context);
    }

    private String renderWelcomeGoogleHtml(String subject, String bodyText, String displayName) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("bodyText", bodyText);
        context.setVariable("displayName", displayName);
        return templateEngine.process(TEMPLATE_WELCOME_GOOGLE, context);
    }
}