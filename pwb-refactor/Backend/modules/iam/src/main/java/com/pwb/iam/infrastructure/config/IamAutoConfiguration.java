package com.pwb.iam.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@Configuration
@ComponentScan(basePackages = {
        "com.pwb.iam.application",
        "com.pwb.iam.infrastructure",
        "com.pwb.iam.api"
})
@EnableJpaRepositories(basePackages = "com.pwb.iam.infrastructure.persistence.repository")
@EnableConfigurationProperties({ OtpProperties.class, PasswordPolicyProperties.class })
public class IamAutoConfiguration {
}
