package com.pwb.audio.application.view;

import java.net.URL;
import java.util.UUID;

public record PresignedUrlView(
        UUID resourceId,
        URL url,
        long expiresInSeconds
) {
}
