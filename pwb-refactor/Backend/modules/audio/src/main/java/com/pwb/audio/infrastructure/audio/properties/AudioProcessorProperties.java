package com.pwb.audio.infrastructure.audio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.processor")
public class AudioProcessorProperties {

    private String mode = "local";

    private String dockerContainerName = "pwb-ffmpeg";

    private String ffmpegPath = "ffmpeg";

    private Integer defaultIntervalSeconds = 60;

    private Integer defaultVolumePercentage = 30;

    private String workingDir = "./tmp/pwb-audio";

    private Boolean cleanupOnSuccess = true;
}