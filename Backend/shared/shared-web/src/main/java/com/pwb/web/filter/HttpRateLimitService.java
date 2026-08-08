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

    private static final String RATE_LIMIT_PREFIX = "pwb:ratelimit:";

    /** Scope for requests that no endpoint rule matched, and so are counted against the global limit. */
    public static final String GLOBAL_SCOPE = "global";

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

    /**
     * Counts one request against the bucket for {@code scope} and {@code subject}.
     *
     * <p>Both halves of the key earned their place by having been missing:
     *
     * <ul>
     *   <li>The <b>scope</b> used to be absent — every request incremented one counter per caller while
     *       the ceiling it was compared against came from whichever rule matched — so the buckets bled
     *       into each other in both directions. Ordinary browsing consumed the tight
     *       {@code tts-preview} allowance, and conversely that allowance never constrained previews,
     *       because the limit enforced depended on which request happened to arrive.</li>
     *   <li>The <b>subject</b> used to be the client IP for everyone. Behind carrier-grade NAT, a
     *       university or an office, that is one bucket for a whole building: twenty previews a minute
     *       shared by everybody on the same public address, with no way for an affected user to tell why
     *       they were refused. See {@code HttpRateLimitFilter#resolveSubject} for how a caller is
     *       identified now.</li>
     * </ul>
     *
     * @param scope   identifies the bucket: an endpoint rule's name, or {@link #GLOBAL_SCOPE}
     * @param subject identifies the caller, already prefixed to say which kind of identity it is
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult checkRateLimit(String scope, String subject, int limit, Duration window) {
        String key = RATE_LIMIT_PREFIX + scope + ":" + subject;
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
                // Tagged and logged with the scope rather than a constant "global": with one bucket per
                // rule, which bucket refused the request is the only thing that tells a tightened
                // endpoint limit apart from the blanket one.
                recordMetric("deny", scope);
                log.warn("Rate limit exceeded: scope={} subject={} limit={}", scope, subject, limit);
                return RateLimitResult.deny(resetSeconds);
            }
            recordMetric("allow", scope);
            return RateLimitResult.allow(remaining, resetSeconds);
        } catch (Exception ex) {
            recordMetric("allow", "fail-open");
            log.warn("Redis unavailable for rate limit check: subject={} reason={}", subject, ex.getMessage());
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