package com.pwb.bootstrap.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which paths the security chain lets through without a token.
 *
 * <p>{@code SecurityConfig} has exactly two rules: the configured public endpoints are permitted, and
 * {@code anyRequest().authenticated()} catches everything else. So the whole question of what is exposed
 * reduces to the list in {@code application.yml}, and the failure mode is a quiet one — adding a pattern
 * there opens a route with no compile error, no failing controller test, and nothing in a diff that looks
 * like a security change.
 *
 * <p>The routes checked below are the ones that cost real money to serve: each one either invites bytes
 * into the bucket, queues an FFmpeg render, or bills a Google TTS request. They used to carry
 * {@code @PreAuthorize("hasRole('PRO')")} as a second line of defence. The paid tier was removed so that
 * any signed-in account can demo the feature set, which leaves authentication as the only thing in front
 * of them — hence this file, which pins the one remaining assumption rather than trusting it.
 *
 * <p>Read against the real {@code application.yml} rather than a fixture, because a fixture would only
 * prove that the test's own copy of the list is safe.
 */
@DisplayName("Security chain – what is reachable without a token")
class PublicEndpointExposureTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static List<String> publicEndpoints() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        List<String> patterns = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            for (int i = 0; ; i++) {
                Object value = source.getProperty("pwb.iam.security.public-endpoints[" + i + "]");
                if (value == null) {
                    break;
                }
                patterns.add(value.toString());
            }
        }
        return patterns;
    }

    @Test
    @DisplayName("the list is actually found, so the assertions below mean something")
    void should_read_the_configured_list() throws IOException {
        // Without this, a renamed property would turn every assertion below into a test of an empty list,
        // which passes for the wrong reason.
        assertThat(publicEndpoints())
                .as("pwb.iam.security.public-endpoints must be readable from the real application.yml")
                .contains("/api/v1/auth/login");
    }

    @ParameterizedTest(name = "{0} requires a token")
    @ValueSource(strings = {
            "/api/v1/songs/upload-url",
            "/api/v1/songs",
            "/api/v1/songs/11111111-1111-1111-1111-111111111111/retry-processing",
            "/api/v1/voice-tags/tts",
            "/api/v1/voice-tags/upload",
            "/api/v1/voice-tags/tts/preview",
            "/api/v1/admin/users"
    })
    @DisplayName("nothing that spends storage, CPU or a TTS quota is public")
    void should_not_expose_costly_routes(String path) throws IOException {
        List<String> matched = publicEndpoints().stream()
                .filter(pattern -> MATCHER.match(pattern, path))
                .toList();

        assertThat(matched)
                .as("%s would be reachable without authentication via %s", path, matched)
                .isEmpty();
    }
}
