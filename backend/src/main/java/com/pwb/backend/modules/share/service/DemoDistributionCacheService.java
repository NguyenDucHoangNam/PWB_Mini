package com.pwb.backend.modules.share.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.share.config.ShareProperties;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.dto.response.DistributionListItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDistributionCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ShareProperties shareProperties;

    public void put(DistributionListItemResponse snapshot, UUID shareToken) {
        if (snapshot == null || shareToken == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(
                    ShareRedisKeys.DISTRIBUTION_CACHE_KEY_PREFIX + shareToken,
                    payload,
                    Duration.ofSeconds(shareProperties.getDistributionCacheTtlSeconds()));
            log.info("DISTRIBUTION_CACHE_SET shareToken={} ttlSeconds={}",
                    shareToken, shareProperties.getDistributionCacheTtlSeconds());
        } catch (JsonProcessingException ex) {
            log.warn("DISTRIBUTION_CACHE_SERIALIZE_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
        } catch (Exception ex) {
            log.warn("DISTRIBUTION_CACHE_SET_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
        }
    }

    public void evict(UUID shareToken) {
        if (shareToken == null) {
            return;
        }
        stringRedisTemplate.delete(ShareRedisKeys.DISTRIBUTION_CACHE_KEY_PREFIX + shareToken);
    }
}