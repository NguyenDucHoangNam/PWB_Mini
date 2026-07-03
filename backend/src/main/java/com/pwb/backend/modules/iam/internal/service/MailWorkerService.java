package com.pwb.backend.modules.iam.internal.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class MailWorkerService {

  private static final String DLQ_TOPIC = "notification-events-dlq";
  private static final int MAX_RETRIES = 3;

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "notification-events", groupId = "pwb-mail-worker")
  public void consumeNotificationEvent(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      String email = payload.get("email").asText();
      String otpCode = payload.get("otpCode").asText();
      String fullName = payload.get("fullName").asText();

      sendRegistrationOtpEmail(email, otpCode, fullName);
    } catch (JsonProcessingException ex) {
      log.error("Failed to parse notification event: {}", ex.getMessage());
    }
  }

  private void sendRegistrationOtpEmail(String toEmail, String otpCode, String fullName) {
    int retryCount = 0;
    while (retryCount < MAX_RETRIES) {
      try {
        Context context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("otpCode", otpCode);

        String htmlContent = templateEngine.process("iam/registration-otp", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom("PWB MiNi <noreply@pwbmini.com>");
        helper.setTo(toEmail);
        helper.setSubject("[PWB MiNi] Mã xác thực đăng ký tài khoản: " + otpCode);
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("OTP email sent successfully to: {}", maskEmail(toEmail));
        return;
      } catch (MessagingException ex) {
        retryCount++;
        log.warn("Failed to send OTP email (attempt {}/{}): {}",
            retryCount, MAX_RETRIES, ex.getMessage());
        if (retryCount >= MAX_RETRIES) {
          log.error("Max retries exceeded for email: {}, sending to DLQ",
              maskEmail(toEmail));
        }
      }
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
