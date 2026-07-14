package com.pwb.backend.modules.audio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audio.hls")
public class HlsProperties {

    private int aesKeyBytes;

    private int segmentDurationSeconds;
}
