package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.domain.service.LoginAttemptChecker.LockState;
import com.pwb.iam.testsupport.AbstractRedisIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisLoginAttemptChecker — Redis integration")
class RedisLoginAttemptCheckerIT extends AbstractRedisIT {

    @Autowired private RedisLoginAttemptChecker checker;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        cleanRedis();
    }

    @AfterEach
    void cleanUp() {
        cleanRedis();
    }

    private void cleanRedis() {
        redis.delete(redis.keys("iam:login:*"));
    }

    @Test
    @DisplayName("isLocked_returns_not_locked_initially")
    void isLocked_returns_not_locked_initially() {
        LockState state = checker.isLocked("user@example.com", "127.0.0.1");

        assertThat(state.locked()).isFalse();
        assertThat(state.retryAfterSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("recordFailure_below_threshold_not_locked")
    void recordFailure_below_threshold_not_locked() {
        for (int i = 0; i < 4; i++) {
            checker.recordFailure("user@example.com", "127.0.0.1");
        }

        LockState state = checker.isLocked("user@example.com", "127.0.0.1");
        assertThat(state.locked()).isFalse();
    }

    @Test
    @DisplayName("recordFailure_reaches_threshold_locks_email")
    void recordFailure_reaches_threshold_locks_email() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("user@example.com", "10.0.0.1");
        }

        LockState state = checker.isLocked("user@example.com", "10.0.0.2");
        assertThat(state.locked()).isTrue();
        assertThat(state.retryAfterSeconds()).isGreaterThan(0L);
        assertThat(state.retryAfterSeconds()).isLessThanOrEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("isLocked_returns_remaining_seconds_when_locked")
    void isLocked_returns_remaining_seconds_when_locked() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("user2@example.com", null);
        }

        LockState state = checker.isLocked("user2@example.com", null);
        assertThat(state.locked()).isTrue();
        assertThat(state.retryAfterSeconds()).isLessThanOrEqualTo(15 * 60L);
        assertThat(state.retryAfterSeconds()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("reset_clears_email_failure_and_lock")
    void reset_clears_email_failure_and_lock() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("user3@example.com", null);
        }
        assertThat(checker.isLocked("user3@example.com", null).locked()).isTrue();

        checker.reset("user3@example.com");

        assertThat(checker.isLocked("user3@example.com", null).locked()).isFalse();
    }

    @Test
    @DisplayName("reset_normalizes_email_case")
    void reset_normalizes_email_case() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("User4@Example.com", null);
        }
        assertThat(checker.isLocked("user4@example.com", null).locked()).isTrue();

        checker.reset("USER4@example.COM");

        assertThat(checker.isLocked("user4@example.com", null).locked()).isFalse();
    }

    @Test
    @DisplayName("reset_with_null_email_does_not_throw")
    void reset_with_null_email_does_not_throw() {
        checker.reset(null);
        assertThat(checker.isLocked("user@example.com", null).locked()).isFalse();
    }

    @Test
    @DisplayName("recordFailure_with_null_email_and_null_ip_is_noop")
    void recordFailure_with_null_email_and_null_ip_is_noop() {
        checker.recordFailure(null, null);

        assertThat(checker.isLocked(null, null).locked()).isFalse();
        assertThat(checker.isLocked("user@example.com", "127.0.0.1").locked()).isFalse();
    }

    @Test
    @DisplayName("recordFailure_with_blank_ip_only_counts_email")
    void recordFailure_with_blank_ip_only_counts_email() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("user5@example.com", "");
        }

        LockState state = checker.isLocked("user5@example.com", "192.168.1.1");
        assertThat(state.locked()).isTrue();
    }

    @Test
    @DisplayName("recordFailure_ip_below_threshold_not_locked")
    void recordFailure_ip_below_threshold_not_locked() {
        for (int i = 0; i < 19; i++) {
            checker.recordFailure(null, "192.168.1.100");
        }

        LockState state = checker.isLocked(null, "192.168.1.100");
        assertThat(state.locked()).isFalse();
    }

    @Test
    @DisplayName("recordFailure_ip_at_threshold_locks_ip")
    void recordFailure_ip_at_threshold_locks_ip() {
        for (int i = 0; i < 20; i++) {
            checker.recordFailure(null, "192.168.1.101");
        }

        LockState state = checker.isLocked(null, "192.168.1.101");
        assertThat(state.locked()).isTrue();
        assertThat(state.retryAfterSeconds()).isLessThanOrEqualTo(30 * 60L);
    }

    @Test
    @DisplayName("isLocked_with_null_email_checks_ip_only")
    void isLocked_with_null_email_checks_ip_only() {
        for (int i = 0; i < 20; i++) {
            checker.recordFailure(null, "192.168.1.102");
        }

        LockState state = checker.isLocked(null, "192.168.1.102");
        assertThat(state.locked()).isTrue();
    }

    @Test
    @DisplayName("resetIpLock_clears_ip_lock_and_fails")
    void resetIpLock_clears_ip_lock_and_fails() {
        for (int i = 0; i < 20; i++) {
            checker.recordFailure(null, "192.168.1.103");
        }
        assertThat(checker.isLocked(null, "192.168.1.103").locked()).isTrue();

        checker.resetIpLock("user@example.com", "192.168.1.103");

        assertThat(checker.isLocked(null, "192.168.1.103").locked()).isFalse();
    }

    @Test
    @DisplayName("resetIpLock_with_null_ip_does_not_throw")
    void resetIpLock_with_null_ip_does_not_throw() {
        checker.resetIpLock("user@example.com", null);
        checker.resetIpLock("user@example.com", "");
        checker.resetIpLock("user@example.com", "   ");
    }

    @Test
    @DisplayName("isLocked_email_lock_has_priority_over_ip")
    void isLocked_email_lock_has_priority_over_ip() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("priority@example.com", "192.168.1.200");
        }

        LockState state = checker.isLocked("priority@example.com", "192.168.1.200");
        assertThat(state.locked()).isTrue();
        assertThat(state.retryAfterSeconds()).isLessThanOrEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("email_lock_does_not_affect_other_emails")
    void email_lock_does_not_affect_other_emails() {
        for (int i = 0; i < 5; i++) {
            checker.recordFailure("locked@example.com", null);
        }

        assertThat(checker.isLocked("locked@example.com", null).locked()).isTrue();
        assertThat(checker.isLocked("other@example.com", null).locked()).isFalse();
    }

    @Test
    @DisplayName("ip_lock_does_not_affect_other_ips")
    void ip_lock_does_not_affect_other_ips() {
        for (int i = 0; i < 20; i++) {
            checker.recordFailure(null, "192.168.1.250");
        }

        assertThat(checker.isLocked(null, "192.168.1.250").locked()).isTrue();
        assertThat(checker.isLocked(null, "192.168.1.251").locked()).isFalse();
    }
}