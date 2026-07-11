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

    private boolean enabled = true;

    private String provider = "s3";

    private String endpoint;

    private String region = "us-east-1";

    private String accessKey;

    private String secretKey;

    private boolean pathStyleAccessEnabled = true;

    private String publicBaseUrl;

    private long presignedUrlExpirySeconds = 900;

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
        };
    }
}
