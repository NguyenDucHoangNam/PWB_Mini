package com.pwb.iam.domain.model;

import java.time.Duration;
import java.util.Set;

public record AvatarPolicy(
        long maxSizeBytes,
        Set<String> allowedContentTypes,
        Duration urlTtl
) {

    public AvatarPolicy {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive");
        }
        allowedContentTypes = allowedContentTypes == null ? Set.of() : Set.copyOf(allowedContentTypes);
        if (urlTtl == null || urlTtl.isZero() || urlTtl.isNegative()) {
            throw new IllegalArgumentException("urlTtl must be positive");
        }
    }

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType);
    }
}
