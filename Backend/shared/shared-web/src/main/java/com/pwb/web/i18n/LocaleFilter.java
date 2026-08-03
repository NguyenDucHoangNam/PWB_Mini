package com.pwb.web.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocaleFilter extends OncePerRequestFilter {

    private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    private static final Set<String> SUPPORTED_LOCALES = Set.of("vi", "en");
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String acceptLanguage = request.getHeader(ACCEPT_LANGUAGE_HEADER);
        Locale locale = parseLocale(acceptLanguage);
        LocaleContextHolder.setLocale(locale);
        log.debug("Locale set to: {} from Accept-Language: {}", locale, acceptLanguage);
        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private Locale parseLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String primaryTag = acceptLanguage.split(",")[0].trim();
        String language = primaryTag.split("[-_;]")[0].trim();
        if (language.isBlank()) {
            return DEFAULT_LOCALE;
        }
        if (SUPPORTED_LOCALES.contains(language)) {
            return Locale.forLanguageTag(language);
        }
        return DEFAULT_LOCALE;
    }
}
