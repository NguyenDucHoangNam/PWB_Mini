package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    private long tokenTtlMinutes = 30L;

    private long cooldownSeconds = 60L;

    private String frontendUrl = "http://localhost:3000";

    private String resetPath = "/auth/reset-password";
}
