package com.pwb.backend.audio.internal.api;

public record VoiceTagPreviewResponse(
    String preSignedUrl,
    int expiresInSeconds
) {}
