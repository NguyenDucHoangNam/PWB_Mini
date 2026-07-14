package com.pwb.backend.modules.liveroom.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.liveroom.webrtc")
public class WebRtcProperties {

    private String stunServer;

    private List<String> turnServers = new ArrayList<>();

    private String turnStaticSecret;

    @Min(60)
    private int credentialTtlSeconds;

    @Min(1)
    private int signallingRateLimitPerMinute;

    @NotBlank
    private String signallingDestinationPattern;
}
