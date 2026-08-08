package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import com.pwb.iam.domain.model.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingAuthEventPublisherTest {

    private LoggingAuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LoggingAuthEventPublisher();
    }

    @Test
    @DisplayName("publishAuthSuccess should not throw")
    void should_publish_auth_success_event() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> publisher.publishAuthSuccess(
                AuthSuccessEvent.of(userId, "user@example.com", "10.0.0.1", "ua")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishLoginFailed should not throw")
    void should_publish_login_failed() {
        assertThatCode(() -> publisher.publishLoginFailed("user@example.com", "10.0.0.1", "ua", "BAD_CREDENTIALS"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishLogout should not throw")
    void should_publish_logout() {
        assertThatCode(() -> publisher.publishLogout(UUID.randomUUID(), "10.0.0.1", "ua"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishOtpIssued and publishOtpVerified should not throw")
    void should_not_throw_for_otp_events() {
        assertThatCode(() -> {
            publisher.publishOtpIssued(new OtpIssuedDomainEvent(UUID.randomUUID(), "user@example.com", OtpPurpose.REGISTER, Instant.now()));
            publisher.publishOtpVerified(new OtpVerifiedDomainEvent(UUID.randomUUID(), "user@example.com", OtpPurpose.REGISTER, Instant.now()));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishPasswordResetRequested should not throw")
    void should_publish_password_reset_requested() {
        assertThatCode(() -> publisher.publishPasswordResetRequested(
                UUID.randomUUID(), "user@example.com", "http://localhost:3000/reset", 30L, "ua"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishPasswordChanged should not throw")
    void should_publish_password_changed() {
        assertThatCode(() -> publisher.publishPasswordChanged(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishUserRegisteredGoogle and publishUserLinkedGoogle should not throw")
    void should_not_throw_for_google_linking() {
        assertThatCode(() -> {
            publisher.publishUserRegisteredGoogle(UUID.randomUUID(), "user@example.com", "Alice");
            publisher.publishUserLinkedGoogle(UUID.randomUUID(), "user@example.com", "Alice");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishGoogleLoginSuccess should not throw")
    void should_publish_google_login_success() {
        assertThatCode(() -> publisher.publishGoogleLoginSuccess(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("publishGoogleLoginFailed should not throw")
    void should_publish_google_login_failed() {
        assertThatCode(() -> publisher.publishGoogleLoginFailed("user@example.com", "10.0.0.1", "ua", "TOKEN_INVALID"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maskEmail should handle null email without throwing")
    void should_handle_null_email() {
        assertThatCode(() -> publisher.publishLoginFailed(null, "ip", "ua", "x"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maskEmail should handle short email without throwing")
    void should_handle_short_email() {
        assertThatCode(() -> publisher.publishLoginFailed("a@b.com", "ip", "ua", "x"))
                .doesNotThrowAnyException();
    }
}