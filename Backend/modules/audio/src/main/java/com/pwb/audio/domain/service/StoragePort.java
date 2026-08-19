package com.pwb.audio.domain.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface StoragePort {

    PresignedUrl presignDownload(String storageKey, Duration expiration);

    /**
     * Presigns an upload that only accepts this exact file.
     *
     * <p>Both arguments are signed into the URL, so storage rejects a body of a different size or a
     * different declared type before any of it is accepted. Size in particular can be bounded nowhere
     * else: once bytes have arrived they are already paid for, and a limit applied afterwards only
     * decides what gets registered, not what gets stored.
     */
    PresignedUrl presignUpload(String storageKey, String contentType, long contentLength, Duration expiration);

    /** @return empty when nothing was ever uploaded to that key */
    Optional<StoredObject> findMetadata(String storageKey);

    /**
     * Reads the first few bytes of a stored object so its real format can be identified.
     *
     * <p>Ranged, so identifying a 200 MB upload costs a few bytes rather than the whole file.
     */
    byte[] readHead(String storageKey, int maxBytes);

    /**
     * Moves an object within storage by copying it server-side. The bytes do not pass through this
     * process, so promoting a 200 MB upload out of the staging area costs a single API call.
     */
    void copy(String sourceKey, String destinationKey);

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
