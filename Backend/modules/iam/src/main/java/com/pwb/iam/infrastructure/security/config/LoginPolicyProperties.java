package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.login-policy")
public class LoginPolicyProperties {

    private boolean enabled = true;

    private int maxFailAttempts = 5;

    private int lockDurationMinutes = 15;
}
