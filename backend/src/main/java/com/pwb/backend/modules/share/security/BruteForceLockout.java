package com.pwb.backend.modules.share.security;

import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BruteForceLockout {

    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final long BRUTE_FORCE_THRESHOLD = 120L;

    private final StringRedisTemplate stringRedisTemplate;

    public void recordHit(UUID shareToken) {
        if (shareToken == null) {
            return;
        }
        String counterKey = "share_token:hit:" + shareToken;
        try {
            Long hits = stringRedisTemplate.opsForValue().increment(counterKey);
            if (hits != null && hits == 1L) {
                stringRedisTemplate.expire(counterKey, WINDOW);
            }
            if (hits != null && hits >= BRUTE_FORCE_THRESHOLD) {
                String lockKey = ShareRedisKeys.distributionLockedKey(shareToken);
                stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_TTL);
                log.warn("SHARE_TOKEN_BRUTE_FORCE shareToken={} hits={}", shareToken, hits);
            }
        } catch (Exception ex) {
            log.warn("BRUTE_FORCE_RECORD_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
        }
    }

    public boolean isLocked(UUID shareToken) {
        if (shareToken == null) {
            return false;
        }
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(ShareRedisKeys.distributionLockedKey(shareToken));
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception ex) {
            log.warn("BRUTE_FORCE_CHECK_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
            return false;
        }
    }
}
