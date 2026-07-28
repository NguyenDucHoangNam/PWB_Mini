package com.pwb.iam.domain.service;

import java.time.Duration;

public interface RateLimiter {

    Decision consume(String key, int limit, Duration window);

    record Decision(boolean allowed, long retryAfterSeconds, long remaining) {
        public static Decision allow(long remaining) {
            return new Decision(true, 0L, remaining);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds, 0L);
        }
    }
}