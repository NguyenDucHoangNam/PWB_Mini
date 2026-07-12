package com.pwb.backend.modules.voice_tag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.voice-tag")
public class VoiceTagProperties {

    private int maxRawTextLength = 100;

    private int maxSsmlLength = 250;

    private int maxNestedDepth = 3;

    private int minMp3Bytes = 1024;

    private int maxMp3Bytes = 5 * 1024 * 1024;

    private int dailyLimitPerUser = 30;

    private int maxActivePerUser = 50;

    private long maxStorageBytesPerUser = 200L * 1024L * 1024L;

    private int voiceListCacheTtlSeconds = 24 * 60 * 60;

    private int previewUrlTtlSeconds = 30;

    private int restoreWindowDays = 7;

    private Compensation compensation = new Compensation();

    private Gcp gcp = new Gcp();

    @Getter
    @Setter
    public static class Compensation {
        private int maxAttempts = 5;
        private long initialDelayMillis = 2000L;
        private long maxDelayMillis = 60_000L;
        private double multiplier = 3.0;
    }

    @Getter
    @Setter
    public static class Gcp {
        private String endpoint = "google.cloud.texttospeech.v1.TextToSpeechClient";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 15000;
    }
}
