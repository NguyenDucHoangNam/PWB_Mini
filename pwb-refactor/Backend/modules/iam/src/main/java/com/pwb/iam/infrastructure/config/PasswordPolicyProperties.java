package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.password-policy")
public class PasswordPolicyProperties {

    private int minLength = 12;
    private int maxLength = 128;
}
