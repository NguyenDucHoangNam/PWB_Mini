package com.pwb.audio.infrastructure.audio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.processor")
public class AudioProcessorProperties {

    /**
     * Directory holding the {@code ffmpeg} and {@code ffprobe} executables — a directory, not a path to a
     * binary. Empty, the default, resolves both from {@code PATH}.
     */
    private String ffmpegDir = "";

    private String workingDir = "./tmp/pwb-audio";

    private Boolean cleanupTempFiles = true;

    private Integer orphanRetentionHours = 6;

    private Integer timeoutMinutes = 15;
}