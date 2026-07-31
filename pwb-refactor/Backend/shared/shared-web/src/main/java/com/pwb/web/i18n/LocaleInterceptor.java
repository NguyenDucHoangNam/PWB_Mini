package com.pwb.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class LocaleInterceptor implements HandlerInterceptor {

    private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    private static final Set<String> SUPPORTED_LOCALES = Set.of("vi", "en");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String acceptLanguage = request.getHeader(ACCEPT_LANGUAGE_HEADER);
        Locale locale = parseLocale(acceptLanguage);
        LocaleContextHolder.setLocale(locale);
        log.debug("Locale set to: {} from Accept-Language: {}", locale, acceptLanguage);
        return true;
    }

    private Locale parseLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.forLanguageTag("vi");
        }
        String primaryTag = acceptLanguage.split(",")[0].trim();
        String language = primaryTag.split("[-_;]")[0].trim();
        if (language.isBlank()) {
            return Locale.forLanguageTag("vi");
        }
        if (SUPPORTED_LOCALES.contains(language)) {
            return Locale.forLanguageTag(language);
        }
        return Locale.forLanguageTag("vi");
    }
}
