package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    private String clientId;

    private long clockSkewSeconds = 30;
}
