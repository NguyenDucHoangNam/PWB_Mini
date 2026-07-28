package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.PasswordResetPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamPolicyConfig {

    @Bean
    public PasswordResetPolicy passwordResetPolicy(PasswordResetProperties properties) {
        return new PasswordResetPolicy(
                properties.getTokenSecret(),
                properties.getTokenTtlMinutes(),
                properties.getCooldownSeconds(),
                properties.getFrontendUrl(),
                properties.getResetPath()
        );
    }

    @Bean
    public LoginPolicy loginPolicy(RateLimitProperties rateLimit) {
        return new LoginPolicy(
                rateLimit.getLoginPerMinute(),
                rateLimit.getRefreshPerMinute(),
                rateLimit.getLoginPerMinute(),
                5,
                15
        );
    }
}
