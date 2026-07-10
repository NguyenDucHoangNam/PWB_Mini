package com.pwb.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

public final class CsrfSupport {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private CsrfSupport() {
    }

    public static CookieCsrfTokenRepository cookieTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieName(CSRF_COOKIE_NAME);
        repo.setCookieCustomizer(c -> c.path("/").sameSite("Lax"));
        return repo;
    }

    public static CsrfTokenRequestAttributeHandler requestAttributeHandler() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    public static boolean isCookieAuthenticated(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        boolean hasBearer = auth != null && !auth.isBlank() && auth.startsWith("Bearer ");
        return !hasBearer && hasRefreshCookie(request);
    }

    private static boolean hasRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if ("refreshToken".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean responseHasCsrfCookie(HttpServletResponse response) {
        return response.getHeaders("Set-Cookie") != null
            && response.getHeaders("Set-Cookie").stream()
                .anyMatch(v -> v != null && v.startsWith(CSRF_COOKIE_NAME + "="));
    }
}