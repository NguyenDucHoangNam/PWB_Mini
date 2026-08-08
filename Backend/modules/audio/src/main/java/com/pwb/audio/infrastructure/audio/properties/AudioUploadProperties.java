package com.pwb.audio.infrastructure.audio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.upload")
public class AudioUploadProperties {

    private long maxFileSizeBytes = 200L * 1024 * 1024;
}
