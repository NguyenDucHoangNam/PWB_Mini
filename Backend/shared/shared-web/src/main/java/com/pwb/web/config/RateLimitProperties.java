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

    /**
     * A {@code strategy} field used to sit here, fixed at {@code "per-ip"} and read by nothing. It is gone
     * rather than implemented: the filter now counts an authenticated caller by account and falls back to
     * the address only when there is no account yet, which is the behaviour every rule wants. Leaving a
     * knob that described the old behaviour, and never controlled even that, was worse than having none.
     */
    @Getter
    @Setter
    public static class EndpointRule {
        private String pattern;
        private List<String> methods;
        private int limit;
        private int windowSeconds = 60;
    }
}