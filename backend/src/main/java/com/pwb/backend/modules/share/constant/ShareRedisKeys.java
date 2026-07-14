package com.pwb.backend.modules.share.constant;

import java.util.UUID;

public final class ShareRedisKeys {

    public static final String DAILY_RECIPIENTS_KEY_PREFIX = "share:daily_recipients:";
    public static final String DAILY_COUNT_KEY_PREFIX = "share:daily_count:";
    public static final String DOMAIN_BLACKLIST_KEY = "email:domain:blacklist";
    public static final String DISTRIBUTION_CACHE_KEY_PREFIX = "demo:distribution:";
    public static final String DISTRIBUTION_REVOKED_KEY_PREFIX = "demo:distribution:revoked:";
    public static final String DISTRIBUTION_LOCKED_KEY_PREFIX = "demo:distribution:locked:";
    public static final String COOKIE_ACTIVE_SESSIONS_KEY_PREFIX = "demo:distribution:active_sessions:";
    public static final String COOKIE_REVOKED_KEY_PREFIX = "stream:cookie:revoked:";
    public static final String PLAY_SESSION_KEY_PREFIX = "play_session:";
    public static final String WS_HEARTBEAT_KEY_PREFIX = "ws_heartbeat:";
    public static final String KEYS_REQUEST_COUNT_KEY_PREFIX = "keys_request_count:";

    public static String distributionCacheKey(UUID shareToken) {
        return DISTRIBUTION_CACHE_KEY_PREFIX + shareToken;
    }

    public static String distributionRevokedKey(UUID shareToken) {
        return DISTRIBUTION_REVOKED_KEY_PREFIX + shareToken;
    }

    public static String distributionLockedKey(UUID shareToken) {
        return DISTRIBUTION_LOCKED_KEY_PREFIX + shareToken;
    }

    public static String activeCookieSessionSetKey(UUID shareToken) {
        return COOKIE_ACTIVE_SESSIONS_KEY_PREFIX + shareToken;
    }

    public static String cookieRevokedKey(String jti) {
        return COOKIE_REVOKED_KEY_PREFIX + jti;
    }

    public static String playSessionKey(UUID shareToken, String sessionId) {
        return PLAY_SESSION_KEY_PREFIX + shareToken + ":" + sessionId;
    }

    public static String wsHeartbeatKey(String sessionId) {
        return WS_HEARTBEAT_KEY_PREFIX + sessionId;
    }

    public static String keysRequestCountKey(String sessionId) {
        return KEYS_REQUEST_COUNT_KEY_PREFIX + sessionId;
    }

    private ShareRedisKeys() {
    }
}
