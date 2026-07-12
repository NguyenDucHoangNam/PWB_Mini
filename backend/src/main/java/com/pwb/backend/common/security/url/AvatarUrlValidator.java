package com.pwb.backend.common.security.url;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class AvatarUrlValidator {

    private AvatarUrlValidator() {
    }

    public static String sanitizeGoogleAvatarUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equals(scheme.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        List<String> allowedHosts = List.of(
                "googleusercontent.com",
                "lh3.googleusercontent.com",
                "lh4.googleusercontent.com",
                "lh5.googleusercontent.com",
                "lh6.googleusercontent.com");
        boolean matched = allowedHosts.stream().anyMatch(allowed -> hostLower.equals(allowed) || hostLower.endsWith("." + allowed));
        if (!matched) {
            return null;
        }
        return rawUrl;
    }
}
