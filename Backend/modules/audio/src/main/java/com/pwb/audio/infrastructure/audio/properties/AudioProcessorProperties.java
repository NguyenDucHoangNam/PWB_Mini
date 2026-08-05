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

    /**
     * How long a single FFmpeg render may run before it is killed and the song marked failed. Keep it below
     * the consumer's {@code max.poll.interval.ms}, or Kafka will declare the listener dead and hand the same
     * song to another instance while this one is still working on it.
     */
    private Integer timeoutMinutes = 15;

    /**
     * Bitrate of the merged rendition. Without it FFmpeg falls back to its own MP3 default of 128k, which
     * quietly downgrades every upload that came in above that — and the merged file is the one the user
     * releases. Raising it costs encoding time and upload bytes, so it is a knob rather than a constant.
     */
    private String outputBitrate = "192k";
}