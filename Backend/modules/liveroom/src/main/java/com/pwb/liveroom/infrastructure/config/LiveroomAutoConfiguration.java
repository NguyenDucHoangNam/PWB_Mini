package com.pwb.liveroom.infrastructure.config;

import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;


@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.pwb.liveroom.infrastructure.persistence")
@EnableConfigurationProperties(LiveroomConfig.class)
@ComponentScan(basePackages = {
        "com.pwb.liveroom.application",
        "com.pwb.liveroom.infrastructure",
        "com.pwb.liveroom.api"
})
public class LiveroomAutoConfiguration {
}