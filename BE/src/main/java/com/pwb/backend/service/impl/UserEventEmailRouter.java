package com.pwb.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.kafka.config.KafkaTopicConfig;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventEmailRouter {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.USER_EVENTS,
            groupId = "${app.notification.consumer-group:pwb-notification-mail}",
            containerFactory = "notificationKafkaListenerContainerFactory")
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
                case AuthEventPublisher.EVENT_PASSWORD_RESET_REQUESTED -> handlePasswordReset(node);
                case AuthEventPublisher.EVENT_PASSWORD_CHANGED -> handlePasswordChanged(node);
                case AuthEventPublisher.EVENT_USER_REGISTERED_GOOGLE -> handleWelcomeGoogle(node);
                case AuthEventPublisher.EVENT_LOGIN_SUCCESS -> log.debug("Skip login success: {}", payload);
                case AuthEventPublisher.EVENT_LOGOUT -> log.debug("Skip logout event: {}", payload);
                default -> log.debug("Unhandled event type: {}", eventType);
            }
        } catch (Exception ex) {
            log.error("Failed to process user event payload: {}", payload, ex);
            throw new RuntimeException(ex);
        }
    }

    private void handleRegisterOtp(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null) {
            log.warn("Register OTP event missing data: {}", node);
            return;
        }
        String userIdStr = textOrNull(data, "userId");
        String email = textOrNull(data, "email");
        String otp = textOrNull(data, "otp");
        if (userIdStr == null || email == null || otp == null) {
            log.warn("Register OTP event missing required fields: {}", node);
            return;
        }
        notificationService.sendOtpEmail(UUID.fromString(userIdStr), email, otp);
    }

    private void handlePasswordReset(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null) {
            log.warn("Password reset event missing data: {}", node);
            return;
        }
        String userIdStr = textOrNull(data, "userId");
        String email = textOrNull(data, "email");
        String resetLink = textOrNull(data, "resetLink");
        String ttlStr = textOrNull(data, "ttlMinutes");
        if (userIdStr == null || email == null || resetLink == null || ttlStr == null) {
            log.warn("Password reset event missing required fields: {}", node);
            return;
        }
        notificationService.sendPasswordResetLinkEmail(
                UUID.fromString(userIdStr), email, resetLink, Long.parseLong(ttlStr));
    }

    private void handlePasswordChanged(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null) {
            log.warn("Password changed event missing data: {}", node);
            return;
        }
        String userIdStr = textOrNull(data, "userId");
        String email = textOrNull(data, "email");
        if (userIdStr == null || email == null) {
            log.warn("Password changed event missing required fields: {}", node);
            return;
        }
        notificationService.sendPasswordChangedEmail(UUID.fromString(userIdStr), email);
    }

    private void handleWelcomeGoogle(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null) {
            log.warn("Welcome Google event missing data: {}", node);
            return;
        }
        String userIdStr = textOrNull(data, "userId");
        String email = textOrNull(data, "email");
        String fullName = textOrNull(data, "fullName");
        if (userIdStr == null || email == null) {
            log.warn("Welcome Google event missing required fields: {}", node);
            return;
        }
        notificationService.sendWelcomeGoogleEmail(
                UUID.fromString(userIdStr), email, fullName);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
