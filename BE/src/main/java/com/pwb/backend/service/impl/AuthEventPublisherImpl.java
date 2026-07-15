package com.pwb.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.entity.rdbms.OutboxEvent;
import com.pwb.backend.enums.OutboxStatus;
import com.pwb.backend.repository.rdbms.OutboxEventRepository;
import com.pwb.backend.service.AuthEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventPublisherImpl implements AuthEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishUserRegisteredOtp(UUID userId, String email, String otp) {
        saveOutbox(userId, EVENT_REGISTER_OTP, idempotencyKey("register-otp", userId),
                Map.of("userId", userId.toString(), "email", email, "otp", otp));
    }

    @Override
    @Transactional
    public void publishLoginSuccess(UUID userId, String email) {
        saveOutbox(userId, EVENT_LOGIN_SUCCESS, idempotencyKey("login", userId),
                Map.of("userId", userId.toString(), "email", email));
    }

    @Override
    @Transactional
    public void publishLogout(UUID userId, String email) {
        saveOutbox(userId, EVENT_LOGOUT, idempotencyKey("logout", userId),
                Map.of("userId", userId.toString(), "email", email));
    }

    @Override
    @Transactional
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes) {
        Map<String, String> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("email", email);
        payload.put("resetLink", resetLink);
        payload.put("ttlMinutes", Long.toString(ttlMinutes));
        saveOutbox(userId, EVENT_PASSWORD_RESET_REQUESTED,
                idempotencyKey("password-reset", userId), payload);
    }

    @Override
    @Transactional
    public void publishPasswordChanged(UUID userId, String email) {
        saveOutbox(userId, EVENT_PASSWORD_CHANGED,
                idempotencyKey("password-changed", userId),
                Map.of("userId", userId.toString(), "email", email));
    }

    @Override
    @Transactional
    public void publishUserRegisteredGoogle(UUID userId, String email, String fullName) {
        Map<String, String> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("email", email);
        payload.put("fullName", fullName == null ? "" : fullName);
        saveOutbox(userId, EVENT_USER_REGISTERED_GOOGLE,
                idempotencyKey("registered-google", userId), payload);
    }

    private String idempotencyKey(String prefix, UUID aggregateId) {
        return prefix + ":" + aggregateId + ":" + UUID.randomUUID();
    }

    private void saveOutbox(UUID userId, String eventType, String idempotencyKey, Map<String, String> payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(AGGREGATE_USER)
                    .aggregateId(userId.toString())
                    .eventType(eventType)
                    .idempotencyKey(idempotencyKey)
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize outbox payload for user={} event={}", userId, eventType, ex);
            throw new IllegalStateException("Cannot serialize outbox payload", ex);
        }
    }
}