package com.pwb.backend.common.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled = true;
    private long defaultPermitsPerWindow = 100;
    private Duration defaultWindow = Duration.ofMinutes(1);
    private List<RateLimitRule> rules = new ArrayList<>();
}