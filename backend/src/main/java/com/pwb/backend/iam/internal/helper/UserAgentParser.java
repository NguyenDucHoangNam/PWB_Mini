package com.pwb.backend.iam.internal.helper;

import com.pwb.backend.iam.internal.enums.OAuthProvider;

public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String detectOs(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) return "macOS";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("Linux")) return "Linux";
        return "Unknown";
    }

    public static String providerOf(OAuthProvider provider) {
        return provider == null ? "LOCAL" : provider.name();
    }
}
