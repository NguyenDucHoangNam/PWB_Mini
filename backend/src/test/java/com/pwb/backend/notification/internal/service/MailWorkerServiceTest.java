package com.pwb.backend.notification.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.notification.api.NotificationEvent;
import com.pwb.backend.notification.api.NotificationEventTypes;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailWorkerServiceTest {

  private JavaMailSender mailSender;
  private TemplateEngine templateEngine;
  private ObjectMapper objectMapper;
  private MessageSource messageSource;
  private MailWorkerService worker;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    templateEngine = mock(TemplateEngine.class);
    objectMapper = new ObjectMapper();
    messageSource = mock(MessageSource.class);
    worker = new MailWorkerService(mailSender, templateEngine, objectMapper, messageSource);

    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>ok</html>");
    when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Subject");
    when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
  }

  @Test
  void consumeNotificationEvent_registrationOtp_sendsEmail() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "eventType", NotificationEventTypes.REGISTRATION_OTP,
        "email", "test@gmail.com",
        "otpCode", "123456",
        "fullName", "Test User",
        "locale", "en"));

    worker.consumeNotificationEvent(payload);

    verify(mailSender, times(1)).send(any(MimeMessage.class));
  }

  @Test
  void consumeNotificationEvent_unknownType_doesNotSend() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "eventType", "TOTALLY_UNKNOWN",
        "email", "test@gmail.com",
        "locale", "en"));

    worker.consumeNotificationEvent(payload);

    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void consumeNotificationEvent_malformedJson_doesNotThrow() {
    worker.consumeNotificationEvent("not json");
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void consumeNotificationEvent_welcomeEmail_rendersWelcomeTemplate() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "eventType", NotificationEventTypes.WELCOME_EMAIL,
        "email", "test@gmail.com",
        "fullName", "Test User",
        "locale", "vi"));

    worker.consumeNotificationEvent(payload);

    verify(templateEngine).process(eq("iam/welcome"), any(Context.class));
  }

  @Test
  void consumeNotificationEvent_passwordReset_rendersPasswordResetTemplate() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "eventType", NotificationEventTypes.PASSWORD_RESET,
        "email", "test@gmail.com",
        "fullName", "Test User",
        "token", "reset-token",
        "locale", "en"));

    worker.consumeNotificationEvent(payload);

    verify(templateEngine).process(eq("iam/password-reset"), any(Context.class));
  }

  @Test
  void consumeNotificationEvent_anomalousLogin_rendersAnomalousLoginTemplate() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "eventType", NotificationEventTypes.ANOMALOUS_LOGIN,
        "email", "test@gmail.com",
        "fullName", "Test User",
        "ip", "203.0.113.10",
        "location", "Hanoi",
        "device", "iPhone",
        "timestamp", "2026-01-01",
        "locale", "en"));

    worker.consumeNotificationEvent(payload);

    verify(templateEngine).process(eq("iam/anomalous-login"), any(Context.class));
  }

  @Test
  void sendProgrammatic_registrationOtp_sendsEmail() {
    NotificationEvent event = new NotificationEvent(
        NotificationEventTypes.REGISTRATION_OTP,
        "test@gmail.com",
        "Test User",
        "en",
        Map.of("otpCode", "999999"));

    worker.sendProgrammatic(event);

    verify(mailSender, times(1)).send(any(MimeMessage.class));
  }

  @Test
  void sendProgrammatic_unsupportedType_doesNotSend() {
    NotificationEvent event = new NotificationEvent(
        "BOGUS_TYPE",
        "test@gmail.com",
        "Test User",
        "en",
        Map.of());

    worker.sendProgrammatic(event);

    verify(mailSender, never()).send(any(MimeMessage.class));
  }
}