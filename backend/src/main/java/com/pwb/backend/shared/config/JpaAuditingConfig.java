package com.pwb.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Configuration
// M1: @EnableJpaAuditing moved to BackendApplication so the shared module
// does not opt the whole context into auditing on import.
public class JpaAuditingConfig {

  private static final String SYSTEM_PRINCIPAL = "system";
  private static final String ANONYMOUS_PRINCIPAL = "anonymous";
  private static final String UNKNOWN_PRINCIPAL = "unknown";
  private static final int LOCAL_PART_MAX_LEN = 8;

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
      return Optional.of(sanitize(name));
    };
  }

  /**
   * H9: general-purpose principal sanitizer. We never write raw PII into
   * audit columns. The result depends on the input shape:
   *
   * <ul>
   *   <li>Emails become {@code user:&lt;first-8-chars-of-local&gt;***@&lt;domain&gt;}
   *       so the value stays useful for grouping (by domain) without leaking
   *       the full address.</li>
   *   <li>Anything containing a colon (e.g. {@code phone:+84...},
   *       {@code oauth:12345}) is replaced with a deterministic SHA-256
   *       fingerprint so the same principal always produces the same audit
   *       value.</li>
   *   <li>Anything else (OAuth subject, UUID, etc.) is hashed.</li>
   * </ul>
   */
  static String sanitize(String name) {
    int atIndex = name.indexOf('@');
    if (atIndex > 0 && atIndex < name.length() - 1) {
      String local = name.substring(0, atIndex);
      String domain = name.substring(atIndex + 1);
      int keep = Math.min(local.length(), LOCAL_PART_MAX_LEN);
      return "user:" + local.substring(0, keep) + "***@" + domain;
    }
    if (name.contains(":")) {
      // phone:+84... / oauth:12345 / etc — never store verbatim.
      return "user:" + sha256Short(name);
    }
    return "user:" + sha256Short(name);
  }

  private static String sha256Short(String input) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        sb.append(String.format("%02x", digest[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      return UNKNOWN_PRINCIPAL;
    }
  }
}