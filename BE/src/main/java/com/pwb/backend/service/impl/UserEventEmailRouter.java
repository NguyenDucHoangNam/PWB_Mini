package com.pwb.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.config.OtpProperties;
import com.pwb.backend.kafka.config.KafkaTopicConfig;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventEmailRouter {

    private static final String SUBJECT_REGISTER = "auth.email.subject.register";
    private static final String BODY_REGISTER = "auth.email.body.register";

    private final MailService mailService;
    private final OtpProperties otpProperties;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.USER_EVENTS, groupId = "pwb-notification-mail")
    public void onUserEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = textOrNull(node, "eventType");
            if (eventType == null) {
                log.warn("Skip event without eventType: {}", payload);
                return;
            }

            switch (eventType) {
                case AuthEventPublisher.EVENT_REGISTER_OTP -> handleRegisterOtp(node);
                case AuthEventPublisher.EVENT_LOGIN_SUCCESS -> log.debug("Skip login success email: {}", payload);
                case AuthEventPublisher.EVENT_LOGOUT -> log.debug("Skip logout event: {}", payload);
                default -> log.debug("Unhandled event type: {}", eventType);
            }
        } catch (Exception ex) {
            log.error("Failed to process user event payload: {}", payload, ex);
        }
    }

    private void handleRegisterOtp(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null) {
            log.warn("Register OTP event missing data: {}", node);
            return;
        }
        String email = textOrNull(data, "email");
        String otp = textOrNull(data, "otp");
        if (email == null || otp == null) {
            log.warn("Register OTP event missing email/otp: {}", node);
            return;
        }
        long ttlMinutes = Math.max(1L, otpProperties.getTtlSeconds() / 60L);
        mailService.send(email, SUBJECT_REGISTER, BODY_REGISTER, otp, ttlMinutes);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
