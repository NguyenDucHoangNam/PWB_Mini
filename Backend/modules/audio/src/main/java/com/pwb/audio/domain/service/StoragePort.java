package com.pwb.audio.domain.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface StoragePort {

    PresignedUrl presignDownload(String storageKey, Duration expiration);

    PresignedUrl presignUpload(String storageKey, Duration expiration);

    /** @return empty when nothing was ever uploaded to that key */
    Optional<StoredObject> findMetadata(String storageKey);

    void delete(String storageKey);

    /**
     * Fetches straight to disk in parallel parts. The merge pipeline pulls whole multi-MB songs across
     * whatever distance separates this process from the bucket, and a single stream spends that wait
     * mostly idle.
     *
     * <p>There is deliberately no {@code InputStream} variant: everything here writes what it fetches to
     * a file for FFmpeg to open, and a stream is the one shape that cannot be fetched in parallel.
     */
    void downloadToPath(String storageKey, Path destination);

    String uploadFromPath(String storageKey, Path sourcePath, long contentLength);

    String uploadBytes(String storageKey, byte[] content, String contentType);
}
