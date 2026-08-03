package com.pwb.iam.testsupport;

import com.pwb.iam.infrastructure.config.GoogleProperties;
import com.pwb.iam.infrastructure.config.IamJpaConfig;
import com.pwb.iam.infrastructure.config.IamPolicyConfig;
import com.pwb.iam.infrastructure.config.JwtProperties;
import com.pwb.iam.infrastructure.config.LoginPolicyProperties;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import com.pwb.iam.infrastructure.config.PasswordResetProperties;
import com.pwb.iam.infrastructure.config.RateLimitProperties;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import com.pwb.iam.infrastructure.config.SecurityProperties;
import com.pwb.iam.infrastructure.config.SeederProperties;
import com.pwb.iam.infrastructure.persistence.adapter.RoleRepositoryImpl;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class,
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class,
        com.pwb.infra.config.InfraAutoConfiguration.class,
        com.pwb.iam.infrastructure.config.IamAutoConfiguration.class
})
@EntityScan(basePackages = "com.pwb.iam.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.pwb.iam.infrastructure.persistence.repository")
@EnableTransactionManagement
@EnableConfigurationProperties({
        OtpProperties.class,
        PasswordPolicyProperties.class,
        JwtProperties.class,
        RefreshTokenProperties.class,
        LoginPolicyProperties.class,
        RateLimitProperties.class,
        PasswordResetProperties.class,
        GoogleProperties.class,
        SecurityProperties.class,
        SeederProperties.class
})
@ComponentScan(
        basePackages = {
                "com.pwb.iam.api",
                "com.pwb.iam.application",
                "com.pwb.iam.infrastructure.persistence.mapper",
                "com.pwb.iam.infrastructure.config",
                "com.pwb.iam.infrastructure.service.impl",
                "com.pwb.iam.infrastructure.security",
                "com.pwb.iam.infrastructure.security.jwt",
                "com.pwb.iam.infrastructure.mail",
                "com.pwb.iam.infrastructure.crypto",
                "com.pwb.iam.infrastructure.persistence.adapter",
                "com.pwb.web.security"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.IamAutoConfiguration",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.UserSeederService",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.JacksonConfig",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.EmailTemplateConfig",
                        "com\\.pwb\\.iam\\.infrastructure\\.audit\\.AuditPersistListener"
                }
        )
)
@Import({
        IamJpaConfig.class,
        IamPolicyConfig.class,
        RoleRepositoryImpl.class
})
public class TestIamConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}