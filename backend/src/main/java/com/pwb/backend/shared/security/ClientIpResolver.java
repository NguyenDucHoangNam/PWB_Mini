package com.pwb.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Single source of truth for resolving the client IP from an HTTP request.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If the request's remote address is on the configured trusted-proxy
 *       allowlist, the first entry of {@code X-Forwarded-For} is used.</li>
 *   <li>Otherwise, {@code X-Forwarded-For} is ignored and {@code remoteAddr}
 *       is returned, preventing spoofing via injected headers.</li>
 * </ul>
 *
 * <p>This is critical for the rate-limit and session-metadata paths: trusting
 * an unvalidated {@code X-Forwarded-For} would let an attacker rotate the IP
 * per request and bypass per-IP rate limits.
 */
@Component
public class ClientIpResolver {

    private final List<String> trustedProxies;

    public ClientIpResolver(
        @Value("${app.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesCsv
    ) {
        if (trustedProxiesCsv == null || trustedProxiesCsv.isBlank()) {
            this.trustedProxies = List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
        } else {
            this.trustedProxies = Arrays.stream(trustedProxiesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        }
    }

    /**
     * Returns the client IP for the current request, or {@code "Unknown"} when
     * invoked outside an HTTP scope.
     */
    public String current() {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "Unknown";
        }
        return resolve(attributes.getRequest());
    }

    /**
     * Returns the client IP for the given request. See class-level docs for
     * the trust semantics.
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
                int comma = forwarded.indexOf(',');
                String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
                String candidate = first.trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return remoteAddr == null || remoteAddr.isBlank() ? "Unknown" : remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        if (trustedProxies.contains(remoteAddr)) {
            return true;
        }
        // Allow loopback ranges (127.0.0.0/8) and IPv6 loopback.
        if (remoteAddr.startsWith("127.")) {
            return true;
        }
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return true;
        }
        return false;
    }
}