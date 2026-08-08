package com.pwb.web.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Redis key, which is where the counters actually live.
 *
 * <p>{@link HttpRateLimitFilterTest} pins what the filter asks for; this pins what that turns into. The
 * distinction matters because the original defect was invisible from the filter's side — it passed a
 * per-rule limit down quite correctly, and the service then counted every request into one key per IP
 * regardless. Only an assertion on the key can tell the fixed version from the broken one.
 */
@DisplayName("HttpRateLimitService — the counter key")
class HttpRateLimitServiceTest {

    /** Already namespaced by the filter; the service only concatenates what it is handed. */
    private static final String SUBJECT = "u:11111111-2222-3333-4444-555555555555";

    private StringRedisTemplate redis;
    private HttpRateLimitService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        // Allowed, 19 remaining, 60s to reset — the shape the Lua script returns.
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(List.of(1L, 19L, 60L));
        service = new HttpRateLimitService(redis, null);
        service.initLuaScript();
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> capturedKeys() {
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(2)).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        return keys.getAllValues();
    }

    @Test
    @DisplayName("two scopes for the same caller count into two different keys")
    void should_key_each_scope_separately() {
        service.checkRateLimit("tts-preview", SUBJECT, 20, Duration.ofSeconds(60));
        service.checkRateLimit(HttpRateLimitService.GLOBAL_SCOPE, SUBJECT, 500, Duration.ofSeconds(60));

        List<List<String>> keys = capturedKeys();
        assertThat(keys.get(0)).containsExactly("pwb:ratelimit:tts-preview:" + SUBJECT);
        assertThat(keys.get(1)).containsExactly("pwb:ratelimit:global:" + SUBJECT);
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    }

    @Test
    @DisplayName("the same scope for two callers still counts separately")
    void should_keep_callers_apart_within_a_scope() {
        service.checkRateLimit("tts-preview", SUBJECT, 20, Duration.ofSeconds(60));
        service.checkRateLimit("tts-preview", "u:99999999-0000-0000-0000-000000000000", 20,
                Duration.ofSeconds(60));

        List<List<String>> keys = capturedKeys();
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    }

    /**
     * An account and an address never collide, because the filter hands down namespaced identities. Worth
     * an assertion: the two namespaces are the only thing stopping an account whose id read like an
     * address from silently sharing that address's counter.
     */
    @Test
    @DisplayName("an account and an address are different keys even within one scope")
    void should_keep_account_and_address_namespaces_apart() {
        service.checkRateLimit("tts-preview", "u:203.0.113.7", 20, Duration.ofSeconds(60));
        service.checkRateLimit("tts-preview", "ip:203.0.113.7", 20, Duration.ofSeconds(60));

        List<List<String>> keys = capturedKeys();
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    }

    @Test
    @DisplayName("Redis being unreachable lets the request through")
    void should_fail_open_when_redis_is_down() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("connection refused"));
        HttpRateLimitService failing = new HttpRateLimitService(broken, null);
        failing.initLuaScript();

        // Stated as a test because it is a deliberate asymmetry: the IAM throttler for critical
        // operations fails closed (`fail-closed-for-critical-ops`), this blanket one fails open, and a
        // reader who knows only one of the two will assume the other behaves the same way.
        HttpRateLimitService.RateLimitResult result =
                failing.checkRateLimit("tts-preview", SUBJECT, 20, Duration.ofSeconds(60));

        assertThat(result.allowed()).isTrue();
    }
}
