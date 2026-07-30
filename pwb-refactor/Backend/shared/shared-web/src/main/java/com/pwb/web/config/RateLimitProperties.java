package com.pwb.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int globalLimitPerMinute = 100;
    private List<String> publicPaths = List.of("/actuator/**", "/health/**");
}
