package com.pwb.iam.infrastructure.security;

import com.pwb.iam.infrastructure.security.config.CookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private static final String REFRESH_COOKIE_PATH = "/";

    private final CookieProperties cookieProperties;

    public void setRefreshCookie(jakarta.servlet.http.HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = buildResponseCookie(refreshToken, cookieProperties.getMaxAgeSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(jakarta.servlet.http.HttpServletResponse response) {
        ResponseCookie cookie = buildResponseCookie("", 0);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildResponseCookie(String value, int maxAge) {
        String path = cookieProperties.getPath() == null ? REFRESH_COOKIE_PATH : cookieProperties.getPath();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(cookieProperties.getRefreshName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(path)
                .maxAge(maxAge)
                .sameSite(cookieProperties.getSameSite());
        return builder.build();
    }
}
