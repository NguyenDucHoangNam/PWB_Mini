package com.pwb.infra.redis.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisKeyPatterns — Redis key prefix constants")
class RedisKeyPatternsTest {

    @Test
    @DisplayName("should_expose_login_fail_email_prefix")
    void should_expose_login_fail_email_prefix() {
        assertThat(RedisKeyPatterns.LOGIN_FAIL_EMAIL).isEqualTo("login:fail:email:");
    }

    @Test
    @DisplayName("should_expose_login_fail_ip_prefix")
    void should_expose_login_fail_ip_prefix() {
        assertThat(RedisKeyPatterns.LOGIN_FAIL_IP).isEqualTo("login:fail:ip:");
    }

    @Test
    @DisplayName("should_expose_login_lock_prefixes")
    void should_expose_login_lock_prefixes() {
        assertThat(RedisKeyPatterns.LOGIN_LOCK_EMAIL).isEqualTo("login:lock:email:");
        assertThat(RedisKeyPatterns.LOGIN_LOCK_IP).isEqualTo("login:lock:ip:");
    }

    @Test
    @DisplayName("should_expose_ratelimit_prefix")
    void should_expose_ratelimit_prefix() {
        assertThat(RedisKeyPatterns.RATELIMIT_PREFIX).isEqualTo("ratelimit:");
    }

    @Test
    @DisplayName("should_expose_refresh_token_prefixes")
    void should_expose_refresh_token_prefixes() {
        assertThat(RedisKeyPatterns.REFRESH_TOKEN).isEqualTo("refresh:token:");
        assertThat(RedisKeyPatterns.REFRESH_USER).isEqualTo("refresh:user:");
    }

    @Test
    @DisplayName("should_expose_otp_prefixes")
    void should_expose_otp_prefixes() {
        assertThat(RedisKeyPatterns.OTP_PREFIX).isEqualTo("otp:");
        assertThat(RedisKeyPatterns.OTP_LAST_SENT).isEqualTo("otp:last-sent:");
        assertThat(RedisKeyPatterns.OTP_DAILY_COUNT).isEqualTo("otp:daily-count:");
    }

    @Test
    @DisplayName("should_expose_password_reset_cooldown_prefix")
    void should_expose_password_reset_cooldown_prefix() {
        assertThat(RedisKeyPatterns.PASSWORD_RESET_COOLDOWN).isEqualTo("password-reset:cooldown:");
    }

    @Test
    @DisplayName("should_use_colon_as_namespace_separator")
    void should_use_colon_as_namespace_separator() {
        for (String prefix : new String[]{
                RedisKeyPatterns.LOGIN_FAIL_EMAIL,
                RedisKeyPatterns.LOGIN_FAIL_IP,
                RedisKeyPatterns.RATELIMIT_PREFIX,
                RedisKeyPatterns.REFRESH_TOKEN,
                RedisKeyPatterns.OTP_PREFIX,
                RedisKeyPatterns.PASSWORD_RESET_COOLDOWN}) {
            assertThat(prefix).endsWith(":");
        }
    }
}