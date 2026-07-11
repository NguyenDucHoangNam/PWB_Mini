package com.pwb.backend.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpClientContextResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_IP = "unknown";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String UNKNOWN_DEVICE = "Unknown";

    public String resolveIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            String first = commaIndex >= 0 ? forwarded.substring(0, commaIndex) : forwarded;
            return first.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null && !remote.isBlank() ? remote : UNKNOWN_IP;
    }

    public String resolveUserAgent(jakarta.servlet.http.HttpServletRequest request) {
        String agent = request.getHeader(USER_AGENT_HEADER);
        return agent == null || agent.isBlank() ? UNKNOWN_DEVICE : agent;
    }
}
