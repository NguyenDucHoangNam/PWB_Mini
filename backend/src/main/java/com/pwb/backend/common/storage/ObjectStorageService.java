package com.pwb.backend.common.storage;

import java.time.Duration;
import java.util.Map;

public interface ObjectStorageService {

    StoredObject putObject(StorageBucket bucket, String key, byte[] data, String contentType, Map<String, String> metadata);

    StoredObject putObject(StorageBucket bucket, String key, byte[] data, String contentType);

    void deleteObject(StorageBucket bucket, String key);

    String generatePublicUrl(StorageBucket bucket, String key);

    String generatePresignedDownloadUrl(StorageBucket bucket, String key, Duration expiry);

    StoredObjectMetadata getMetadata(StorageBucket bucket, String key);

    record StoredObject(
            String bucket,
            String key,
            String publicUrl,
            long sizeBytes,
            String contentType) {
    }

    record StoredObjectMetadata(
            String bucket,
            String key,
            long sizeBytes,
            String contentType) {
    }
}
