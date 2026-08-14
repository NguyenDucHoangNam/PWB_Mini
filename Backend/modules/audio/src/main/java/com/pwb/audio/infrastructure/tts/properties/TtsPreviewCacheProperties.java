package com.pwb.audio.infrastructure.tts.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "pwb.audio.tts.preview-cache")
public class TtsPreviewCacheProperties {

    /**
     * Turning this off restores the older behaviour, where a preview left no trace anywhere at all. It
     * exists because that property was a deliberate choice before it was traded away for the saving, and
     * reversing the trade should not need a code change.
     */
    private boolean enabled = true;

    /**
     * Kept short on purpose. It only has to span one sitting — the minutes a user spends replaying a
     * phrase across voices — and every extra minute is time that rejected audio outlives its rejection.
     */
    private Duration ttl = Duration.ofMinutes(15);

    /**
     * Larger payloads are synthesised and returned as normal but never stored. A preview is a few seconds
     * of speech, so anything near this size means the caller is not doing what the endpoint is for, and
     * such a request is not the kind that gets replayed.
     */
    private int maxBytes = 1024 * 1024;
}
