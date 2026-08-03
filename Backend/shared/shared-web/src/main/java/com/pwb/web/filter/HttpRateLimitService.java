package com.pwb.web.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class HttpRateLimitService {

    private static final String RATE_LIMIT_PREFIX = "pwb:ratelimit:global:";

    private static final String RATE_LIMIT_LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local count = redis.call('INCR', key)
            if count == 1 then
                redis.call('EXPIRE', key, window)
            end
            local ttl = redis.call('TTL', key)
            if count > limit then
                return {0, ttl, 0}
            else
                return {1, limit - count, ttl}
            end
            """;

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript rateLimitScript = new DefaultRedisScript<>();

    public HttpRateLimitService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    @SuppressWarnings("rawtypes")
    void initLuaScript() {
        rateLimitScript.setScriptText(RATE_LIMIT_LUA_SCRIPT);
        rateLimitScript.setResultType(List.class);
    }

    @SuppressWarnings("unchecked")
    public RateLimitResult checkRateLimit(String clientIp, int limit, Duration window) {
        String key = RATE_LIMIT_PREFIX + clientIp;
        try {
            List<Object> result = (List<Object>) redis.execute(
                    rateLimitScript,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(window.getSeconds())
            );
            if (result == null || result.size() < 3) {
                recordMetric("allow", "redis-error");
                return RateLimitResult.allow(limit, window.getSeconds());
            }
            long allowed = ((Number) result.get(0)).longValue();
            long remaining = ((Number) result.get(1)).longValue();
            long ttl = ((Number) result.get(2)).longValue();
            long resetSeconds = ttl > 0 ? ttl : window.getSeconds();
            if (allowed == 0) {
                recordMetric("deny", "global");
                log.warn("Global rate limit exceeded: ip={} limit={}", clientIp, limit);
                return RateLimitResult.deny(resetSeconds);
            }
            recordMetric("allow", "global");
            return RateLimitResult.allow(remaining, resetSeconds);
        } catch (Exception ex) {
            recordMetric("allow", "fail-open");
            log.warn("Redis unavailable for rate limit check: ip={} reason={}", clientIp, ex.getMessage());
            return RateLimitResult.allow(limit, window.getSeconds());
        }
    }

    private void recordMetric(String decision, String reason) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("pwb.ratelimit.decision")
                .tag("decision", decision)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds, long remaining, long resetSeconds) {
        public static RateLimitResult allow(long remaining, long resetSeconds) {
            return new RateLimitResult(true, 0L, remaining, resetSeconds);
        }

        public static RateLimitResult deny(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds, 0L, retryAfterSeconds);
        }
    }
}