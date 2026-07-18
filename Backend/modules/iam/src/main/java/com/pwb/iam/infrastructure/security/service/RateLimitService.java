package com.pwb.iam.infrastructure.security.service;

public interface RateLimitService {

    RateLimitDecision check(String endpoint, String clientKey);

    void reset(String endpoint, String clientKey);

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        public static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0L);
        }

        public static RateLimitDecision deny(long retryAfterSeconds) {
            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }
}
