package com.pwb.backend.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class ObjectStorageProperties {

    private boolean enabled;

    private String provider;

    private String endpoint;

    private String region;

    private String accessKey;

    private String secretKey;

    private boolean pathStyleAccessEnabled;

    private String publicBaseUrl;

    private long presignedUrlExpirySeconds;

    @NestedConfigurationProperty
    private Map<String, String> buckets = new HashMap<>();

    public String bucketFor(StorageBucket bucket) {
        String configured = buckets.get(bucket.getPropertyKey());
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return switch (bucket) {
            case AVATAR -> "pwb-avatars";
            case DEMO_AUDIO -> "pwb-demo-audio";
            case COVER_IMAGE -> "pwb-covers";
            case GENERIC -> "pwb-misc";
            case VOICE_TAG -> "pwb-voice-tags";
        };
    }
}
