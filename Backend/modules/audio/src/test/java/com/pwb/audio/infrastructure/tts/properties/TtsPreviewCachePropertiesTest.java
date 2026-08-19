package com.pwb.audio.infrastructure.tts.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The yml block in {@code application.yml} restates the defaults declared on the properties class, so a
 * mistyped prefix or key would bind nothing and still leave the cache behaving exactly as configured —
 * silently ignoring the file. These tests are what make that block mean something.
 */
@DisplayName("pwb.audio.tts.preview-cache – binding the configured values")
class TtsPreviewCachePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(BindingConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TtsPreviewCacheProperties.class)
    static class BindingConfig {
    }

    @Test
    @DisplayName("falls back to a short TTL and an enabled cache when nothing is configured")
    void appliesDefaults() {
        runner.run(context -> {
            TtsPreviewCacheProperties properties = context.getBean(TtsPreviewCacheProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.getMaxBytes()).isEqualTo(1024 * 1024);
        });
    }

    @Test
    @DisplayName("binds every key the yml block actually writes")
    void bindsConfiguredValues() {
        runner.withPropertyValues(
                "pwb.audio.tts.preview-cache.enabled=false",
                "pwb.audio.tts.preview-cache.ttl=45m",
                "pwb.audio.tts.preview-cache.max-bytes=2048"
        ).run(context -> {
            TtsPreviewCacheProperties properties = context.getBean(TtsPreviewCacheProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(45));
            assertThat(properties.getMaxBytes()).isEqualTo(2048);
        });
    }
}
