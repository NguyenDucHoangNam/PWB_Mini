package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.rate-limit")
public class RateLimitProperties {

    private int loginPerMinute = 10;
    private int refreshPerMinute = 30;
    private boolean failClosedForCriticalOps = false;
}