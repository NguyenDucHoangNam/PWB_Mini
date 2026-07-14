package com.pwb.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.exception.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSandboxFilter extends OncePerRequestFilter {

    private static final String ROLE_LISTENER = "ROLE_LISTENER";
    private static final String ROLE_PRO = "ROLE_PRO";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String[] LISTENER_ALLOWED_PREFIXES = {
            "/api/v1/rooms/",
            "/ws"
    };

    private static final String[] LISTENER_FORBIDDEN_SUBPATHS = {
            "/waiting/approve",
            "/waiting/reject",
            "/waiting/kick"
    };

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AbstractAuthenticationToken token) {
            String role = extractRole(token);
            if (ROLE_LISTENER.equals(role) && !isListenerAllowed(request.getRequestURI(), request.getMethod())) {
                writeSandboxForbidden(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isListenerAllowed(String uri, String method) {
        if (uri == null) {
            return false;
        }
        if (uri.startsWith("/ws")) {
            return true;
        }
        if (!uri.startsWith("/api/v1/rooms/")) {
            return false;
        }
        String suffix = uri.substring("/api/v1/rooms/".length());
        if ("POST".equalsIgnoreCase(method)) {
            if (suffix.endsWith("/join")) {
                return true;
            }
            for (String forbidden : LISTENER_FORBIDDEN_SUBPATHS) {
                if (suffix.endsWith(forbidden)) {
                    return false;
                }
            }
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        return false;
    }

    private String extractRole(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority() == null ? "" : a.getAuthority().toUpperCase(Locale.ROOT))
                .orElse("");
    }

    private void writeSandboxForbidden(HttpServletResponse response) throws IOException {
        log.warn("LISTENER_SANDBOX_BLOCKED uri={}", "masked");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail detail = new ErrorDetail(
                "FORBIDDEN_LISTENER_SANDBOX",
                null,
                "Listener token is not allowed to access this endpoint");
        ApiResponse<Void> body = ApiResponse.error(
                "Listener sandbox violation", List.of(detail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @SuppressWarnings("unused")
    private static UUID randomTrace() {
        return UUID.randomUUID();
    }
}