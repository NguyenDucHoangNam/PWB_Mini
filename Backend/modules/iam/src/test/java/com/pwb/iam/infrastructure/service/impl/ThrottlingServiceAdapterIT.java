package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.PasswordResetPolicy;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.ThrottlingService.CooldownPurpose;
import com.pwb.iam.infrastructure.config.RateLimitProperties;
import com.pwb.iam.testsupport.AbstractRedisIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThrottlingServiceAdapter — Redis integration")
class ThrottlingServiceAdapterIT extends AbstractRedisIT {

    @Autowired private ThrottlingServiceAdapter adapter;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        cleanRedis();
    }

    @AfterEach
    void cleanUp() {
        cleanRedis();
    }

    private void cleanRedis() {
        redis.delete(redis.keys("iam:ratelimit:*"));
        redis.delete(redis.keys("iam:cooldown:*"));
        redis.delete(redis.keys("iam:password-reset:*"));
    }

    @Test
    @DisplayName("consume_returns_allow_when_under_limit")
    void consume_returns_allow_when_under_limit() {
        ThrottlingService.ThrottleDecision d1 = adapter.consume("login:user1", 3, Duration.ofMinutes(1));
        ThrottlingService.ThrottleDecision d2 = adapter.consume("login:user1", 3, Duration.ofMinutes(1));
        ThrottlingService.ThrottleDecision d3 = adapter.consume("login:user1", 3, Duration.ofMinutes(1));

        assertThat(d1.allowed()).isTrue();
        assertThat(d1.remaining()).isEqualTo(2);
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.remaining()).isEqualTo(1);
        assertThat(d3.allowed()).isTrue();
        assertThat(d3.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("consume_returns_deny_when_over_limit_with_retry_after")
    void consume_returns_deny_when_over_limit_with_retry_after() {
        for (int i = 0; i < 3; i++) {
            adapter.consume("login:user2", 3, Duration.ofMinutes(1));
        }

        ThrottlingService.ThrottleDecision denied = adapter.consume("login:user2", 3, Duration.ofMinutes(1));

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThan(0);
        assertThat(denied.retryAfterSeconds()).isLessThanOrEqualTo(60);
        assertThat(denied.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("consume_separates_keys_independently")
    void consume_separates_keys_independently() {
        for (int i = 0; i < 3; i++) {
            adapter.consume("login:user-a", 3, Duration.ofMinutes(1));
        }

        ThrottlingService.ThrottleDecision denied = adapter.consume("login:user-a", 3, Duration.ofMinutes(1));
        ThrottlingService.ThrottleDecision allowed = adapter.consume("login:user-b", 3, Duration.ofMinutes(1));

        assertThat(denied.allowed()).isFalse();
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    @DisplayName("consume_returns_allow_for_null_or_blank_key")
    void consume_returns_allow_for_null_or_blank_key() {
        ThrottlingService.ThrottleDecision n = adapter.consume(null, 5, Duration.ofMinutes(1));
        ThrottlingService.ThrottleDecision e = adapter.consume("", 5, Duration.ofMinutes(1));
        ThrottlingService.ThrottleDecision b = adapter.consume("   ", 5, Duration.ofMinutes(1));

        assertThat(n.allowed()).isTrue();
        assertThat(e.allowed()).isTrue();
        assertThat(b.allowed()).isTrue();
    }

    @Test
    @DisplayName("enforceCooldown_returns_0_first_call_then_active_seconds")
    void enforceCooldown_returns_0_first_call_then_active_seconds() throws InterruptedException {
        long first = adapter.enforceCooldown("user@example.com", CooldownPurpose.REGISTER);
        assertThat(first).isEqualTo(0L);

        long second = adapter.enforceCooldown("user@example.com", CooldownPurpose.REGISTER);
        assertThat(second).isGreaterThan(0L);
        assertThat(second).isLessThanOrEqualTo(60L);

        Thread.sleep(1100);

        long third = adapter.enforceCooldown("user@example.com", CooldownPurpose.REGISTER);
        assertThat(third).isLessThan(second);
    }

    @Test
    @DisplayName("enforceCooldown_different_purposes_independent")
    void enforceCooldown_different_purposes_independent() {
        long reg = adapter.enforceCooldown("user@example.com", CooldownPurpose.REGISTER);
        long resend = adapter.enforceCooldown("user@example.com", CooldownPurpose.RESEND_OTP);

        assertThat(reg).isEqualTo(0L);
        assertThat(resend).isEqualTo(0L);

        long reg2 = adapter.enforceCooldown("user@example.com", CooldownPurpose.REGISTER);
        assertThat(reg2).isGreaterThan(0L);
    }

    @Test
    @DisplayName("enforceCooldown_returns_0_for_blank_email")
    void enforceCooldown_returns_0_for_blank_email() {
        long n = adapter.enforceCooldown(null, CooldownPurpose.REGISTER);
        long e = adapter.enforceCooldown("", CooldownPurpose.REGISTER);
        long b = adapter.enforceCooldown("   ", CooldownPurpose.REGISTER);

        assertThat(n).isEqualTo(0L);
        assertThat(e).isEqualTo(0L);
        assertThat(b).isEqualTo(0L);
    }

    @Test
    @DisplayName("enforceCooldown_normalizes_email_case")
    void enforceCooldown_normalizes_email_case() {
        long first = adapter.enforceCooldown("User@Example.COM", CooldownPurpose.RESEND_OTP);
        long second = adapter.enforceCooldown("user@example.com", CooldownPurpose.RESEND_OTP);

        assertThat(first).isEqualTo(0L);
        assertThat(second).isGreaterThan(0L);
    }

    @Test
    @DisplayName("enforceCooldownForPasswordReset_returns_0_then_active")
    void enforceCooldownForPasswordReset_returns_0_then_active() {
        long first = adapter.enforceCooldownForPasswordReset("user@example.com");
        assertThat(first).isEqualTo(0L);

        long second = adapter.enforceCooldownForPasswordReset("user@example.com");
        assertThat(second).isGreaterThan(0L);
    }

    @Test
    @DisplayName("enforceCooldownForPasswordReset_uses_password_reset_policy_cooldown")
    void enforceCooldownForPasswordReset_uses_password_reset_policy_cooldown() {
        long first = adapter.enforceCooldownForPasswordReset("user2@example.com");
        long second = adapter.enforceCooldownForPasswordReset("user2@example.com");

        assertThat(first).isEqualTo(0L);
        assertThat(second).isLessThanOrEqualTo(60L);
        assertThat(second).isGreaterThan(50L);
    }

    @Test
    @DisplayName("enforceCooldownForPasswordReset_returns_0_for_blank")
    void enforceCooldownForPasswordReset_returns_0_for_blank() {
        assertThat(adapter.enforceCooldownForPasswordReset(null)).isEqualTo(0L);
        assertThat(adapter.enforceCooldownForPasswordReset("")).isEqualTo(0L);
        assertThat(adapter.enforceCooldownForPasswordReset("   ")).isEqualTo(0L);
    }

    @Test
    @DisplayName("PasswordResetPolicy_dependency_wired")
    void passwordResetPolicy_dependency_wired() {
        PasswordResetPolicy policy = applicationContext.getBean(PasswordResetPolicy.class);
        assertThat(policy).isNotNull();
        assertThat(policy.cooldownSeconds()).isEqualTo(60L);
    }
}