package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.password-reset")
public class PasswordResetProperties {

    private String tokenSecret = "change-me-change-me-change-me-change-me-1234567890";
    private long tokenTtlMinutes = 30L;
    private long cooldownSeconds = 60L;
    private String frontendUrl = "http://localhost:3000";
    private String resetPath = "/reset-password";
}