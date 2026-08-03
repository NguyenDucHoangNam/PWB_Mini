package com.pwb.iam.testsupport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Configuration
public class TestClockConfig {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @Bean
    public Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    }
}