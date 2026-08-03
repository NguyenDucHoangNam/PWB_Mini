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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;
    private final S3TransferManager transferManager;
    private final StorageProperties properties;

    @Override
    @Retryable(
            retryFor = {S3Exception.class, IOException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 3, maxDelay = 10000)
    )
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

    @Override
    public PresignedUrlResult generatePresignedUrl(String key, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try (S3Presigner presigner = S3Presigner.builder()
                .region(s3Client.serviceClientConfiguration().region())
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build()) {

            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build())
                    .build();

            PresignedGetObjectRequest presigned = presigner.presignGetObject(request);
            URL url = presigned.url();
            return new PresignedUrlResult(url, Instant.now().plus(expiration));
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }

    @Override
    public PresignedUrlResult generatePresignedUploadUrl(String key, String contentType, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        String bucket = bucket();

        try (S3Presigner presigner = S3Presigner.builder()
                .region(s3Client.serviceClientConfiguration().region())
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build()) {

            PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build())
                    .build();

            PresignedPutObjectRequest presigned = presigner.presignPutObject(request);
            URL url = presigned.url();
            return new PresignedUrlResult(url, Instant.now().plus(expiration));
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
