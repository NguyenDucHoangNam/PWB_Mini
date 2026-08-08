package com.pwb.iam.api.support;

import java.util.Set;

public final class RequestLocale {

    public static final String DEFAULT = "vi";

    private static final Set<String> SUPPORTED = Set.of("vi", "en");

    private RequestLocale() {
    }

    public static String from(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT;
        }
        String primary = acceptLanguage.split(",")[0].trim();
        int separator = primary.indexOf('-');
        if (separator > 0) {
            primary = primary.substring(0, separator);
        }
        int quality = primary.indexOf(';');
        if (quality > 0) {
            primary = primary.substring(0, quality);
        }
        primary = primary.toLowerCase();
        return SUPPORTED.contains(primary) ? primary : DEFAULT;
    }
}
