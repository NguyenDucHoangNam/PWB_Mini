package com.pwb.infra.outbox.config;

import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import com.pwb.infra.outbox.properties.OutboxProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ConditionalOnClass(OutboxEventJpaRepository.class)
@ConditionalOnProperty(name = "pwb.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
@ComponentScan(basePackages = "com.pwb.infra.outbox")
@EnableJpaRepositories(basePackages = "com.pwb.infra.outbox")
@EnableScheduling
public class OutboxAutoConfiguration {
}