package com.pwb.backend.common.security;

import org.springframework.http.HttpMethod;

import java.time.Duration;

public record RateLimitRule(
        String pathPattern,
        HttpMethod method,
        long permitsPerWindow,
        Duration window) {
}