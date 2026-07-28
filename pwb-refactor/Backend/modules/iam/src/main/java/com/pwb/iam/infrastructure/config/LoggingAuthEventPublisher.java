package com.pwb.iam.infrastructure.config;

import com.pwb.iam.application.audit.AuditPersistRequested;
import com.pwb.iam.domain.audit.AuditEventType;
import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishAuthSuccess(AuthSuccessEvent event) {
        publishAuthSuccess(event.userId(), event.email(), event.clientIp());
    }

    @Override
    public void publishAuthSuccess(UUID userId, String email, String clientIp) {
        log.info("Auth success: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGIN_SUCCESS, userId, email, clientIp, true, null)));
    }

    @Override
    public void publishLoginFailed(String email, String clientIp, String reason) {
        String maskedEmail = maskEmail(email);
        log.info("Login failed: emailMasked={} ip={} reason={}", maskedEmail, clientIp, reason);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGIN_FAILED, null, email, clientIp, false, reason)));
    }

    @Override
    public void publishLogout(UUID userId, String email, String clientIp) {
        log.info("Logout: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGOUT, userId, email, clientIp, true, null)));
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
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes) {
        log.info("Password reset requested: userId={} ttlMinutes={}", userId, ttlMinutes);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.PASSWORD_RESET_REQUESTED, userId, email, null, true, null)));
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email, String clientIp) {
        log.info("Password changed: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.PASSWORD_CHANGED, userId, email, clientIp, true, null)));
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
    public void publishGoogleLoginSuccess(UUID userId, String email, String clientIp) {
        log.info("Google login success: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.GOOGLE_LOGIN_SUCCESS, userId, email, clientIp, true, null)));
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
