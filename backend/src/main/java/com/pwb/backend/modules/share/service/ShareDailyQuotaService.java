package com.pwb.backend.modules.share.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.share.config.ShareProperties;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareDailyQuotaService {

    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private static final DefaultRedisScript<Long> RECORD_RECIPIENT_SCRIPT;

    static {
        RECORD_RECIPIENT_SCRIPT = new DefaultRedisScript<>(
                """
                local limit = tonumber(ARGV[1])
                local now = tonumber(ARGV[2])
                local windowSeconds = tonumber(ARGV[3])
                local member = ARGV[4]
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - (windowSeconds * 1000))
                local existing = redis.call('ZSCORE', KEYS[1], member)
                if existing then
                  redis.call('ZADD', KEYS[1], now, member)
                  redis.call('EXPIRE', KEYS[1], windowSeconds + 600)
                  return 0
                end
                local count = redis.call('ZCARD', KEYS[1])
                if count >= limit then
                  return -1
                end
                redis.call('ZADD', KEYS[1], now, member)
                redis.call('EXPIRE', KEYS[1], windowSeconds + 600)
                return count + 1
                """,
                Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final ShareProperties shareProperties;

    public void assertWithinQuota(UUID producerId, String normalizedEmail) {
        long recipientLimit = shareProperties.getDailyRecipientsLimit();
        long now = Instant.now().toEpochMilli();
        Long recipientResult = stringRedisTemplate.execute(
                RECORD_RECIPIENT_SCRIPT,
                Collections.singletonList(ShareRedisKeys.DAILY_RECIPIENTS_KEY_PREFIX + producerId),
                String.valueOf(recipientLimit),
                String.valueOf(now),
                String.valueOf(QUOTA_WINDOW.toSeconds()),
                normalizedEmail);

        if (recipientResult != null && recipientResult < 0) {
            log.warn("SHARE_QUOTA_EXCEEDED producerId={} type=recipients limit={}",
                    producerId, recipientLimit);
            throw new BusinessException(ShareErrorCode.SHARE_QUOTA_EXCEEDED);
        }

        String countKey = ShareRedisKeys.DAILY_COUNT_KEY_PREFIX + producerId;
        Long current = stringRedisTemplate.opsForValue().increment(countKey);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(countKey, QUOTA_WINDOW);
        }
        if (current != null && current > shareProperties.getDailyCountLimit()) {
            log.warn("SHARE_QUOTA_EXCEEDED producerId={} type=count limit={} current={}",
                    producerId, shareProperties.getDailyCountLimit(), current);
            throw new BusinessException(ShareErrorCode.SHARE_QUOTA_EXCEEDED);
        }
    }
}