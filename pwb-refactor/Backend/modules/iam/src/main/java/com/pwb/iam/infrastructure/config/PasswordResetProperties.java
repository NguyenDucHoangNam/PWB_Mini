package com.pwb.iam.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.password-reset")
public class PasswordResetProperties {

    private static final String DEFAULT_SECRET_PREFIX = "change-me-";

    private String tokenSecret = "change-me-change-me-change-me-change-me-1234567890";
    private long tokenTtlMinutes = 30L;
    private long cooldownSeconds = 60L;
    private String frontendUrl = "http://localhost:3000";
    private String resetPath = "/reset-password";

    @PostConstruct
    public void validate() {
        if (tokenSecret == null || tokenSecret.isBlank() || tokenSecret.startsWith(DEFAULT_SECRET_PREFIX)) {
            String msg = "pwb.iam.password-reset.token-secret must be configured with a secure value in production";
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }
}