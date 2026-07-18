package com.pwb.outbox.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.outbox.retry")
public class OutboxRetryConfig {

    private int maxAttempts = 3;
    private int[] backoffSeconds = {1, 5, 30};

    public int getBackoffForAttempt(int attempt) {
        if (attempt < 0 || attempt >= backoffSeconds.length) {
            return backoffSeconds[backoffSeconds.length - 1];
        }
        return backoffSeconds[attempt];
    }
}
