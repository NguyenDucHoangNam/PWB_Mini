package com.pwb.backend.shared.config;

import com.pwb.backend.shared.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.equals("/favicon.ico")
            || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        log.info("ACCESS_IN method={} uri={} ip={} user={}",
            request.getMethod(),
            request.getRequestURI(),
            clientIpResolver.resolve(request),
            extractUserId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("ACCESS_OUT method={} uri={} status={} duration={}ms ip={} user={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration,
                clientIpResolver.resolve(request),
                extractUserId());
        }
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return "anonymous";
        }
        return maskPrincipal(name);
    }

    private static String maskPrincipal(String name) {
        int atIndex = name.indexOf('@');
        if (atIndex > 0 && atIndex < name.length() - 1) {
            String local = name.substring(0, atIndex);
            String domain = name.substring(atIndex + 1);
            int keep = Math.min(local.length(), 4);
            String prefix = local.substring(0, keep);
            return prefix + "***@" + domain;
        }
        return name;
    }
}