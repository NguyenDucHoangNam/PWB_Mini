package com.pwb.backend.iam.internal.helper;

public class UserAgentParser {

    private UserAgentParser() {
    }

    public static String detectOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);

        if (ua.contains("windows nt")) return "Windows";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("mac os x") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("cros") || ua.contains("chromium")) return "ChromeOS";
        if (ua.contains("linux")) return "Linux";
        if (ua.contains("freebsd")) return "FreeBSD";
        if (ua.contains("openbsd")) return "OpenBSD";
        return "Unknown";
    }

    public static String detectBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);

        if (ua.contains("edg/") || ua.contains("edge/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("firefox/") || ua.contains("fxios")) return "Firefox";
        if (ua.contains("samsungbrowser")) return "Samsung Browser";
        if (ua.contains("headlesschrome") || ua.contains("phantomjs") || ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) return "Headless/Bot";
        if (ua.contains("chrome/") || ua.contains("crios/")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";
        if (ua.contains("curl/") || ua.contains("wget/")) return "curl/wget";
        return "Unknown";
    }

    public static String providerOf(com.pwb.backend.iam.internal.enums.OAuthProvider provider) {
        return provider == null ? "LOCAL" : provider.name();
    }
}