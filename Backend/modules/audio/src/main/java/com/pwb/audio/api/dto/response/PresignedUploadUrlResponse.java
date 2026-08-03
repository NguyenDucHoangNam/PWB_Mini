package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.PresignedUploadUrlView;

import java.net.URL;

public record PresignedUploadUrlResponse(
        String originalS3Key,
        URL uploadUrl,
        long expiresInSeconds
) {

    public static PresignedUploadUrlResponse from(PresignedUploadUrlView view) {
        return new PresignedUploadUrlResponse(
                view.originalS3Key(),
                view.uploadUrl(),
                view.expiresInSeconds()
        );
    }
}
