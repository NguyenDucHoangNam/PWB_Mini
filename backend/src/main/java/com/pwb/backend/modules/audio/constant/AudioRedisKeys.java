package com.pwb.backend.modules.audio.constant;

public final class AudioRedisKeys {

    public static final String DEMO_KEY_CACHE_KEY_PREFIX = "demo:key:";
    public static final String DEMO_STATUS_CACHE_KEY_PREFIX = "demo:status:";

    public static String demoKeyCacheKey(java.util.UUID demoId) {
        return DEMO_KEY_CACHE_KEY_PREFIX + demoId;
    }

    public static String demoStatusCacheKey(java.util.UUID demoId) {
        return DEMO_STATUS_CACHE_KEY_PREFIX + demoId;
    }

    private AudioRedisKeys() {
    }
}
