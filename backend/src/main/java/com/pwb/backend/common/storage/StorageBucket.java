package com.pwb.backend.common.storage;

import lombok.Getter;

@Getter
public enum StorageBucket {

    AVATAR("avatar", "avatars/"),
    DEMO_AUDIO("demo-audio", "demos/audio/"),
    COVER_IMAGE("cover-image", "covers/"),
    GENERIC("generic", "misc/"),
    VOICE_TAG("voice-tag", "voicetags/");

    private final String propertyKey;
    private final String keyPrefix;

    StorageBucket(String propertyKey, String keyPrefix) {
        this.propertyKey = propertyKey;
        this.keyPrefix = keyPrefix;
    }
}
