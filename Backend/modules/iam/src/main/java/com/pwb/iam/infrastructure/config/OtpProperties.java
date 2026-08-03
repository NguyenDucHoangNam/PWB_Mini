package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.otp")
public class OtpProperties {

    private int ttlMinutes = 10;
    private int resendCooldownSeconds = 60;
    private int maxAttempts = 5;
    private int codeLength = 6;
    private int dailyLimit = 10;
}
