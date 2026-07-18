package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private int requestsPerHour = 3;
}
