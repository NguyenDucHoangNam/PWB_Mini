package com.pwb.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.gcp.tts")
public class GcpTtsProperties {

    private String credentialsPath;

    private String projectId = "producerworkbench";
}
