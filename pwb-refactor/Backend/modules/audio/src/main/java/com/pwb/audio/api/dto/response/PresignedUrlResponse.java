package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.PresignedUrlView;

import java.net.URL;
import java.util.UUID;

public record PresignedUrlResponse(
        UUID resourceId,
        URL url,
        long expiresInSeconds
) {

    public static PresignedUrlResponse from(PresignedUrlView view) {
        return new PresignedUrlResponse(
                view.resourceId(),
                view.url(),
                view.expiresInSeconds()
        );
    }
}
