package com.pwb.backend.iam.internal.application.helper;

import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import com.pwb.backend.iam.internal.domain.model.User;
import com.pwb.backend.iam.internal.application.service.GeoIpService;
import com.pwb.backend.iam.internal.application.service.JwtService;
import com.pwb.backend.shared.web.security.ClientIpResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SessionMetadataBuilder {

    public Map<String, String> buildForNewSession(User user, JwtService jwtService, GeoIpService geoIpService,
                                                  String accessToken, ClientIpResolver clientIpResolver) {
        if (clientIpResolver == null) {
            throw new IllegalArgumentException(
                "SessionMetadataBuilder.buildForNewSession requires a non-null ClientIpResolver");
        }
        String ip = clientIpResolver.current();
        String ua = userAgent();
        String browser = ua != null ? ua : "Unknown";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("ip", ip);
        metadata.put("browser", browser);
        String os = UserAgentParser.detectOs(ua);
        metadata.put("os", os);
        metadata.put("device", formatDevice(browser, os));
        metadata.put("location", geoIpService.getLocation(ip));
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("active_jwt_signature", jwtService.getSignature(accessToken));
        return metadata;
    }

    public static String formatDevice(String browser, String os) {
        if (browser == null || browser.isBlank() || "Unknown".equals(browser)) {
            return os == null || "Unknown".equals(os) ? "Unknown" : os;
        }
        if (os == null || "Unknown".equals(os)) {
            return browser;
        }
        return browser + " (" + os + ")";
    }

    public void store(StringRedisTemplate redisTemplate, String refreshToken, Map<String, String> metadata,
                      IamProperties iamProperties) {
        String key = "session:metadata:" + refreshToken;
        redisTemplate.opsForHash().putAll(key, metadata);
        redisTemplate.expire(key, iamProperties.getJwt().getRefreshTokenExpiration(), TimeUnit.SECONDS);
    }

    public String userAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "Unknown";
        return attributes.getRequest().getHeader("User-Agent");
    }
}
