package com.pwb.backend.common.kafka.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    @Min(1)
    private int partitions;

    @Min(1)
    private short replicas;

    @NotBlank
    private String bootstrapServers;

    private Producer producer;
    private Consumer consumer;
    private Retry retry;
    private MailWorker mailWorker;

    @Data
    public static class Producer {
        private int retries;
        private long retryBackoffMs;
        private long acksTimeoutMs;
        private boolean idempotence;
    }

    @Data
    public static class Consumer {
        private String groupIdPrefix;
        private int maxPollRecords;
        private int concurrency;
    }

    @Data
    public static class Retry {
        private int maxAttempts;
        private long initialIntervalMs;
        private double multiplier;
        private long maxIntervalMs;
    }

    @Data
    public static class MailWorker {
        private String groupId;
    }
}
