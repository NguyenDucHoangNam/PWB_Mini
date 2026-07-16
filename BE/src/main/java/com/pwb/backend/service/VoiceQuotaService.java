package com.pwb.backend.service;

import com.pwb.backend.config.VoiceTagProperties;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.utils.helper.MessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceQuotaService {

    private static final long DAILY_WINDOW_MS = Duration.ofDays(1).toMillis();

    private final StringRedisTemplate stringRedisTemplate;
    private final VoiceTagProperties voiceTagProperties;
    private final MessageHelper messageHelper;

    private DefaultRedisScript<Long> incrementScript;
    private DefaultRedisScript<Long> decrementScript;
    private DefaultRedisScript<Long> dailyScript;

    @PostConstruct
    void loadScripts() throws IOException {
        incrementScript = loadScript("scripts/redis/quota_increment.lua");
        decrementScript = loadScript("scripts/redis/quota_decrement.lua");
        dailyScript = loadScript("scripts/redis/daily_quota_add.lua");
    }

    private DefaultRedisScript<Long> loadScript(String path) throws IOException {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(readResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private String readResource(String path) throws IOException {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void preCheckActiveQuota(UUID userId) {
        String key = activeKey(userId);
        Long result = stringRedisTemplate.execute(
                incrementScript,
                List.of(key),
                String.valueOf(voiceTagProperties.getActiveLimit()),
                "3600");

        if (result == null || result < 0) {
            throw new BusinessException(
                    ErrorCode.VOICE_TAG_LIMIT_EXCEEDED,
                    voiceTagProperties.getActiveLimit());
        }
    }

    public void rollbackActiveQuota(UUID userId) {
        String key = activeKey(userId);
        stringRedisTemplate.execute(
                decrementScript,
                List.of(key));
    }

    public void decrementActiveQuota(UUID userId) {
        rollbackActiveQuota(userId);
    }

    public void preCheckDailyQuota(UUID userId) {
        String key = dailyKey(userId);
        Long result = stringRedisTemplate.execute(
                dailyScript,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(DAILY_WINDOW_MS),
                String.valueOf(voiceTagProperties.getDailyGenerationLimit()));

        if (result == null || result < 0) {
            String message = messageHelper.get(ErrorCode.VOICE_TAG_RATE_LIMIT.getMessageCode());
            throw new BusinessException(ErrorCode.VOICE_TAG_RATE_LIMIT);
        }
    }

    private String activeKey(UUID userId) {
        return "voice_tag:active_count:" + userId;
    }

    private String dailyKey(UUID userId) {
        return "voice_tag:daily_count:" + userId;
    }
}
