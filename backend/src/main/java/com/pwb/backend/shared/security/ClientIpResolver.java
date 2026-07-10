package com.pwb.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "Unknown";
    private static final String LOOPBACK_IPV4_PREFIX = "127.";
    private static final String LOOPBACK_IPV6_SHORT = "::1";
    private static final String LOOPBACK_IPV6_FULL = "0:0:0:0:0:0:0:1";
    private static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    private final List<String> trustedProxies;

    public ClientIpResolver(
        @Value("${app.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxiesCsv
    ) {
        this.trustedProxies = parseTrustedProxies(trustedProxiesCsv);
    }

    public String current() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return UNKNOWN;
        }
        return resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (isUsableForwardedHeader(forwarded)) {
                return extractFirstForwardedAddress(forwarded);
            }
        }
        return isBlank(remoteAddr) ? UNKNOWN : remoteAddr;
    }

    private List<String> parseTrustedProxies(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of("127.0.0.1", "::1", LOOPBACK_IPV6_FULL);
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private boolean isUsableForwardedHeader(String forwarded) {
        return forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded);
    }

    private String extractFirstForwardedAddress(String forwarded) {
        int comma = forwarded.indexOf(',');
        String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
        String candidate = first.trim();
        return candidate.isEmpty() ? null : candidate;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (isBlank(remoteAddr)) {
            return false;
        }
        if (trustedProxies.contains(remoteAddr)) {
            return true;
        }
        if (remoteAddr.startsWith(LOOPBACK_IPV4_PREFIX)) {
            return true;
        }
        return LOOPBACK_IPV6_SHORT.equals(remoteAddr) || LOOPBACK_IPV6_FULL.equals(remoteAddr);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
