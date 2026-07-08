package com.pwb.backend.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MailConfig {

  private final Environment environment;

  @PostConstruct
  void validateMailCredentials() {
    String username = environment.getProperty("spring.mail.username");
    String password = environment.getProperty("spring.mail.password");
    String activeProfile = environment.getActiveProfiles().length > 0
        ? environment.getActiveProfiles()[0]
        : "default";

    if ("prod".equalsIgnoreCase(activeProfile)) {
      if (username == null || username.isBlank() || username.startsWith("${")) {
        throw new IllegalStateException(
            "MAIL_USERNAME must be set in the production environment.");
      }
      if (password == null || password.isBlank() || password.startsWith("${")) {
        throw new IllegalStateException(
            "MAIL_PASSWORD must be set in the production environment.");
      }
    } else if (username == null || password == null) {
      org.slf4j.LoggerFactory.getLogger(MailConfig.class)
          .warn("MAIL_USERNAME or MAIL_PASSWORD not set; email sending will fail at runtime.");
    }
  }
}