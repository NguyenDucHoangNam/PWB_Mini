package com.pwb.backend.audio.internal.api;

public record DownloadResponse(
    String downloadUrl,
    int ttlSeconds
) {}