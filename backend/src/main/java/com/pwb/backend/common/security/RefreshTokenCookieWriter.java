package com.pwb.backend.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieWriter {

    private final CookieProperties properties;

    public void writeRefreshCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        if (refreshToken == null || refreshToken.isBlank() || maxAgeSeconds <= 0) {
            return;
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(properties.isSecure())
                .path(properties.getPath())
                .maxAge(maxAgeSeconds)
                .sameSite(properties.getSamesite());
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .path(properties.getPath())
                .maxAge(0)
                .sameSite(properties.getSamesite());
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getRefreshCookieName().equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }
}
