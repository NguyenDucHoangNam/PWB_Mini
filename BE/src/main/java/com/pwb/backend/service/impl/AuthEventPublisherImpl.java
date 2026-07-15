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
        saveOutbox(userId, EVENT_REGISTER_OTP, "register-otp:" + userId,
                Map.of("userId", userId.toString(), "email", email, "otp", otp));
    }

    @Override
    @Transactional
    public void publishLoginSuccess(UUID userId, String email) {
        saveOutbox(userId, EVENT_LOGIN_SUCCESS, "login:" + userId + ":" + System.currentTimeMillis(),
                Map.of("userId", userId.toString(), "email", email));
    }

    @Override
    @Transactional
    public void publishLogout(UUID userId, String email) {
        saveOutbox(userId, EVENT_LOGOUT, "logout:" + userId + ":" + System.currentTimeMillis(),
                Map.of("userId", userId.toString(), "email", email));
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
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize outbox payload for user={} event={}", userId, eventType, ex);
            throw new IllegalStateException("Cannot serialize outbox payload", ex);
        }
    }
}