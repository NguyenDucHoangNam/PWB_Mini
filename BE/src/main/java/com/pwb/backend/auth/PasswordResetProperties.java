package com.pwb.backend.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.auth.password-reset")
public class PasswordResetProperties {

    private long tokenTtlMinutes = 30L;

    private long cooldownSeconds = 60L;

    private String frontendUrl = "http://localhost:3000";

    private String resetPath = "/reset-password";
}