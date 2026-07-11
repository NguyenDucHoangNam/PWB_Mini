package com.pwb.backend.modules.iam.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.modules.iam.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class MailWorker {

    private final ObjectMapper objectMapper;
    private final MailService mailService;


    @KafkaListener(topics = KafkaTopics.IAM_USER_REGISTERED, groupId = "${app.kafka.mail-worker.group-id:mail-worker}")
    public void onUserRegistered(String payload) {
        handle(payload, KafkaTopics.IAM_USER_REGISTERED);
    }

    @KafkaListener(topics = KafkaTopics.IAM_OTP_RESENT, groupId = "${app.kafka.mail-worker.group-id:mail-worker}")
    public void onOtpResent(String payload) {
        handle(payload, KafkaTopics.IAM_OTP_RESENT);
    }

    private void handle(String payload, String topic) {
        try {
            UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
            mailService.sendOtpEmail(event.email(), event.fullName(), event.otp());
        } catch (Exception ex) {
            log.warn("Failed to process mail event from {}: {}", topic, ex.getMessage());
            throw new IllegalStateException("Mail event processing failed", ex);
        }
    }
}