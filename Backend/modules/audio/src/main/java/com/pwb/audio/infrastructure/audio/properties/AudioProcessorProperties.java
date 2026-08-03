package com.pwb.audio.infrastructure.audio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.processor")
public class AudioProcessorProperties {

    private String ffmpegPath = "ffmpeg";

    private String workingDir = "./tmp/pwb-audio";

    private Boolean cleanupTempFiles = true;

    private Integer orphanRetentionHours = 6;

    private Integer timeoutMinutes = 15;
}