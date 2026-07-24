package com.pwb.liveroom.infrastructure.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class PlaybackRateLimiter {

    private static final long DEFAULT_CAPACITY = 10L;
    private static final Duration DEFAULT_REFILL_WINDOW = Duration.ofSeconds(10);

    private final ConcurrentHashMap<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_CAPACITY, DEFAULT_REFILL_WINDOW);
    }

    public boolean tryAcquire(String key, long capacity, Duration refillWindow) {
        long nowMs = System.currentTimeMillis();
        long windowMs = refillWindow.toMillis();
        AtomicReference<Bucket> holder = buckets.computeIfAbsent(key, k -> new AtomicReference<>(new Bucket(capacity, nowMs)));
        while (true) {
            Bucket current = holder.get();
            Bucket updated = current.refill(nowMs, windowMs, capacity);
            if (!holder.compareAndSet(current, updated)) {
                continue;
            }
            if (updated.tokens <= 0L) {
                log.warn("Playback rate limit exceeded: key={}", key);
                return false;
            }
            updated.consume();
            return true;
        }
    }

    private static final class Bucket {
        private long tokens;
        private long lastRefillMs;

        Bucket(long capacity, long nowMs) {
            this.tokens = capacity;
            this.lastRefillMs = nowMs;
        }

        Bucket refill(long nowMs, long windowMs, long capacity) {
            if (windowMs <= 0L) {
                return this;
            }
            long elapsed = nowMs - lastRefillMs;
            if (elapsed <= 0L) {
                return this;
            }
            long tokensToAdd = (elapsed * capacity) / windowMs;
            if (tokensToAdd <= 0L) {
                return this;
            }
            return new Bucket(Math.min(capacity, tokens + tokensToAdd), nowMs);
        }

        void consume() {
            tokens -= 1L;
        }
    }
}