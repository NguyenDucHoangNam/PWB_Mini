package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.login-policy")
public class LoginPolicyProperties {

    private int maxFailures = 5;
    private int lockMinutes = 15;
    private int ipMaxFailures = 20;
    private int ipLockMinutes = 30;
}