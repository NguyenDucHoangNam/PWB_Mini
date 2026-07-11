package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.domain.model.BlacklistedDomain;
import com.pwb.backend.audio.internal.infrastructure.repository.BlacklistedDomainRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDomainBlacklistService {

  private static final String CACHE_KEY = "email:domain:blacklist";
  private static final String CACHE_VERSION_KEY = "email:domain:blacklist:ver";

  private final BlacklistedDomainRepository repository;
  private final StringRedisTemplate redisTemplate;
  private final AudioProperties audioProperties;

  @Value("${app.audio.distribution.blacklist-domain-cache-ttl-seconds:3600}")
  private int cacheTtlOverride;

  @PostConstruct
  public void warmUp() {
    try {
      refreshCacheIfStale();
    } catch (Exception ex) {
      log.warn("Failed to warm up email domain blacklist cache: {}", ex.getMessage());
    }
  }

  public void validate(String email) {
    if (email == null) {
      throw new BusinessException(ErrorCode.INVALID_RECIPIENT_EMAIL, "Email is required");
    }
    int at = email.lastIndexOf('@');
    if (at < 0 || at == email.length() - 1) {
      throw new BusinessException(ErrorCode.INVALID_RECIPIENT_EMAIL, "Invalid email format");
    }
    String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
    Set<String> blocked = loadCache();
    if (blocked.contains(domain)) {
      log.warn("BLACKLISTED_DOMAIN_REJECTED domain={} producerId={}", domain, "?");
      throw new BusinessException(ErrorCode.INVALID_RECIPIENT_EMAIL,
          "Recipient email domain is blacklisted");
    }
  }

  public Set<String> loadCache() {
    refreshCacheIfStale();
    String cached = redisTemplate.opsForValue().get(CACHE_KEY);
    if (cached == null || cached.isBlank()) {
      return Set.of();
    }
    return Set.of(cached.split("\\|"));
  }

  public void refreshCache() {
    List<BlacklistedDomain> rows = repository.findAll();
    Set<String> domains = rows.stream()
        .map(r -> r.getDomain().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    String payload = String.join("|", domains);
    int ttl = cacheTtlOverride > 0
        ? cacheTtlOverride
        : audioProperties.getDistribution().getBlacklistDomainCacheTtlSeconds();
    redisTemplate.opsForValue().set(CACHE_KEY, payload, Duration.ofSeconds(ttl));
    redisTemplate.opsForValue().set(CACHE_VERSION_KEY, String.valueOf(System.currentTimeMillis()));
  }

  private void refreshCacheIfStale() {
    Boolean hasKey = redisTemplate.hasKey(CACHE_KEY);
    if (Boolean.TRUE.equals(hasKey)) {
      return;
    }
    refreshCache();
  }
}
