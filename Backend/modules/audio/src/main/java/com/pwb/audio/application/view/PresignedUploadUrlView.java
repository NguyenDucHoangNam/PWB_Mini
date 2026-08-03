package com.pwb.audio.application.view;

import java.net.URL;

public record PresignedUploadUrlView(
        String originalS3Key,
        URL uploadUrl,
        long expiresInSeconds
) {
}
