package com.pwb.web.http;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

public final class CookieUtils {

    private CookieUtils() {
    }

    public static void addRefreshTokenCookie(
            HttpServletResponse response,
            String token,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite,
            int maxAgeSeconds
    ) {
        ResponseCookie cookie = ResponseCookie.from("pwb_refresh_token", token)
                .path(path)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public static void clearRefreshTokenCookie(
            HttpServletResponse response,
            String path
    ) {
        ResponseCookie cookie = ResponseCookie.from("pwb_refresh_token", "")
                .path(path)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
