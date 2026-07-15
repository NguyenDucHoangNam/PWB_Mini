package com.pwb.backend.security;

import com.pwb.backend.security.config.CookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Configuration
@EnableConfigurationProperties(CookieProperties.class)
@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private static final String REFRESH_COOKIE_PATH = "/";

    private final CookieProperties cookieProperties;

    public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
                cookieProperties.getRefreshName(),
                refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieProperties.isSecure());
        cookie.setPath(cookieProperties.getPath() == null ? REFRESH_COOKIE_PATH : cookieProperties.getPath());
        cookie.setMaxAge(cookieProperties.getMaxAgeSeconds());
        cookie.setAttribute("SameSite", cookieProperties.getSameSite());
        response.addCookie(cookie);
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
                cookieProperties.getRefreshName(),
                "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieProperties.isSecure());
        cookie.setPath(cookieProperties.getPath() == null ? REFRESH_COOKIE_PATH : cookieProperties.getPath());
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", cookieProperties.getSameSite());
        response.addCookie(cookie);
    }
}
