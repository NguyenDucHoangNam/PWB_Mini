package com.pwb.infra.outbox.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "pwb.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    private Relay relay = new Relay();
    private Retry retry = new Retry();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relay {
        private boolean enabled = true;
        private long pollIntervalMs = 5000L;
        private int batchSize = 20;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Retry {
        private int maxAttempts = 3;
        private int[] backoffSeconds = {1, 5, 30};
    }
}