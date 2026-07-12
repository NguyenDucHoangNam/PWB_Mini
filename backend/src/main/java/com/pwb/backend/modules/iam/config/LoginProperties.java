package com.pwb.backend.modules.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.login")
@Getter
@Setter
public class LoginProperties {

    private int maxConcurrentSessions = 3;
    private int maxFailedAttempts = 5;
    private long failedAttemptWindowSeconds = 900;
    private long lockoutSeconds = 900;
    private int ipMaxFailedAttempts = 20;
    private long ipAttemptWindowSeconds = 300;
    private long ipLockoutSeconds = 900;
}
