package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.UploadUrlView;

import java.net.URL;
import java.time.Instant;

public record UploadUrlResponse(
        String storageKey,
        URL url,
        Instant expiresAt
) {

    public static UploadUrlResponse from(UploadUrlView view) {
        return new UploadUrlResponse(view.storageKey(), view.url(), view.expiresAt());
    }
}
