package com.pwb.backend.modules.audio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audio")
public class AudioProperties {

    private String tempDir = "/tmp/pwb-audio";

    private String ffmpegPath = "ffmpeg";

    private String ffprobePath = "ffprobe";

    private long maxFileSizeBytes = 209715200L;

    private int workerConcurrency = 2;

    private Presigned presigned = new Presigned();

    private Quota quota = new Quota();

    private int waveformPeaks = 200;

    private int hlsSegmentSeconds = 6;

    @Getter
    @Setter
    public static class Presigned {

        private long expirySeconds = 60;

        private long uploadClaimTtlSeconds = 7200L;
    }

    @Getter
    @Setter
    public static class Quota {

        private int maxActiveDemosPerUser = 50;

        private long maxActiveAudioBytesPerUser = 10737418240L;
    }
}