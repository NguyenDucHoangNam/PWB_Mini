package com.pwb.voice.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.voice.audio")
public class AudioProcessingProperties {

    private String tempDir = "/tmp/voice-processing";
    private int processingTimeoutMinutes = 30;
    private int maxDurationSeconds = 600;
    private long maxFileSizeBytes = 524288000L;
    private String allowedFormats = "MP3,WAV,FLAC";
}
