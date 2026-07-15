package com.pwb.backend.websocket.filter;

import com.pwb.backend.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ATTR_USER_ID = "userId";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        HttpServletRequest servletRequest = toServletRequest(request);
        if (servletRequest == null) {
            return true;
        }
        String token = extractToken(servletRequest);
        if (!StringUtils.hasText(token)) {
            return true;
        }
        if (!jwtTokenProvider.validateAccessToken(token)) {
            log.debug("WebSocket handshake rejected: invalid JWT");
            return true;
        }
        try {
            UUID userId = jwtTokenProvider.extractUserId(token);
            attributes.put(ATTR_USER_ID, userId);
        } catch (Exception ex) {
            log.debug("Could not extract userId from JWT for handshake: {}", ex.getMessage());
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               @Nullable Exception exception) {
    }

    @Nullable
    private HttpServletRequest toServletRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest();
        }
        return null;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
