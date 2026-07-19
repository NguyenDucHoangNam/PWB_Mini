package com.pwb.voice.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.voice.tts")
public class TtsProperties {

    private String credentialsPath;
    private String defaultLanguage = "en-US";
    private String defaultVoiceName = "en-US-Standard-A";
    private double speakingRate = 1.0;
    private double pitch = 0.0;
    private int cacheTtlHours = 168;
    private String cacheKeyPrefix = "voice:tts:";
}
