package com.pwb.backend.iam.internal.helper;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.service.GeoIpService;
import com.pwb.backend.iam.internal.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class SessionMetadataBuilder {

    private SessionMetadataBuilder() {
    }

    public static Map<String, String> buildForNewSession(User user, JwtService jwtService, GeoIpService geoIpService,
                                                          String accessToken) {
        String ip = clientIp();
        String ua = userAgent();
        String browser = ua != null ? ua : "Unknown";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("ip", ip);
        metadata.put("browser", browser);
        metadata.put("os", UserAgentParser.detectOs(ua));
        metadata.put("location", geoIpService.getLocation(ip));
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("active_jwt_signature", jwtService.getSignature(accessToken));
        return metadata;
    }

    public static void store(StringRedisTemplate redisTemplate, String refreshToken, Map<String, String> metadata,
                             IamProperties iamProperties) {
        String key = "session:metadata:" + refreshToken;
        redisTemplate.opsForHash().putAll(key, metadata);
        redisTemplate.expire(key, iamProperties.getJwt().getRefreshTokenExpiration(), TimeUnit.SECONDS);
    }

    public static String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "Unknown";
        HttpServletRequest req = attributes.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    public static String userAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "Unknown";
        return attributes.getRequest().getHeader("User-Agent");
    }
}
