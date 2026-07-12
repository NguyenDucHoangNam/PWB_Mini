package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagDailyQuotaService {

    private static final String DAILY_COUNT_KEY_PREFIX = "voice_tag:daily_count:";

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT;

    static {
        INCREMENT_SCRIPT = new DefaultRedisScript<>(
                """
                local limit = tonumber(ARGV[1])
                local now = tonumber(ARGV[2])
                local member = ARGV[3]
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - 86400000)
                local count = redis.call('ZCARD', KEYS[1])
                if count >= limit then
                  return -1
                end
                redis.call('ZADD', KEYS[1], now, member)
                redis.call('EXPIRE', KEYS[1], 90000)
                return count + 1
                """,
                Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final VoiceTagProperties properties;

    public void checkAndIncrement(UUID ownerId) {
        String key = DAILY_COUNT_KEY_PREFIX + ownerId;
        long now = System.currentTimeMillis();
        String member = UUID.randomUUID().toString();
        Long result = stringRedisTemplate.execute(
                INCREMENT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(properties.getDailyLimitPerUser()),
                String.valueOf(now),
                member);
        if (result != null && result == -1L) {
            log.warn("VOICE_TAG_DAILY_QUOTA_EXCEEDED userId={} limit={}",
                    ownerId, properties.getDailyLimitPerUser());
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_LIMIT_EXCEEDED);
        }
    }
}
