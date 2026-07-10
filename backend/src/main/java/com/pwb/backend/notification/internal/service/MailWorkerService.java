package com.pwb.backend.notification.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.notification.api.NotificationEvent;
import com.pwb.backend.notification.api.NotificationEventTypes;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Listens to the {@code notification-events} Kafka topic and dispatches
 * emails. Lives in the {@code notification} module so the IAM module is
 * no longer responsible for outbound email rendering.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailWorkerService {

  private static final String FROM_SYSTEM = "PWB MiNi <noreply@pwbmini.com>";
  private static final String FROM_SECURITY = "PWB MiNi Security <security@pwbmini.com>";

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  @KafkaListener(topics = "notification-events", groupId = "pwb-mail-worker")
  public void consumeNotificationEvent(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      String eventType = payload.path("eventType").asText(NotificationEventTypes.REGISTRATION_OTP);
      Locale locale = Locale.forLanguageTag(payload.path("locale").asText("vi"));

      switch (eventType) {
        case NotificationEventTypes.REGISTRATION_OTP -> sendRegistrationOtp(payload, locale);
        case NotificationEventTypes.ANOMALOUS_LOGIN -> sendAnomalousLogin(payload, locale);
        case NotificationEventTypes.WELCOME_EMAIL -> sendWelcomeEmail(payload, locale);
        case NotificationEventTypes.PASSWORD_RESET -> sendPasswordReset(payload, locale);
        case NotificationEventTypes.ACCOUNT_DELETION_REQUESTED -> sendAccountDeletionRequested(payload, locale);
        case NotificationEventTypes.SEND_SHARE_EMAIL -> sendShareDemoEmail(payload, locale);
        case NotificationEventTypes.SEND_REVOKE_NOTICE -> sendRevokeNotice(payload, locale);
        default -> log.warn("Unknown event type received in MailWorker: {}", eventType);
      }
    } catch (Exception ex) {
      log.error("Failed to process notification event: {}", ex.getMessage(), ex);
    }
  }

  /** Internal programmatic entry point so other notification services can re-use the renderer. */
  public void sendProgrammatic(NotificationEvent event) {
    Locale locale = Locale.forLanguageTag(event.locale() == null ? "vi" : event.locale());
    Map<String, Object> v = event.variables() == null ? Map.of() : event.variables();
    try {
      switch (event.eventType()) {
        case NotificationEventTypes.REGISTRATION_OTP ->
            dispatch(event.email(), event.fullName(), FROM_SYSTEM,
                "iam/registration-otp",
                messageSource.getMessage("email.registration-otp.subject",
                    new Object[]{v.get("otpCode")}, locale),
                locale, Map.of("fullName", event.fullName(), "otpCode", String.valueOf(v.get("otpCode"))));
        case NotificationEventTypes.WELCOME_EMAIL ->
            dispatch(event.email(), event.fullName(), FROM_SYSTEM,
                "iam/welcome",
                messageSource.getMessage("email.welcome.subject", null, locale),
                locale, Map.of("fullName", event.fullName()));
        default -> log.warn("sendProgrammatic: unsupported eventType {}", event.eventType());
      }
    } catch (Exception ex) {
      log.error("sendProgrammatic failed for eventType={}", event.eventType(), ex);
    }
  }

  private void sendRegistrationOtp(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String otpCode = payload.path("otpCode").asText();
    String fullName = payload.path("fullName").asText();
    String subject = messageSource.getMessage("email.registration-otp.subject",
        new Object[]{otpCode}, locale);
    dispatch(email, fullName, FROM_SYSTEM, "iam/registration-otp", subject, locale,
        Map.of("fullName", fullName, "otpCode", otpCode));
  }

  private void sendAnomalousLogin(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText();
    String subject = messageSource.getMessage("email.anomalous-login.subject", null, locale);
    dispatch(email, fullName, FROM_SECURITY, "iam/anomalous-login", subject, locale,
        Map.of(
            "fullName", fullName,
            "ipAddress", payload.path("ip").asText(),
            "location", payload.path("location").asText(),
            "deviceInfo", payload.path("device").asText(),
            "timestamp", payload.path("timestamp").asText()));
  }

  private void sendWelcomeEmail(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText();
    String subject = messageSource.getMessage("email.welcome.subject", null, locale);
    dispatch(email, fullName, FROM_SYSTEM, "iam/welcome", subject, locale,
        Map.of("fullName", fullName));
  }

  private void sendPasswordReset(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText();
    String token = payload.path("token").asText();
    String subject = messageSource.getMessage("email.password-reset.subject", null, locale);
    dispatch(email, fullName, FROM_SECURITY, "iam/password-reset", subject, locale,
        Map.of("fullName", fullName, "token", token));
  }

  private void sendAccountDeletionRequested(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText();
    String deletionDate = payload.path("deletionDate").asText();
    String subject = messageSource.getMessage("email.account-deletion.subject", null, locale);
    dispatch(email, fullName, FROM_SECURITY, "iam/account-deletion-requested", subject, locale,
        Map.of("fullName", fullName, "deletionDate", deletionDate));
  }

  private void sendShareDemoEmail(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText("Producer");
    String demoTitle = payload.path("demoTitle").asText("Untitled demo");
    String shareLink = payload.path("shareLink").asText();
    boolean allowDownload = payload.path("allowDownload").asBoolean(false);
    String subject = messageSource.getMessage("email.share-demo.subject",
        new Object[]{demoTitle}, locale);
    dispatch(email, fullName, FROM_SYSTEM, "audio/share-demo", subject, locale,
        Map.of(
            "fullName", fullName,
            "demoTitle", demoTitle,
            "shareLink", shareLink,
            "allowDownload", allowDownload));
  }

  private void sendRevokeNotice(JsonNode payload, Locale locale) {
    String email = payload.path("email").asText();
    String fullName = payload.path("fullName").asText("Listener");
    String demoTitle = payload.path("demoTitle").asText("Untitled demo");
    String subject = messageSource.getMessage("email.revoke-notice.subject",
        new Object[]{demoTitle}, locale);
    dispatch(email, fullName, FROM_SYSTEM, "audio/revoke-notice", subject, locale,
        Map.of(
            "fullName", fullName,
            "demoTitle", demoTitle,
            "reason", payload.path("reason").asText("manual")));
  }

  private void dispatch(String toEmail, String fullName, String from,
                        String template, String subject, Locale locale,
                        Map<String, Object> model) {
    if (toEmail == null || toEmail.isBlank()) {
      log.warn("Skipping email send: recipient email is blank (subject={})", subject);
      return;
    }
    try {
      Context context = new Context(locale);
      context.setVariables(model);
      String htmlContent = templateEngine.process(template, context);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(toEmail);
      helper.setSubject(subject != null ? subject : "");
      helper.setText(htmlContent != null ? htmlContent : "", true);
      mailSender.send(mimeMessage);

      log.info("Email '{}' sent to {}", subject, maskEmail(toEmail));
    } catch (MessagingException | RuntimeException ex) {
      log.error("Failed to send email to {} (subject={})", maskEmail(toEmail), subject, ex);
    }
  }

  private String maskEmail(String email) {
    if (email == null) return "***";
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) return "***" + email.substring(Math.max(0, atIndex));
    return email.charAt(0) + "***" + email.substring(atIndex);
  }
}