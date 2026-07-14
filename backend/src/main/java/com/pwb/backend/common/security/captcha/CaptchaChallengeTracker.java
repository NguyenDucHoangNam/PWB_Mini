package com.pwb.backend.common.security.captcha;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class CaptchaChallengeTracker {

    private static final String FAILURE_KEY_PREFIX = "captcha_fail:";
    private static final String EVALUATE_SCRIPT = "scripts/captcha_challenge_tracker.lua";
    private static final String INCREMENT_SCRIPT = "scripts/captcha_failure_increment.lua";

    private final StringRedisTemplate redisTemplate;
    private final TurnstileProperties properties;

    private final DefaultRedisScript<List> evaluateScript;
    private final DefaultRedisScript<Long> incrementScript;

    public CaptchaChallengeTracker(StringRedisTemplate redisTemplate, TurnstileProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.evaluateScript = loadScript(EVALUATE_SCRIPT, List.class);
        this.incrementScript = loadScript(INCREMENT_SCRIPT, Long.class);
    }

    public boolean requiresCaptcha(CaptchaContext context) {
        TurnstileProperties.Adaptive adaptive = properties.getAdaptive();
        if (adaptive == null || !adaptive.isEnabled()) {
            return false;
        }
        if (adaptive.isAlwaysRequired()) {
            return true;
        }
        String key = buildKey(context);
        try {
            List<Object> result = redisTemplate.execute(
                    evaluateScript,
                    List.of(key),
                    Integer.toString(adaptive.getFailureThreshold()),
                    Long.toString(adaptive.getTrackingWindowSeconds()));
            if (result == null || result.size() < 2) {
                return false;
            }
            long requires = ((Number) result.get(1)).longValue();
            return requires == 1L;
        } catch (Exception ex) {
            log.warn("CAPTCHA_TRACKER_EVAL_FAILED scope={} identifier={} error={}",
                    context.scope(), context.identifier(), ex.getMessage());
            return false;
        }
    }

    public long recordFailure(CaptchaContext context) {
        TurnstileProperties.Adaptive adaptive = properties.getAdaptive();
        if (adaptive == null || !adaptive.isEnabled()) {
            return 0L;
        }
        String key = buildKey(context);
        try {
            Long count = redisTemplate.execute(
                    incrementScript,
                    List.of(key),
                    Long.toString(adaptive.getTrackingWindowSeconds()));
            return count == null ? 0L : count;
        } catch (Exception ex) {
            log.warn("CAPTCHA_TRACKER_INCREMENT_FAILED scope={} identifier={} error={}",
                    context.scope(), context.identifier(), ex.getMessage());
            return 0L;
        }
    }

    public void clearFailure(CaptchaContext context) {
        TurnstileProperties.Adaptive adaptive = properties.getAdaptive();
        if (adaptive == null || !adaptive.isEnabled() || !adaptive.isBypassOnSuccess()) {
            return;
        }
        String key = buildKey(context);
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("CAPTCHA_TRACKER_CLEAR_FAILED scope={} identifier={} error={}",
                    context.scope(), context.identifier(), ex.getMessage());
        }
    }

    @PostConstruct
    void warmUp() {
    }

    private String buildKey(CaptchaContext context) {
        return FAILURE_KEY_PREFIX + context.scope().name().toLowerCase() + ":"
                + sanitize(context.identifier());
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9@._:-]", "_");
    }

    private <T> DefaultRedisScript<T> loadScript(String resource, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setResultType(resultType);
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(resource).getInputStream(),
                    StandardCharsets.UTF_8);
            script.setScriptText(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resource, ex);
        }
        return script;
    }
}
