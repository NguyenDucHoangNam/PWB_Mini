package com.pwb.audio.infrastructure.audio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on a user-supplied voice tag clip. A tag is stamped over a song at an interval, so it has to stay
 * short — the merge itself refuses an interval no longer than the tag.
 */
@Data
@ConfigurationProperties(prefix = "pwb.audio.voice-tag")
public class VoiceTagUploadProperties {

    private double maxDurationSeconds = 10.0;

    /** Ten seconds of lossless audio fits comfortably; the cap only exists to stop absurd uploads. */
    private long maxFileSizeBytes = 10L * 1024 * 1024;
}
