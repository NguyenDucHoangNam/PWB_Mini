package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.UploadUrlView;

import java.net.URL;
import java.time.Instant;

/**
 * @param contentType send this back verbatim as the {@code Content-Type} of the PUT. It is part of the
 *                    URL's signature, so any other value is refused by storage.
 */
public record UploadUrlResponse(
        String storageKey,
        URL url,
        String contentType,
        Instant expiresAt
) {

    public static UploadUrlResponse from(UploadUrlView view) {
        return new UploadUrlResponse(view.storageKey(), view.url(), view.contentType(), view.expiresAt());
    }
}
