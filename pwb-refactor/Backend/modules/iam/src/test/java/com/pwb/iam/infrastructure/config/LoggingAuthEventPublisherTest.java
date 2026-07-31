package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.audit.AuditEventType;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import com.pwb.iam.infrastructure.audit.AuditPersistRequested;
import com.pwb.iam.domain.model.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggingAuthEventPublisherTest {

    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private LoggingAuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LoggingAuthEventPublisher(applicationEventPublisher);
    }

    @Test
    @DisplayName("publishAuthSuccess with AuthSuccessEvent should publish AuditPersistRequested")
    void should_publish_auth_success_event() {
        UUID userId = UUID.randomUUID();
        publisher.publishAuthSuccess(AuthSuccessEvent.of(userId, "user@example.com", "10.0.0.1", "ua"));

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        AuditPersistRequested event = captor.getValue();
        assertThat(event.entry().eventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(event.entry().actorId()).isEqualTo(userId);
        assertThat(event.entry().clientIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("publishAuthSuccess with raw fields should also publish event")
    void should_publish_auth_success_with_raw_fields() {
        publisher.publishAuthSuccess(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua");

        verify(applicationEventPublisher, times(1)).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishLoginFailed should mask email and include reason in metadata")
    void should_publish_login_failed_with_masked_email() {
        publisher.publishLoginFailed("user@example.com", "10.0.0.1", "ua", "BAD_CREDENTIALS");

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().entry().actorEmail()).isEqualTo("u***@example.com");
        assertThat(captor.getValue().entry().failureReason()).isEqualTo("BAD_CREDENTIALS");
        assertThat(captor.getValue().entry().eventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
    }

    @Test
    @DisplayName("publishLogout should publish LOGOUT event")
    void should_publish_logout() {
        publisher.publishLogout(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua");
        publisher.publishLogout(UUID.randomUUID(), "10.0.0.1", "ua");

        verify(applicationEventPublisher, times(2)).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishOtpIssued and publishOtpVerified should not publish audit event")
    void should_not_persist_for_otp_events() {
        publisher.publishOtpIssued(new OtpIssuedDomainEvent(UUID.randomUUID(), "user@example.com", OtpPurpose.REGISTER, Instant.now()));
        publisher.publishOtpVerified(new OtpVerifiedDomainEvent(UUID.randomUUID(), "user@example.com", OtpPurpose.REGISTER, Instant.now()));

        verify(applicationEventPublisher, times(0)).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishPasswordResetRequested should publish audit event")
    void should_publish_password_reset_requested() {
        UUID userId = UUID.randomUUID();
        publisher.publishPasswordResetRequested(userId, "user@example.com", "http://localhost:3000/reset", 30L, "ua");

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().entry().eventType()).isEqualTo(AuditEventType.PASSWORD_RESET_REQUESTED);
    }

    @Test
    @DisplayName("publishPasswordChanged should publish PASSWORD_CHANGED event")
    void should_publish_password_changed() {
        publisher.publishPasswordChanged(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua");

        verify(applicationEventPublisher).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishUserRegisteredGoogle and publishUserLinkedGoogle should not publish audit event")
    void should_not_persist_for_google_linking() {
        publisher.publishUserRegisteredGoogle(UUID.randomUUID(), "user@example.com", "Alice");
        publisher.publishUserLinkedGoogle(UUID.randomUUID(), "user@example.com", "Alice");

        verify(applicationEventPublisher, times(0)).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishGoogleLoginSuccess should publish GOOGLE_LOGIN_SUCCESS event")
    void should_publish_google_login_success() {
        publisher.publishGoogleLoginSuccess(UUID.randomUUID(), "user@example.com", "10.0.0.1", "ua");

        verify(applicationEventPublisher).publishEvent(any(AuditPersistRequested.class));
    }

    @Test
    @DisplayName("publishGoogleLoginFailed should mask email and persist failure")
    void should_publish_google_login_failed() {
        publisher.publishGoogleLoginFailed("user@example.com", "10.0.0.1", "ua", "TOKEN_INVALID");

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().entry().eventType()).isEqualTo(AuditEventType.GOOGLE_LOGIN_FAILED);
    }

    @Test
    @DisplayName("maskEmail should handle null and short emails")
    void should_handle_null_email() {
        publisher.publishLoginFailed(null, "ip", "ua", "x");

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().entry().actorEmail()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("maskEmail should mask short email as ***")
    void should_mask_short_email() {
        publisher.publishLoginFailed("a@b.com", "ip", "ua", "x");

        ArgumentCaptor<AuditPersistRequested> captor = ArgumentCaptor.forClass(AuditPersistRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().entry().actorEmail()).isEqualTo("***");
    }
}