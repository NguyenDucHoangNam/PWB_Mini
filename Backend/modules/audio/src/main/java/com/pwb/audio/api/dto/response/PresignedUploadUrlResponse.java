package com.pwb.audio.api.dto.response;

import java.net.URL;

public record PresignedUploadUrlResponse(
        String originalS3Key,
        URL uploadUrl,
        long expiresInSeconds
) {
}
