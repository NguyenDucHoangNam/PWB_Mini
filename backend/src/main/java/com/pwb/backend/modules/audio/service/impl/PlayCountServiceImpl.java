package com.pwb.backend.modules.audio.service.impl;

import com.pwb.backend.modules.share.entity.DemoDistribution;

import com.pwb.backend.modules.audio.security.PlaySessionFingerprint;
import com.pwb.backend.modules.audio.service.PlayCountService;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayCountServiceImpl implements PlayCountService {

    private static final Duration PLAY_SESSION_TTL = Duration.ofHours(24);
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    private final DemoDistributionRepository demoDistributionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final PlaySessionFingerprint playSessionFingerprint;

    @Override
    @Transactional
    public void recordPlay(UUID shareToken, HttpServletRequest request) {
        Optional<DemoDistribution> distributionOpt =
                demoDistributionRepository.findByShareToken(shareToken);
        if (distributionOpt.isEmpty()) {
            log.warn("PLAY_COUNT_LINK_MISSING token={}", shareToken);
            return;
        }

        String sessionId = playSessionFingerprint.compute(request);
        String key = ShareRedisKeys.playSessionKey(shareToken, sessionId);

        Boolean firstWrite = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", PLAY_SESSION_TTL);
        if (Boolean.FALSE.equals(firstWrite)) {
            log.warn("PLAY_COUNT_SPAM_BLOCKED token={} sessionId={}", shareToken, sessionId);
            trackHeartbeat(sessionId);
            return;
        }

        try {
            demoDistributionRepository.incrementPlayCount(distributionOpt.get().getId(), Instant.now());
            log.info("PLAY_COUNT_INCREMENTED token={} sessionId={}",
                    shareToken, sessionId);
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(key);
            log.error("PLAY_COUNT_INCREMENT_FAILED token={} reason={}",
                    shareToken, ex.getMessage(), ex);
            return;
        }

        trackHeartbeat(sessionId);
    }

    private void trackHeartbeat(String sessionId) {
        try {
            String key = ShareRedisKeys.wsHeartbeatKey(sessionId);
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, HEARTBEAT_TTL);
            }
        } catch (Exception ex) {
            log.warn("WS_HEARTBEAT_TRACK_FAILED sessionId={} reason={}",
                    sessionId, ex.getMessage());
        }
    }
}
