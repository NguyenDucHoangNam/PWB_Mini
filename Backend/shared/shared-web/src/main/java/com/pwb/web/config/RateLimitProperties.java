package com.pwb.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int globalLimitPerMinute = 500;
    private List<String> publicPaths = List.of("/actuator/**", "/health/**");
    private Map<String, EndpointRule> endpointLimits = Map.of();
    private List<String> trustedProxies = List.of();

    @Getter
    @Setter
    public static class EndpointRule {
        private String pattern;
        private List<String> methods;
        private int limit;
        private int windowSeconds = 60;
        private String strategy = "per-ip";
    }
}