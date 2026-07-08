package com.pwb.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

  private static final String SYSTEM_PRINCIPAL = "system";
  private static final String ANONYMOUS_PRINCIPAL = "anonymous";

  @Bean
  public AuditorAware<String> auditorProvider() {
    return () -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null
          || !authentication.isAuthenticated()
          || ANONYMOUS_PRINCIPAL.equals(authentication.getPrincipal())) {
        return Optional.of(SYSTEM_PRINCIPAL);
      }
      String name = authentication.getName();
      if (name == null || name.isBlank() || SYSTEM_PRINCIPAL.equals(name)) {
        return Optional.of(SYSTEM_PRINCIPAL);
      }
      int atIndex = name.indexOf('@');
      if (atIndex > 0 && atIndex < name.length() - 1) {
        return Optional.of("user:" + name.substring(0, Math.min(atIndex, 8)) + "***");
      }
      return Optional.of("user:" + name);
    };
  }
}
