package com.pwb.web.http;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class CookieUtils {

    private CookieUtils() {
    }

    public static void addRefreshTokenCookie(
            HttpServletResponse response,
            String name,
            String token,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite,
            int maxAgeSeconds
    ) {
        write(response, name, token, path, httpOnly, secure, sameSite, maxAgeSeconds);
    }

    public static void clearRefreshTokenCookie(
            HttpServletResponse response,
            String name,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite
    ) {
        write(response, name, "", path, httpOnly, secure, sameSite, 0);
    }

    private static void write(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite,
            int maxAgeSeconds
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path(path)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
