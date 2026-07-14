package com.pwb.backend.modules.audio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audio")
public class AudioProperties {

    private String tempDir;

    private String ffmpegPath;

    private String ffprobePath;

    private long maxFileSizeBytes;

    private int workerConcurrency;

    private Presigned presigned = new Presigned();

    private Quota quota = new Quota();

    private int waveformPeaks;

    private int hlsSegmentSeconds;

    @Getter
    @Setter
    public static class Presigned {

        private long expirySeconds;

        private long uploadClaimTtlSeconds;
    }

    @Getter
    @Setter
    public static class Quota {

        private int maxActiveDemosPerUser;

        private long maxActiveAudioBytesPerUser;
    }
}