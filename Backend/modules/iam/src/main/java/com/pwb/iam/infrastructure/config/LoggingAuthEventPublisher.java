package com.pwb.iam.infrastructure.config;

import com.pwb.iam.infrastructure.audit.AuditPersistRequested;
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

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    private static final String KEY_LOCKOUT_REASON = "lockoutReason";
    private static final String KEY_TTL_MINUTES = "ttlMinutes";

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishAuthSuccess(AuthSuccessEvent event) {
        log.info("Auth success: userId={} ip={}", event.userId(), event.clientIp());
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGIN_SUCCESS, event.userId(), event.email(),
                        event.clientIp(), event.userAgent(), true, null, Map.of())));
    }

    @Override
    public void publishLoginFailed(String email, String clientIp, String userAgent, String reason) {
        String maskedEmail = maskEmail(email);
        log.info("Login failed: emailMasked={} ip={} reason={}", maskedEmail, clientIp, reason);
        Map<String, Object> metadata = Map.of(KEY_LOCKOUT_REASON, reason);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGIN_FAILED, null, maskedEmail, clientIp, userAgent, false, reason, metadata)));
    }

    @Override
    public void publishLogout(UUID userId, String clientIp, String userAgent) {
        log.info("Logout: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.LOGOUT, userId, null, clientIp, userAgent, true, null, Map.of())));
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
        Map<String, Object> metadata = Map.of(KEY_TTL_MINUTES, ttlMinutes);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.PASSWORD_RESET_REQUESTED, userId, maskEmail(email), null, userAgent, true, null, metadata)));
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email, String clientIp, String userAgent) {
        log.info("Password changed: userId={} ip={}", userId, clientIp);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.PASSWORD_CHANGED, userId, email, clientIp, userAgent, true, null, Map.of())));
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
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.GOOGLE_LOGIN_SUCCESS, userId, email, clientIp, userAgent, true, null, Map.of())));
    }

    @Override
    public void publishGoogleLoginFailed(String email, String clientIp, String userAgent, String reason) {
        String maskedEmail = maskEmail(email);
        log.info("Google login failed: emailMasked={} ip={} reason={}", maskedEmail, clientIp, reason);
        Map<String, Object> metadata = Map.of(KEY_LOCKOUT_REASON, reason);
        applicationEventPublisher.publishEvent(new AuditPersistRequested(
                AuditLogEntry.of(AuditEventType.GOOGLE_LOGIN_FAILED, null, maskedEmail, clientIp, userAgent, false, reason, metadata)));
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
