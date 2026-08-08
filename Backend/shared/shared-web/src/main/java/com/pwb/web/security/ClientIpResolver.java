package com.pwb.web.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, List<String> trustedProxies) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        if (isProxyTrusted(remoteAddr, trustedProxies)) {
            String forwarded = request.getHeader(X_FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    private static boolean isProxyTrusted(String remoteAddr, List<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty() || remoteAddr == null) {
            return false;
        }
        return trustedProxies.contains(remoteAddr);
    }
}