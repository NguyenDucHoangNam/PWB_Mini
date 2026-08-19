package com.pwb.audio.application.view;

import java.net.URL;
import java.time.Instant;

/**
 * @param contentType the value signed into {@code url}. The client must send exactly this as its
 *                    {@code Content-Type} header, and exactly the size it declared as its body, or
 *                    storage rejects the upload — that is what stops an upload being labelled anything
 *                    the client likes, or being larger than the limit allows.
 */
public record UploadUrlView(
        String storageKey,
        URL url,
        String contentType,
        Instant expiresAt
) {
}
