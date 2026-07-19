package com.pwb.iam.infrastructure.security.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.notification.api.event.EmailRequestedIntegrationEvent;
import com.pwb.outbox.api.OutboxEnqueueRequested;
import com.pwb.outbox.api.OutboxEventPayload;
import com.pwb.outbox.infrastructure.messaging.OutboxKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAuthEventPublisher implements AuthEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void publishUserRegisteredOtp(UUID userId, String email, String otp) {
        EmailRequestedIntegrationEvent event = new EmailRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                email,
                "email.register.otp.subject",
                "register-otp",
                Map.of("email", email, "otp", otp),
                null,
                Instant.now()
        );
        enqueue(userId.toString(), event);
    }

    @Override
    public void publishLoginSuccess(UUID userId, String email) {
    }

    @Override
    public void publishLogout(UUID userId, String email) {
    }

    @Override
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes) {
        EmailRequestedIntegrationEvent event = new EmailRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                email,
                "email.password.reset.subject",
                "password-reset",
                Map.of("email", email, "resetLink", resetLink, "ttlMinutes", ttlMinutes),
                null,
                Instant.now()
        );
        enqueue(userId.toString(), event);
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email) {
        EmailRequestedIntegrationEvent event = new EmailRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                email,
                "email.password.changed.subject",
                "password-changed",
                Map.of("email", email),
                null,
                Instant.now()
        );
        enqueue(userId.toString(), event);
    }

    @Override
    public void publishUserRegisteredGoogle(UUID userId, String email, String fullName) {
        EmailRequestedIntegrationEvent event = new EmailRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                email,
                "email.welcome.google.subject",
                "welcome-google",
                Map.of("fullName", fullName),
                null,
                Instant.now()
        );
        enqueue(userId.toString(), event);
    }

    @Override
    public void publishUserLinkedGoogle(UUID userId, String email, String fullName) {
        EmailRequestedIntegrationEvent event = new EmailRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                email,
                "email.linked.google.subject",
                "linked-google",
                Map.of("fullName", fullName),
                null,
                Instant.now()
        );
        enqueue(userId.toString(), event);
    }

    @Override
    public void publishUserVerifiedEmail(UUID userId, String email) {
    }

    private void enqueue(String key, EmailRequestedIntegrationEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            OutboxEventPayload payload = OutboxEventPayload.of(body);
            applicationEventPublisher.publishEvent(
                    new OutboxEnqueueRequested(OutboxKafkaConfig.TOPIC_EMAIL, key, "User", payload, Map.of())
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EmailRequestedIntegrationEvent: eventId={}", event.eventId(), e);
        }
    }
}
