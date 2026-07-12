package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagQuotaService {

    private static final String ACTIVE_COUNT_KEY_PREFIX = "voice_tag:active_count:";

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        RESERVE_SCRIPT = new DefaultRedisScript<>(
                """
                local n = redis.call('INCR', KEYS[1])
                if n == 1 then redis.call('EXPIRE', KEYS[1], 3600) end
                if n > tonumber(ARGV[1]) then
                  redis.call('DECR', KEYS[1])
                  return -1
                end
                return n
                """,
                Long.class);
        RELEASE_SCRIPT = new DefaultRedisScript<>(
                "return redis.call('DECR', KEYS[1])",
                Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final VoiceTagProperties properties;
    private final VoiceTagRepository voiceTagRepository;

    public void tryReserve(UUID ownerId) {
        String key = ACTIVE_COUNT_KEY_PREFIX + ownerId;
        Long result = stringRedisTemplate.execute(
                RESERVE_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(properties.getMaxActivePerUser()));
        if (result == null || result == -1L) {
            log.warn("VOICE_TAG_QUOTA_EXCEEDED userId={} max={}", ownerId, properties.getMaxActivePerUser());
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_LIMIT_EXCEEDED);
        }
    }

    public void release(UUID ownerId) {
        String key = ACTIVE_COUNT_KEY_PREFIX + ownerId;
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key));
        } catch (Exception ex) {
            log.warn("VOICE_TAG_QUOTA_RELEASE_FAILED userId={} reason={}", ownerId, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void postCheckAndCommit(UUID ownerId) {
        long count = voiceTagRepository.countActiveByOwner(ownerId);
        if (count > properties.getMaxActivePerUser()) {
            log.warn("VOICE_TAG_QUOTA_POSTCHECK_EXCEEDED userId={} count={} max={}",
                    ownerId, count, properties.getMaxActivePerUser());
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_LIMIT_EXCEEDED);
        }
    }
}
