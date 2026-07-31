package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.PresignedUrlView;

import java.net.URL;
import java.time.Instant;

public record AudioUrlResponse(
        URL url,
        Instant expiresAt
) {

    public static AudioUrlResponse from(PresignedUrlView view) {
        Instant expiresAt = Instant.now().plusSeconds(view.expiresInSeconds());
        return new AudioUrlResponse(view.url(), expiresAt);
    }
}
