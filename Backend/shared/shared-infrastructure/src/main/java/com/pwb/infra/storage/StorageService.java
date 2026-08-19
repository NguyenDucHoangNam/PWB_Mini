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
     * Reads at most the first {@code maxBytes} of an object.
     *
     * <p>Exists so a caller can identify what was really uploaded without paying for the whole transfer:
     * a magic-byte check needs a dozen bytes, and pulling a 200 MB song to look at them is the reason
     * that check tends not to get written at all. Issued as a ranged {@code GET}, so the bucket sends
     * only the requested prefix.
     *
     * @return the bytes read, shorter than {@code maxBytes} when the object itself is
     */
    byte[] readHead(String key, int maxBytes);

    /**
     * Downloads straight to disk, in parallel ranged parts once it is big enough. Prefer this over
     * {@link #download(String)} plus a copy whenever the destination is a file: the stream version is one
     * sequential transfer.
     *
     * @return bytes written
     */
    long downloadToFile(String key, Path destination);

    /**
     * Copies an object within the bucket, server-side — the bytes never travel through this process.
     *
     * <p>Overwrites the destination if it exists, and preserves the source's content type. Single-request,
     * so it is bounded by S3's 5 GB copy limit; everything this system stores is far below that.
     */
    void copy(String sourceKey, String destinationKey);

    void delete(String key);

    void deleteAll(List<String> keys);

    boolean exists(String key);

    ObjectMetadata getMetadata(String key);

    PresignedUrlResult generatePresignedUrl(String key, Duration expiration);

    /**
     * Presigns a {@code PUT} that only accepts the exact upload it was issued for.
     *
     * <p>Both {@code contentType} and {@code contentLength} become part of the signature — verified:
     * the resulting URL carries {@code X-Amz-SignedHeaders=content-length;content-type;host} — so S3
     * itself rejects a body of a different size or a different declared type. Passing {@code null} or a
     * non-positive length leaves that half unsigned, which is the same as letting the client choose.
     *
     * <p>This is the only point at which an upload's size can be bounded at all. Once bytes have been
     * accepted the transfer is already paid for, so a limit checked afterwards limits what gets
     * registered, not what gets stored.
     */
    PresignedUrlResult generatePresignedUploadUrl(String key, String contentType, long contentLength, Duration expiration);
}
