package com.pwb.backend.filter;

import org.springframework.http.HttpMethod;

import java.time.Duration;

public record RateLimitRule(
        String pathPattern,
        HttpMethod method,
        long permitsPerWindow,
        Duration window) {
}