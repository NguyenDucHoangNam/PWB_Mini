package com.pwb.liveroom.infrastructure.config;

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
@ConfigurationProperties(prefix = "app.liveroom")
public class LiveRoomProperties {

    private Code code;
    private Capacity capacity;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Code {

        private String charset;
        private int length;
        private int maxGenerationAttempts;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Capacity {

        private int defaultMaxParticipants;
        private int absoluteMaxParticipants;
    }
}