package com.pwb.backend.modules.audio.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.cache.DemoStatusCache;
import com.pwb.backend.modules.audio.config.StreamProperties;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.security.StreamCookieSigner;
import com.pwb.backend.modules.audio.service.StreamKeyCacheService;
import com.pwb.backend.modules.audio.service.StreamKeyService;
import com.pwb.backend.modules.audio.service.crypto.AesKeyEncryptor;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamKeyServiceImpl implements StreamKeyService {

    private final DemoDistributionRepository demoDistributionRepository;
    private final DemoRepository demoRepository;
    private final DemoStatusCache demoStatusCache;
    private final StreamCookieSigner cookieSigner;
    private final StreamKeyCacheService keyCacheService;
    private final AesKeyEncryptor aesKeyEncryptor;
    private final StreamProperties streamProperties;
    private final HttpClientContextResolver clientContextResolver;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(readOnly = true)
    public byte[] loadKey(UUID shareToken, HttpServletRequest request, HttpServletResponse response) {
        Claims claims = verifyCookie(request);
        String cookieToken = claims.get("shareToken", String.class);
        if (cookieToken == null || !cookieToken.equals(shareToken.toString())) {
            log.warn("KEY_COOKIE_TOKEN_MISMATCH urlToken={} cookieToken={}",
                    shareToken, cookieToken);
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Secure session cookie does not match requested share token");
        }

        String cookieSubnet = claims.get("clientIpSubnet", String.class);
        if (cookieSubnet == null || cookieSubnet.isBlank()) {
            log.warn("KEY_COOKIE_SUBNET_MISSING token={}", shareToken);
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Secure session cookie missing client IP subnet claim");
        }
        String requestIp = clientContextResolver.resolveIp(request);
        if (!cookieSigner.ipMatchesSubnet(requestIp, cookieSubnet)) {
            log.warn("KEY_IP_MISMATCH token={} cookieSubnet={} requestIp={}",
                    shareToken, cookieSubnet, requestIp);
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Client IP does not match the secure session cookie subnet");
        }

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                ShareRedisKeys.distributionRevokedKey(shareToken)))) {
            log.warn("KEY_JTI_REVOKED token={} reason=redis_blacklist", shareToken);
            throw new BusinessException(ShareErrorCode.LINK_REVOKED,
                    "Distribution revoked");
        }

        DemoDistribution distribution = demoDistributionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> {
                    log.warn("KEY_LINK_NOT_FOUND token={}", shareToken);
                    return new BusinessException(ShareErrorCode.LINK_NOT_FOUND);
                });

        if (distribution.isRevoked()) {
            log.warn("KEY_LINK_REVOKED token={}", shareToken);
            throw new BusinessException(ShareErrorCode.LINK_REVOKED);
        }

        DemoStatus status = resolveDemoStatus(distribution.getDemoId());
        if (status != DemoStatus.ACTIVE) {
            log.warn("KEY_DEMO_NOT_ACTIVE demoId={} status={}", distribution.getDemoId(), status);
            throw new BusinessException(ShareErrorCode.DEMO_NOT_ACTIVE);
        }

        byte[] keyBytes = keyCacheService.getKeyBytes(distribution.getDemoId())
                .orElseGet(() -> loadAndCacheKey(distribution.getDemoId()));

        log.info("KEY_SERVED token={} demoId={} keyVersion={}",
                shareToken, distribution.getDemoId(), StreamKeyCacheService.CURRENT_VERSION);
        response.setHeader("X-Stream-Key-Version", String.valueOf(StreamKeyCacheService.CURRENT_VERSION));
        return keyBytes;
    }

    private Claims verifyCookie(HttpServletRequest request) {
        String token = extractCookie(request, streamProperties.getCookieName());
        if (token == null || token.isBlank()) {
            log.warn("KEY_COOKIE_MISSING");
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Secure session cookie is required");
        }
        return cookieSigner.verify(token);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return parseCookieHeader(request.getHeader("Cookie"));
    }

    private String parseCookieHeader(String header) {
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0 && trimmed.substring(0, eq).equals(streamProperties.getCookieName())) {
                return trimmed.substring(eq + 1);
            }
        }
        return null;
    }

    private DemoStatus resolveDemoStatus(UUID demoId) {
        DemoStatus cached = demoStatusCache.get(demoId);
        if (cached != null) {
            return cached;
        }
        DemoStatus status = demoRepository.findById(demoId)
                .map(Demo::getStatus)
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Demo not found for status check"));
        demoStatusCache.put(demoId, status);
        return status;
    }

    private byte[] loadAndCacheKey(UUID demoId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Demo not found for AES key load"));
        if (demo.getAesKeyEncrypted() == null || demo.getAesKeyEncrypted().length == 0) {
            throw new BusinessException(ShareErrorCode.DEMO_NOT_ACTIVE,
                    "AES key not yet available for this demo");
        }
        byte[] keyBytes = aesKeyEncryptor.decrypt(demo.getAesKeyEncrypted());
        keyCacheService.put(demoId, keyBytes);
        return keyBytes;
    }
}
