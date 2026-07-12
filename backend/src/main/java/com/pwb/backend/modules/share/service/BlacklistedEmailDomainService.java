package com.pwb.backend.modules.share.service;

import com.pwb.backend.modules.share.config.ShareProperties;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.entity.BlacklistedEmailDomain;
import com.pwb.backend.modules.share.repository.BlacklistedEmailDomainRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistedEmailDomainService {

    private final BlacklistedEmailDomainRepository repository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShareProperties shareProperties;

    @PostConstruct
    public void warmCacheOnStartup() {
        try {
            refreshCache();
        } catch (Exception ex) {
            log.warn("Failed to warm blacklist domain cache on startup: {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String normalizedEmail) {
        if (!shareProperties.isBlacklistDomainCheckEnabled() || normalizedEmail == null) {
            return false;
        }
        int at = normalizedEmail.lastIndexOf('@');
        if (at < 0 || at == normalizedEmail.length() - 1) {
            return false;
        }
        String domain = normalizedEmail.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            return false;
        }
        HashOperations<String, Object, Object> hashOps = stringRedisTemplate.opsForHash();
        Boolean exists = hashOps.hasKey(ShareRedisKeys.DOMAIN_BLACKLIST_KEY, domain);
        if (exists != null && exists) {
            return true;
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(ShareRedisKeys.DOMAIN_BLACKLIST_KEY))) {
            refreshCache();
            Boolean refreshed = hashOps.hasKey(ShareRedisKeys.DOMAIN_BLACKLIST_KEY, domain);
            return Boolean.TRUE.equals(refreshed);
        }
        return false;
    }

    public void refreshCache() {
        Map<String, String> snapshot = new HashMap<>();
        for (BlacklistedEmailDomain row : repository.findAllByDeletedAtIsNull()) {
            if (row.getDomain() != null) {
                snapshot.put(row.getDomain().toLowerCase(Locale.ROOT),
                        row.getReason() == null ? "" : row.getReason());
            }
        }
        String key = ShareRedisKeys.DOMAIN_BLACKLIST_KEY;
        stringRedisTemplate.delete(key);
        if (!snapshot.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(key, snapshot);
            stringRedisTemplate.expire(key, Duration.ofSeconds(shareProperties.getBlacklistCacheTtlSeconds()));
        }
        log.info("BLACKLIST_DOMAIN_CACHE_REFRESHED count={}", snapshot.size());
    }
}