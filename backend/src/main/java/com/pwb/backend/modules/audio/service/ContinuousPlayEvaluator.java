package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContinuousPlayEvaluator {

    private static final int MIN_HEARTBEAT_IN_WINDOW = 5;
    private static final int MIN_KEY_REQUESTS_IN_WINDOW = 10;
    private static final double SUSPECT_WEIGHT = 0.5;

    private final StringRedisTemplate stringRedisTemplate;

    public ContinuousPlayResult evaluate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ContinuousPlayResult.unsupported();
        }
        try {
            long heartbeat = readLong(ShareRedisKeys.wsHeartbeatKey(sessionId));
            long keyRequests = readLong(ShareRedisKeys.keysRequestCountKey(sessionId));
            boolean heartbeatsOk = heartbeat >= MIN_HEARTBEAT_IN_WINDOW;
            boolean keysOk = keyRequests >= MIN_KEY_REQUESTS_IN_WINDOW;
            boolean continuous = heartbeatsOk && keysOk;
            double weight = continuous ? 1.0 : (heartbeatsOk ? SUSPECT_WEIGHT : 0.0);
            if (!continuous && heartbeatsOk) {
                log.warn("CONTINUOUS_PLAY_SUSPECT_FAKE sessionId={} heartbeatCount={} keysCount={}",
                        sessionId, heartbeat, keyRequests);
            }
            return new ContinuousPlayResult(continuous, weight, heartbeat, keyRequests);
        } catch (Exception ex) {
            log.warn("CONTINUOUS_PLAY_EVAL_FAILED sessionId={} reason={}",
                    sessionId, ex.getMessage());
            return ContinuousPlayResult.unsupported();
        }
    }

    private long readLong(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record ContinuousPlayResult(boolean continuous, double weight,
                                       long heartbeatCount, long keysRequestCount) {
        static ContinuousPlayResult unsupported() {
            return new ContinuousPlayResult(false, 0.0, 0L, 0L);
        }
    }
}