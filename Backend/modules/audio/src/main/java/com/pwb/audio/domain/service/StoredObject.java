package com.pwb.audio.domain.service;

public record StoredObject(
        String storageKey,
        long sizeBytes,
        String contentType
) {
}
