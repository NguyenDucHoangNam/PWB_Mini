package com.pwb.infra.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.pwb.infra.outbox.properties.OutboxProperties;
import com.pwb.infra.mail.properties.MailProperties;
import com.pwb.infra.kafka.properties.KafkaTopicProperties;

@SpringBootApplication(scanBasePackages = "com.pwb.infra.it")
@ComponentScan(
        basePackages = {
                "com.pwb.infra.mail",
                "com.pwb.infra.redis",
                "com.pwb.infra.kafka"
        }
)
@EnableJpaRepositories(basePackages = "com.pwb.infra.outbox.persistence.repository")
@EntityScan(basePackages = "com.pwb.infra.outbox.persistence.entity")
@EnableConfigurationProperties({
        OutboxProperties.class,
        MailProperties.class,
        KafkaTopicProperties.class
})
public class SharedInfraTestApp {
}

