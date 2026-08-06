package com.pwb.infra.storage;

import com.pwb.infra.storage.dto.ObjectMetadata;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import com.pwb.infra.storage.dto.UploadResult;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface StorageService {

    UploadResult upload(String key, InputStream content, long sizeBytes, String contentType);

    UploadResult upload(String key, byte[] content, String contentType);

    /**
     * Uploads a file already on disk, in parallel parts once it is big enough.
     *
     * <p>Separate from the {@code InputStream} overload because a stream can only be read once and in
     * order, so it can never be split — a caller that has a real file gets a materially faster transfer,
     * and the type is what makes that possible rather than an option to pass.
     */
    UploadResult uploadFile(String key, Path source, String contentType);

    InputStream download(String key);

    /**
     * Downloads straight to disk, in parallel ranged parts once it is big enough. Prefer this over
     * {@link #download(String)} plus a copy whenever the destination is a file: the stream version is one
     * sequential transfer.
     *
     * @return bytes written
     */
    long downloadToFile(String key, Path destination);

    void delete(String key);

    void deleteAll(List<String> keys);

    boolean exists(String key);

    ObjectMetadata getMetadata(String key);

    PresignedUrlResult generatePresignedUrl(String key, Duration expiration);

    PresignedUrlResult generatePresignedUploadUrl(String key, String contentType, Duration expiration);
}
