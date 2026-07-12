package com.pwb.backend.modules.share.security;

import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.security.IpHashUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpBruteForceCounter {

    public static final long FAIL_THRESHOLD = 50L;
    public static final Duration WINDOW = Duration.ofMinutes(5);
    public static final Duration BLOCK_TTL = Duration.ofMinutes(15);

    private static final String KEY_PREFIX = "share_token:fail_count:";

    private final StringRedisTemplate stringRedisTemplate;
    private final HttpClientContextResolver clientContextResolver;
    private final IpHashUtil ipHashUtil;

    public void recordFail(HttpServletRequest request) {
        String ip = resolveIp(request);
        if (ip == null) {
            return;
        }
        try {
            String key = KEY_PREFIX + ip;
            Long hits = stringRedisTemplate.opsForValue().increment(key);
            if (hits != null && hits == 1L) {
                stringRedisTemplate.expire(key, WINDOW);
            }
            if (hits != null && hits >= FAIL_THRESHOLD) {
                String blockKey = "share_token:ip_blocked:" + ip;
                stringRedisTemplate.opsForValue().set(blockKey, "1", BLOCK_TTL);
                log.warn("SHARE_TOKEN_IP_BRUTE_FORCE ipHash={} hits={}",
                        ipHashUtil.hash(ip), hits);
            }
        } catch (Exception ex) {
            log.warn("IP_BRUTE_FORCE_RECORD_FAILED reason={}", ex.getMessage());
        }
    }

    public boolean isIpBlocked(HttpServletRequest request) {
        String ip = resolveIp(request);
        if (ip == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey("share_token:ip_blocked:" + ip));
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = clientContextResolver.resolveIp(request);
        return (ip == null || ip.isBlank()) ? null : ip;
    }
}