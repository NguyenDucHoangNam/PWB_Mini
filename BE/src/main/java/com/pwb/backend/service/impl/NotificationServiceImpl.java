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
        String htmlBody = renderHtml(subject, textBody, otp, footerText);

        mailService.sendHtml(email, subject, htmlBody, textBody);
        log.info("OTP email dispatched: userId={} email={} ttlMinutes={}", userId, email, ttlMinutes);
    }

    private String renderHtml(String subject, String bodyText, String otp, String footerText) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("bodyText", bodyText);
        context.setVariable("otp", otp);
        context.setVariable("footerText", footerText);
        return templateEngine.process(TEMPLATE_REGISTER_OTP, context);
    }
}
