package com.pwb.backend.modules.share.constant;

public final class ShareRedisKeys {

    public static final String DAILY_RECIPIENTS_KEY_PREFIX = "share:daily_recipients:";
    public static final String DAILY_COUNT_KEY_PREFIX = "share:daily_count:";
    public static final String DOMAIN_BLACKLIST_KEY = "email:domain:blacklist";
    public static final String DISTRIBUTION_CACHE_KEY_PREFIX = "demo:distribution:";

    private ShareRedisKeys() {
    }
}