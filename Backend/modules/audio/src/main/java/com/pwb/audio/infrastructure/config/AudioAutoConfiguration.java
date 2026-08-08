package com.pwb.audio.infrastructure.config;

import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import com.pwb.audio.infrastructure.audio.properties.AudioUploadProperties;
import com.pwb.audio.infrastructure.audio.properties.VoiceTagUploadProperties;
import com.pwb.audio.infrastructure.processor.properties.SongProcessorProperties;
import com.pwb.audio.infrastructure.tts.properties.GoogleTtsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.pwb.audio.infrastructure.persistence")
@EnableConfigurationProperties({
        AudioProcessorProperties.class,
        AudioUploadProperties.class,
        VoiceTagUploadProperties.class,
        GoogleTtsProperties.class,
        SongProcessorProperties.class
})
@ComponentScan(basePackages = {
        "com.pwb.audio.application",
        "com.pwb.audio.infrastructure",
        "com.pwb.audio.api"
})
public class AudioAutoConfiguration {
}