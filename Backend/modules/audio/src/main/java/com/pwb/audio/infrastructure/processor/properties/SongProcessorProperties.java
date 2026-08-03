package com.pwb.audio.infrastructure.processor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pwb.audio.processor.kafka")
public class SongProcessorProperties {

    private Boolean enabled = true;

    private String topic = "voice.processing.v1";

    private String groupId = "audio-song-processor";
}