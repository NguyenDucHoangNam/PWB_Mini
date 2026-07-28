package com.pwb.iam.infrastructure.security.event;

import com.pwb.iam.domain.event.AuthEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("test")
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    @Override
    public void publishUserRegisteredOtp(UUID userId, String email, String otp) {
        log.info("EVENT register-otp: userId={} email={} otp={}", userId, email, otp);
    }

    @Override
    public void publishLoginSuccess(UUID userId, String email) {
        log.info("EVENT login-success: userId={} email={}", userId, email);
    }

    @Override
    public void publishLogout(UUID userId, String email) {
        log.info("EVENT logout: userId={} email={}", userId, email);
    }

    @Override
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes) {
        log.info("EVENT password-reset-requested: userId={} email={} link={} ttl={}min",
                userId, email, resetLink, ttlMinutes);
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email) {
        log.info("EVENT password-changed: userId={} email={}", userId, email);
    }

    @Override
    public void publishUserRegisteredGoogle(UUID userId, String email, String fullName) {
        log.info("EVENT user-registered-google: userId={} email={} fullName={}", userId, email, fullName);
    }

    @Override
    public void publishUserLinkedGoogle(UUID userId, String email, String fullName) {
        log.info("EVENT user-linked-google: userId={} email={} fullName={}", userId, email, fullName);
    }

    @Override
    public void publishUserVerifiedEmail(UUID userId, String email) {
        log.info("EVENT user-verified-email: userId={} email={}", userId, email);
    }
}
