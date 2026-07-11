package com.pwb.backend.audio.api.dto.response;

public record DownloadResponse(
    String downloadUrl,
    int ttlSeconds
) {}