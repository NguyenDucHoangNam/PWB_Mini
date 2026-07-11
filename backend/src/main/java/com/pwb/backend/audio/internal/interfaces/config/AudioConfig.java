package com.pwb.backend.audio.internal.interfaces.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioProperties.class)
public class AudioConfig {
}
