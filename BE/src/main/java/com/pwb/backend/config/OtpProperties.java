package com.pwb.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    private int length = 6;

    private long ttlSeconds = 300L;

    private int maxAttempts = 5;

    private long resendCooldownSeconds = 60L;

    private int dailyResendLimit = 5;
}