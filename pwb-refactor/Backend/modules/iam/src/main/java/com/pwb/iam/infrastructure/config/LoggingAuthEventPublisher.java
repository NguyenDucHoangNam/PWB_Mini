package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    @Override
    public void publishAuthSuccess(AuthSuccessEvent event) {
        log.info("Auth success: userId={} email={}", event.userId(), event.email());
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
    public void publishPasswordResetRequested(java.util.UUID userId, String email, String resetLink, long ttlMinutes) {
        log.info("Password reset requested: userId={} email={} ttlMinutes={} resetLink={}",
                userId, email, ttlMinutes, resetLink);
    }

    @Override
    public void publishPasswordChanged(java.util.UUID userId, String email) {
        log.info("Password changed: userId={} email={}", userId, email);
    }
}