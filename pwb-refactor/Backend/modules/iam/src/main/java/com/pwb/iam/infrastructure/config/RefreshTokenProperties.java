package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.refresh-token")
public class RefreshTokenProperties {

    private long ttlSeconds = 1_209_600L;
    private boolean rotationEnabled = true;
    private int rawTokenBytes = 48;
}