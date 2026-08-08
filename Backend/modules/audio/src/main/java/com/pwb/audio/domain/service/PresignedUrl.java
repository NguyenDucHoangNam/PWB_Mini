package com.pwb.audio.domain.service;

import java.net.URL;
import java.time.Instant;

public record PresignedUrl(
        URL url,
        Instant expiresAt
) {
}
