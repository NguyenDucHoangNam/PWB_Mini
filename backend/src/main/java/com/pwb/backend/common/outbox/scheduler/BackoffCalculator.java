package com.pwb.backend.common.outbox.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BackoffCalculator {

    private final long initialSeconds;
    private final long maxSeconds;
    private final long multiplier;

    public BackoffCalculator(
            @Value("${app.outbox.backoff.initial-seconds}") long initialSeconds,
            @Value("${app.outbox.backoff.max-seconds}") long maxSeconds,
            @Value("${app.outbox.backoff.multiplier}") long multiplier) {
        this.initialSeconds = initialSeconds;
        this.maxSeconds = maxSeconds;
        this.multiplier = Math.max(1, multiplier);
    }

    public Duration nextDelay(int attemptCount) {
        long current = initialSeconds;
        for (int i = 1; i < attemptCount; i++) {
            current = current * multiplier;
            if (current >= maxSeconds) {
                return Duration.ofSeconds(maxSeconds);
            }
        }
        return Duration.ofSeconds(Math.min(current, maxSeconds));
    }
}