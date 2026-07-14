package com.pwb.backend.modules.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.login")
@Getter
@Setter
public class LoginProperties {

    private int maxConcurrentSessions;
    private int maxFailedAttempts;
    private long failedAttemptWindowSeconds;
    private long lockoutSeconds;
    private int ipMaxFailedAttempts;
    private long ipAttemptWindowSeconds;
    private long ipLockoutSeconds;
}
