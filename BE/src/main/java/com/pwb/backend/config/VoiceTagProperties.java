package com.pwb.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.voice-tag")
public class VoiceTagProperties {

    private int activeLimit = 50;

    private long storageLimitBytes = 209_715_200L;

    private int maxTextLength = 250;

    private int maxRawTextLength = 100;

    private int previewUrlTtlSeconds = 30;

    private int dailyGenerationLimit = 30;

    private CompensationRetry compensationRetry = new CompensationRetry();

    @Getter
    @Setter
    public static class CompensationRetry {

        private int maxAttempts = 5;

        private long initialBackoffMs = 1000L;

        private double multiplier = 3.0;

        private long maxBackoffMs = 60_000L;
    }
}
