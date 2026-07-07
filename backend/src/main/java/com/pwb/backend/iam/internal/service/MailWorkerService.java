package com.pwb.backend.iam.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.context.MessageSource;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailWorkerService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  @KafkaListener(topics = "notification-events", groupId = "pwb-mail-worker")
  public void consumeNotificationEvent(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      String eventType = payload.has("eventType") ? payload.get("eventType").asText() : "REGISTRATION_OTP";
      String localeStr = payload.has("locale") ? payload.get("locale").asText() : "vi";
      Locale locale = Locale.forLanguageTag(localeStr);

      switch (eventType) {
        case "REGISTRATION_OTP":
          handleRegistrationOtp(payload, locale);
          break;
        case "ANOMALOUS_LOGIN":
          handleAnomalousLogin(payload, locale);
          break;
        case "WELCOME_EMAIL":
          handleWelcomeEmail(payload, locale);
          break;
        case "PASSWORD_RESET":
          handlePasswordReset(payload, locale);
          break;
        case "ACCOUNT_DELETION_REQUESTED":
          handleAccountDeletionRequested(payload, locale);
          break;
        default:
          log.warn("Unknown event type received in MailWorker: {}", eventType);
      }
    } catch (JsonProcessingException ex) {
      log.error("Failed to parse notification event: {}", ex.getMessage());
    } catch (Exception ex) {
      log.error("Unexpected error processing notification event: {}", ex.getMessage(), ex);
    }
  }

  private void handleRegistrationOtp(JsonNode payload, Locale locale) {
    String email = payload.get("email").asText();
    String otpCode = payload.get("otpCode").asText();
    String fullName = payload.get("fullName").asText();
    sendRegistrationOtpEmail(email, otpCode, fullName, locale);
  }

  private void handleAnomalousLogin(JsonNode payload, Locale locale) {
    String email = payload.get("email").asText();
    String fullName = payload.get("fullName").asText();
    String ip = payload.get("ip").asText();
    String location = payload.get("location").asText();
    String device = payload.get("device").asText();
    String timestamp = payload.get("timestamp").asText();
    sendAnomalousLoginEmail(email, fullName, ip, location, device, timestamp, locale);
  }

  private void handleWelcomeEmail(JsonNode payload, Locale locale) {
    String email = payload.get("email").asText();
    String fullName = payload.get("fullName").asText();
    sendWelcomeEmail(email, fullName, locale);
  }

  private void handlePasswordReset(JsonNode payload, Locale locale) {
    String email = payload.get("email").asText();
    String fullName = payload.get("fullName").asText();
    String token = payload.get("token").asText();
    sendPasswordResetEmail(email, fullName, token, locale);
  }

  private void sendPasswordResetEmail(String toEmail, String fullName, String token, Locale locale) {
    try {
      Context context = new Context(locale);
      context.setVariable("fullName", fullName);
      context.setVariable("token", token);

      String htmlContent = templateEngine.process("iam/password-reset", context);
      String subject = messageSource.getMessage("email.password-reset.subject", null, locale);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom("PWB MiNi Security <security@pwbmini.com>");
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Password reset email sent successfully to: {}", maskEmail(toEmail));
    } catch (MessagingException ex) {
      log.error("Failed to send password reset email to: {}", maskEmail(toEmail), ex);
    }
  }

  private void handleAccountDeletionRequested(JsonNode payload, Locale locale) {
    String email = payload.get("email").asText();
    String fullName = payload.get("fullName").asText();
    String deletionDate = payload.get("deletionDate").asText();
    sendAccountDeletionRequestedEmail(email, fullName, deletionDate, locale);
  }

  private void sendAccountDeletionRequestedEmail(String toEmail, String fullName, String deletionDate, Locale locale) {
    try {
      Context context = new Context(locale);
      context.setVariable("fullName", fullName);
      context.setVariable("deletionDate", deletionDate);

      String htmlContent = templateEngine.process("iam/account-deletion-requested", context);
      String subject = messageSource.getMessage("email.account-deletion.subject", null, locale);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom("PWB MiNi Security <security@pwbmini.com>");
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Account deletion request email sent successfully to: {}", maskEmail(toEmail));
    } catch (MessagingException ex) {
      log.error("Failed to send account deletion email to: {}", maskEmail(toEmail), ex);
    }
  }

  private void sendRegistrationOtpEmail(String toEmail, String otpCode, String fullName, Locale locale) {
    try {
      Context context = new Context(locale);
      context.setVariable("fullName", fullName);
      context.setVariable("otpCode", otpCode);

      String htmlContent = templateEngine.process("iam/registration-otp", context);
      String subject = messageSource.getMessage("email.registration-otp.subject", new Object[]{otpCode}, locale);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom("PWB MiNi <noreply@pwbmini.com>");
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("OTP email sent successfully to: {}", maskEmail(toEmail));
    } catch (MessagingException ex) {
      log.error("Failed to send OTP email to: {}", maskEmail(toEmail), ex);
    }
  }

  private void sendAnomalousLoginEmail(String toEmail, String fullName, String ip, String location, String device, String timestamp, Locale locale) {
    try {
      Context context = new Context(locale);
      context.setVariable("fullName", fullName);
      context.setVariable("ipAddress", ip);
      context.setVariable("location", location);
      context.setVariable("deviceInfo", device);
      context.setVariable("timestamp", timestamp);

      String htmlContent = templateEngine.process("iam/anomalous-login", context);
      String subject = messageSource.getMessage("email.anomalous-login.subject", null, locale);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom("PWB MiNi Security <security@pwbmini.com>");
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Anomalous login warning email sent successfully to: {}", maskEmail(toEmail));
    } catch (MessagingException ex) {
      log.error("Failed to send anomalous login email to: {}", maskEmail(toEmail), ex);
    }
  }

  private void sendWelcomeEmail(String toEmail, String fullName, Locale locale) {
    try {
      Context context = new Context(locale);
      context.setVariable("fullName", fullName);

      String htmlContent = templateEngine.process("iam/welcome", context);
      String subject = messageSource.getMessage("email.welcome.subject", null, locale);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom("PWB MiNi <noreply@pwbmini.com>");
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Welcome email sent successfully to: {}", maskEmail(toEmail));
    } catch (MessagingException ex) {
      log.error("Failed to send welcome email to: {}", maskEmail(toEmail), ex);
    }
  }

  private String maskEmail(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***" + email.substring(atIndex);
    }
    return email.charAt(0) + "***" + email.substring(atIndex);
  }
}
