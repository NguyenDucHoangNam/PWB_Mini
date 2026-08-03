package com.pwb.audio.infrastructure.tts.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.tts.google")
public class GoogleTtsProperties {

    private String credentialsPath;

    private String defaultLanguageCode = "en-US";

    private String defaultVoiceName = "en-US-Standard-A";

    private String audioEncoding = "MP3";

    private Integer speakingRate = 1;

    private Double pitch = 0.0;
}