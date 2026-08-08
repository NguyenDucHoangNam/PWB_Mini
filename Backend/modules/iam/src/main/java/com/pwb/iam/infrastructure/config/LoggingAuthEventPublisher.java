package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    @Override
    public void publishAuthSuccess(AuthSuccessEvent event) {
        log.info("Auth success: userId={} ip={}", event.userId(), event.clientIp());
    }

    @Override
    public void publishLoginFailed(String email, String clientIp, String userAgent, String reason) {
        String maskedEmail = maskEmail(email);
        log.info("Login failed: emailMasked={} ip={} reason={}", maskedEmail, clientIp, reason);
    }

    @Override
    public void publishLogout(UUID userId, String clientIp, String userAgent) {
        log.info("Logout: userId={} ip={}", userId, clientIp);
    }

    @Override
    public void publishOtpIssued(OtpIssuedDomainEvent event) {
        log.info("OTP issued: userId={} purpose={} expiresAt={}",
                event.userId(), event.purpose(), event.expiresAt());
    }

    @Override
    public void publishOtpVerified(OtpVerifiedDomainEvent event) {
        log.info("OTP verified: userId={} purpose={} verifiedAt={}",
                event.userId(), event.purpose(), event.verifiedAt());
    }

    @Override
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes, String userAgent) {
        log.info("Password reset requested: userId={} ttlMinutes={}", userId, ttlMinutes);
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email, String clientIp, String userAgent) {
        log.info("Password changed: userId={} ip={}", userId, clientIp);
    }

    @Override
    public void publishUserRegisteredGoogle(UUID userId, String email, String fullName) {
        log.info("User registered via Google: userId={}", userId);
    }

    @Override
    public void publishUserLinkedGoogle(UUID userId, String email, String fullName) {
        log.info("Google account linked to existing user: userId={}", userId);
    }

    @Override
    public void publishGoogleLoginSuccess(UUID userId, String email, String clientIp, String userAgent) {
        log.info("Google login success: userId={} ip={}", userId, clientIp);
    }

    @Override
    public void publishGoogleLoginFailed(String email, String clientIp, String userAgent, String reason) {
        String maskedEmail = maskEmail(email);
        log.info("Google login failed: emailMasked={} ip={} reason={}", maskedEmail, clientIp, reason);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
