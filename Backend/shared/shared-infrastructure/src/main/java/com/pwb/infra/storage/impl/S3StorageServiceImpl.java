package com.pwb.infra.storage.impl;

import com.pwb.infra.storage.exception.StorageErrorCode;
import com.pwb.infra.storage.exception.StorageException;
import com.pwb.infra.storage.properties.StorageProperties;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.ObjectMetadata;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import com.pwb.infra.storage.dto.UploadResult;
import com.pwb.infra.storage.util.MediaTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;
    private final S3TransferManager transferManager;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    /**
     * Deliberately <em>not</em> {@code @Retryable}, unlike every other method here. A stream is consumed
     * by the first attempt and cannot be rewound, so a second attempt would read from EOF and fail
     * against the {@code contentLength} already declared — replacing the real S3 error with a confusing
     * length mismatch and never succeeding. Callers that need retries should hand over a {@code byte[]}
     * or a {@link Path}, both of which can be re-read.
     */
    @Override
    public UploadResult upload(String key, InputStream content, long sizeBytes, String contentType) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        if (sizeBytes > properties.getS3().getMultipartUploadThresholdBytes()) {
            return uploadMultipart(bucket, key, content, sizeBytes, contentType);
        }
        return uploadSimple(bucket, key, content, sizeBytes, contentType);
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public UploadResult upload(String key, byte[] content, String contentType) {
        MediaTypeUtils.validateKey(key);
        return upload(key, new ByteArrayInputStream(content), content.length, contentType);
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public UploadResult uploadFile(String key, Path source, String contentType) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            long sizeBytes = Files.size(source);
            UploadFileRequest request = UploadFileRequest.builder()
                    .source(source)
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build())
                    .build();

            CompletedFileUpload result = transferManager.uploadFile(request).completionFuture().join();
            return new UploadResult(key, sizeBytes, contentType, result.response().eTag());
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        } catch (RuntimeException e) {
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, unwrap(e));
        }
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public long downloadToFile(String key, Path destination) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            DownloadFileRequest request = DownloadFileRequest.builder()
                    // A retry would otherwise fail on the file the previous attempt left behind.
                    .destination(destination)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build())
                    .build();

            transferManager.downloadFile(request).completionFuture().join();
            return Files.size(destination);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        } catch (RuntimeException e) {
            Throwable cause = unwrap(e);
            if (cause instanceof NoSuchKeyException
                    || (cause instanceof S3Exception s3 && s3.statusCode() == 404)) {
                throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, cause);
            }
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, cause);
        }
    }

    /** The transfer manager reports failures wrapped in the future's {@link CompletionException}. */
    private Throwable unwrap(RuntimeException ex) {
        return ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public InputStream download(String key) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
            }
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public byte[] readHead(String key, int maxBytes) {
        MediaTypeUtils.validateKey(key);
        if (maxBytes <= 0) {
            return new byte[0];
        }
        String bucket = bucket();

        try (InputStream stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                // Inclusive on both ends, so the last byte wanted is maxBytes - 1.
                .range("bytes=0-" + (maxBytes - 1))
                .build())) {
            return stream.readNBytes(maxBytes);
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
            }
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public void copy(String sourceKey, String destinationKey) {
        MediaTypeUtils.validateKey(sourceKey);
        MediaTypeUtils.validateKey(destinationKey);
        String bucket = bucket();

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(destinationKey)
                    // Default MetadataDirective is COPY, which carries the source's content type over —
                    // that content type was pinned by the upload signature, so it is worth keeping.
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
            }
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        }
    }

    @Override
    @Retryable(
            retryFor = {S3Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
    public void delete(String key) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_DELETE_FAILED, e);
        }
    }

    @Override
    public void deleteAll(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.forEach(MediaTypeUtils::validateKey);
        String bucket = bucket();

        try {
            List<ObjectIdentifier> identifiers = keys.stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();

            DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(builder -> builder.objects(identifiers))
                    .build());

            if (response.hasErrors()) {
                log.warn("Some objects failed to delete: {}", response.errors());
                throw new StorageException(StorageErrorCode.STORAGE_DELETE_FAILED);
            }
        } catch (S3Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_DELETE_FAILED, e);
        }
    }

    @Override
    public boolean exists(String key) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    public ObjectMetadata getMetadata(String key) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return new ObjectMetadata(
                    key,
                    response.contentLength(),
                    response.contentType(),
                    response.lastModified()
            );
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
            }
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    /**
     * The response is forced to {@code Content-Disposition: attachment}.
     *
     * <p>An object's stored content type is decided by whoever uploaded it, so without this a file that
     * claims to be {@code text/html} is rendered — and its script executed — on the bucket's own origin
     * when the URL is opened. {@code <audio>} and {@code <img>} ignore the header entirely, so playback
     * and avatars are unaffected; only a direct navigation changes, and a direct navigation is exactly
     * the case being closed.
     */
    @Override
    public PresignedUrlResult generatePresignedUrl(String key, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .responseContentDisposition("attachment")
                            .build())
                    .build();

            PresignedGetObjectRequest presigned = presigner.presignGetObject(request);
            return new PresignedUrlResult(presigned.url(), presigned.expiration());
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }

    @Override
    public PresignedUrlResult generatePresignedUploadUrl(
            String key, String contentType, long contentLength, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try {
            // No serverSideEncryption here on purpose, unlike the server-side uploads below: it would
            // become a signed header the browser has to reproduce exactly, and a mismatch surfaces as an
            // opaque 403. Encryption at rest for this path comes from the bucket's default encryption
            // setting instead — see docs/storage/bucket-configuration.md.
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);
            if (contentType != null && !contentType.isBlank()) {
                put.contentType(contentType);
            }
            if (contentLength > 0) {
                put.contentLength(contentLength);
            }

            PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(put.build())
                    .build();

            PresignedPutObjectRequest presigned = presigner.presignPutObject(request);
            return new PresignedUrlResult(presigned.url(), presigned.expiration());
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }

    private UploadResult uploadMultipart(String bucket, String key, InputStream content, long sizeBytes, String contentType) {
        try {
            UploadRequest request = UploadRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(sizeBytes)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build())
                    .requestBody(AsyncRequestBody.fromInputStream(content, sizeBytes, null))
                    .build();

            CompletedUpload result = transferManager.upload(request).completionFuture().join();
            return new UploadResult(
                    key,
                    sizeBytes,
                    contentType,
                    result.response().eTag()
            );
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof S3Exception) {
                throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, cause);
            }
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        }
    }

    private UploadResult uploadSimple(String bucket, String key, InputStream content, long sizeBytes, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(sizeBytes)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromInputStream(content, sizeBytes));
            return new UploadResult(
                    key,
                    sizeBytes,
                    contentType,
                    response.eTag()
            );
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND, e);
        } catch (S3Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        }
    }

    private String bucket() {
        String bucket = properties.getS3().getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        return bucket;
    }
}
