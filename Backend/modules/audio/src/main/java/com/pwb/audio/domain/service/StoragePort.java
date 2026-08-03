package com.pwb.audio.domain.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface StoragePort {

    PresignedUrl presignDownload(String storageKey, Duration expiration);

    PresignedUrl presignUpload(String storageKey, Duration expiration);

    /** @return empty when nothing was ever uploaded to that key */
    Optional<StoredObject> findMetadata(String storageKey);

    void delete(String storageKey);

    InputStream download(String storageKey);

    String uploadFromPath(String storageKey, Path sourcePath, long contentLength);

    String uploadBytes(String storageKey, byte[] content, String contentType);
}
