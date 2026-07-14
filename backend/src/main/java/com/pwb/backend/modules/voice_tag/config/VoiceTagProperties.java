package com.pwb.backend.modules.voice_tag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.voice-tag")
public class VoiceTagProperties {

    private int maxRawTextLength;

    private int maxSsmlLength;

    private int maxNestedDepth;

    private int minMp3Bytes;

    private int maxMp3Bytes;

    private int dailyLimitPerUser;

    private int maxActivePerUser;

    private long maxStorageBytesPerUser;

    private int voiceListCacheTtlSeconds;

    private int previewUrlTtlSeconds;

    private int restoreWindowDays;

    private Compensation compensation = new Compensation();

    private Gcp gcp = new Gcp();

    @Getter
    @Setter
    public static class Compensation {
        private int maxAttempts;
        private long initialDelayMillis;
        private long maxDelayMillis;
        private double multiplier;
    }

    @Getter
    @Setter
    public static class Gcp {
        private String endpoint;
        private int connectTimeoutMs;
        private int readTimeoutMs;
    }
}
