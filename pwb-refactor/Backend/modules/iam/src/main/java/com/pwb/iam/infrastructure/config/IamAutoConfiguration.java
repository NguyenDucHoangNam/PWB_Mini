package com.pwb.iam.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.pwb.iam.application",
        "com.pwb.iam.infrastructure",
        "com.pwb.iam.api"
})
@EnableJpaRepositories(basePackages = "com.pwb.iam.infrastructure.persistence.repository")
@EnableConfigurationProperties({
        OtpProperties.class,
        PasswordPolicyProperties.class,
        JwtProperties.class,
        RefreshTokenProperties.class,
        LoginPolicyProperties.class,
        RateLimitProperties.class,
        PasswordResetProperties.class,
        GoogleProperties.class,
        SecurityProperties.class
})
public class IamAutoConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}