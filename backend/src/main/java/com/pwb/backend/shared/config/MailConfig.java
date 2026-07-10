package com.pwb.backend.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Mail wiring.
 *
 * <p>M6: previously only validated credentials in {@link PostConstruct} and
 * relied on Spring Boot autoconfig for the {@link JavaMailSender} bean.
 * That meant {@code spring.mail.host} not being set would only fail at
 * runtime. We now declare the bean explicitly and fail fast if the host is
 * missing whenever mail is enabled.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailConfig {

  private final Environment environment;
  private final String mailHost;
  private final int mailPort;
  private final String mailUsername;
  private final String mailPassword;
  private final boolean startTls;

  public MailConfig(
      Environment environment,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.port:587}") int mailPort,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${spring.mail.password:}") String mailPassword,
      @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean startTls) {
    this.environment = environment;
    this.mailHost = mailHost;
    this.mailPort = mailPort;
    this.mailUsername = mailUsername;
    this.mailPassword = mailPassword;
    this.startTls = startTls;
  }

  @PostConstruct
  void validateMailCredentials() {
    if (mailHost == null || mailHost.isBlank() || mailHost.startsWith("${")) {
      throw new IllegalStateException(
          "spring.mail.host must be configured when app.mail.enabled=true (or left default).");
    }
    String activeProfile = environment.getActiveProfiles().length > 0
        ? environment.getActiveProfiles()[0]
        : "default";

    if ("prod".equalsIgnoreCase(activeProfile)) {
      if (mailUsername == null || mailUsername.isBlank() || mailUsername.startsWith("${")) {
        throw new IllegalStateException(
            "MAIL_USERNAME must be set in the production environment.");
      }
      if (mailPassword == null || mailPassword.isBlank() || mailPassword.startsWith("${")) {
        throw new IllegalStateException(
            "MAIL_PASSWORD must be set in the production environment.");
      }
    } else if (mailUsername == null || mailPassword == null
        || mailUsername.isBlank() || mailPassword.isBlank()) {
      log.warn("MAIL_USERNAME or MAIL_PASSWORD not set; email sending will fail at runtime.");
    }
  }

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(mailHost);
    sender.setPort(mailPort);
    if (mailUsername != null && !mailUsername.isBlank()) {
      sender.setUsername(mailUsername);
    }
    if (mailPassword != null && !mailPassword.isBlank()) {
      sender.setPassword(mailPassword);
    }
    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
    return sender;
  }
}