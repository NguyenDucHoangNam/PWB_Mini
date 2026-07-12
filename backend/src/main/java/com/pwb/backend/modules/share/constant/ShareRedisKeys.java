package com.pwb.backend.modules.share.constant;

public final class ShareRedisKeys {

    public static final String DAILY_RECIPIENTS_KEY_PREFIX = "share:daily_recipients:";
    public static final String DAILY_COUNT_KEY_PREFIX = "share:daily_count:";
    public static final String DOMAIN_BLACKLIST_KEY = "email:domain:blacklist";
    public static final String DISTRIBUTION_CACHE_KEY_PREFIX = "demo:distribution:";
    public static final String DISTRIBUTION_REVOKED_KEY_PREFIX = "demo:distribution:revoked:";
    public static final String DISTRIBUTION_LOCKED_KEY_PREFIX = "demo:distribution:locked:";

    public static String distributionCacheKey(java.util.UUID shareToken) {
        return DISTRIBUTION_CACHE_KEY_PREFIX + shareToken;
    }

    public static String distributionRevokedKey(java.util.UUID shareToken) {
        return DISTRIBUTION_REVOKED_KEY_PREFIX + shareToken;
    }

    public static String distributionLockedKey(java.util.UUID shareToken) {
        return DISTRIBUTION_LOCKED_KEY_PREFIX + shareToken;
    }

    private ShareRedisKeys() {
    }
}