package com.pwb.backend.service;

import com.pwb.backend.config.StorageKey;
import com.pwb.backend.enums.MediaType;

import java.time.Duration;

public interface StorageService {

    StorageKey upload(MediaType type, byte[] content, String extension);

    String generatePresignedGetUrl(StorageKey key, Duration ttl);

    String generatePresignedGetUrl(String s3Key, Duration ttl);

    void delete(StorageKey key);

    void deleteByPath(String s3Key);
}
