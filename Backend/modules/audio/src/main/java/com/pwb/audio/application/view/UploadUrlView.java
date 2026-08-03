package com.pwb.audio.application.view;

import java.net.URL;
import java.time.Instant;

public record UploadUrlView(
        String storageKey,
        URL url,
        Instant expiresAt
) {
}
