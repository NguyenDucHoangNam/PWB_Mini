package com.pwb.backend.modules.audio.constant;

import java.util.UUID;

public final class AudioRedisKeys {

    public static final String DEMO_KEY_CACHE_KEY_PREFIX = "demo:key:";
    public static final String DEMO_STATUS_CACHE_KEY_PREFIX = "demo:status:";

    public static String demoKeyCacheKey(UUID demoId) {
        return DEMO_KEY_CACHE_KEY_PREFIX + demoId;
    }

    public static String demoStatusCacheKey(UUID demoId) {
        return DEMO_STATUS_CACHE_KEY_PREFIX + demoId;
    }

    private AudioRedisKeys() {
    }
}
