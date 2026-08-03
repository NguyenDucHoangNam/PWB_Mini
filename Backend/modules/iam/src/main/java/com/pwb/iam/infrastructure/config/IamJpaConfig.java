package com.pwb.iam.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class IamJpaConfig {

    private static final String SYSTEM_PRINCIPAL = "system";

    @Bean
    @Primary
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(SYSTEM_PRINCIPAL);
    }
}
