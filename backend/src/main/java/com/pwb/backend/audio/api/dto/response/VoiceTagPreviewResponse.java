package com.pwb.backend.audio.api.dto.response;

public record VoiceTagPreviewResponse(
    String preSignedUrl,
    int expiresInSeconds
) {}
