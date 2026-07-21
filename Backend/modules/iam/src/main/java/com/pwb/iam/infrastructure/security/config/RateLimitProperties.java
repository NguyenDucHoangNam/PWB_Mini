package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private int requestsPerHour = 100;

    private Map<String, EndpointPolicy> endpoints = new HashMap<>();

    public int resolveLimitFor(String endpoint) {
        EndpointPolicy policy = endpoints.get(endpoint);
        if (policy != null && policy.getRequestsPerHour() > 0) {
            return policy.getRequestsPerHour();
        }
        return requestsPerHour;
    }

    @Getter
    @Setter
    public static class EndpointPolicy {

        private int requestsPerHour;
    }
}