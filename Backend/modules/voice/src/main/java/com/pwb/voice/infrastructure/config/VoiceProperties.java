package com.pwb.voice.infrastructure.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.voice")
public class VoiceProperties {

    private Storage storage;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Storage {

        private String voiceTagsPrefix;
        private String songsOriginalPrefix;
        private String songsProcessedPrefix;
        private String keyTemplate;
    }
}
