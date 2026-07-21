package com.pwb.liveroom.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.pwb.liveroom")
public class LiveRoomJpaConfig {
}