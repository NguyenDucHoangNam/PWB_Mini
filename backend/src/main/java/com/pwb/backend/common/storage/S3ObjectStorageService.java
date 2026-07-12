package com.pwb.backend.common.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class S3ObjectStorageService implements ObjectStorageService {

    private final ObjectStorageProperties properties;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public S3ObjectStorageService(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (!properties.isEnabled()) {
            log.info("Object storage disabled, S3ObjectStorageService is in idle mode");
            return;
        }
        if (properties.getAccessKey() == null || properties.getSecretKey() == null) {
            log.warn("Object storage enabled but credentials not configured, S3ObjectStorageService will fail on use");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                orDefault(properties.getAccessKey(), "anonymous"),
                orDefault(properties.getSecretKey(), "anonymous"));

        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();

        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccessEnabled())
                .build();

        S3Client s3;
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(orDefault(properties.getRegion(), "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .httpClient(httpClientBuilder.build())
                .serviceConfiguration(serviceConfig);
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        this.s3Client = builder.build();

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(orDefault(properties.getRegion(), "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfig);
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            presignerBuilder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        this.s3Presigner = presignerBuilder.build();

        log.info("S3ObjectStorageService initialized endpoint={} region={} pathStyle={}",
                properties.getEndpoint(),
                properties.getRegion(),
                properties.isPathStyleAccessEnabled());
    }

    @PreDestroy
    void shutdown() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    @Override
    public StoredObject putObject(StorageBucket bucket, String key, byte[] data, String contentType, Map<String, String> metadata) {
        ensureReady();
        String bucketName = properties.bucketFor(bucket);
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length);
        if (metadata != null && !metadata.isEmpty()) {
            builder.metadata(metadata);
        }
        s3Client.putObject(builder.build(), RequestBody.fromBytes(data));
        String publicUrl = generatePublicUrl(bucket, key);
        log.info("STORAGE_OBJECT_UPLOADED bucket={} key={} bytes={} contentType={}",
                bucketName, key, data.length, contentType);
        return new StoredObject(bucketName, key, publicUrl, data.length, contentType);
    }

    @Override
    public StoredObject putObject(StorageBucket bucket, String key, byte[] data, String contentType) {
        return putObject(bucket, key, data, contentType, null);
    }

    @Override
    public void deleteObject(StorageBucket bucket, String key) {
        ensureReady();
        String bucketName = properties.bucketFor(bucket);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        log.info("STORAGE_OBJECT_DELETED bucket={} key={}", bucketName, key);
    }

    @Override
    public String generatePublicUrl(StorageBucket bucket, String key) {
        String bucketName = properties.bucketFor(bucket);
        String base = properties.getPublicBaseUrl();
        if (base != null && !base.isBlank()) {
            return stripTrailingSlash(base) + "/" + bucketName + "/" + key;
        }
        String endpoint = properties.getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            return stripTrailingSlash(endpoint) + "/" + bucketName + "/" + key;
        }
        return "https://" + bucketName + ".s3." + properties.getRegion() + ".amazonaws.com/" + key;
    }

    @Override
    public String generatePresignedDownloadUrl(StorageBucket bucket, String key, Duration expiry) {
        ensureReady();
        String bucketName = properties.bucketFor(bucket);
        Duration effective = expiry == null || expiry.isNegative() || expiry.isZero()
                ? Duration.ofSeconds(properties.getPresignedUrlExpirySeconds())
                : expiry;
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(effective)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }

    @Override
    public PresignedUpload generatePresignedUpload(StorageBucket bucket, String key, String contentType,
                                                   long contentLength, Duration expiry) {
        ensureReady();
        String bucketName = properties.bucketFor(bucket);
        Duration effective = expiry == null || expiry.isNegative() || expiry.isZero()
                ? Duration.ofSeconds(properties.getPresignedUrlExpirySeconds())
                : expiry;
        PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType);
        if (contentLength > 0L) {
            putBuilder.contentLength(contentLength);
        }
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(effective)
                .putObjectRequest(putBuilder.build())
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request);
        log.info("PRESIGNED_UPLOAD_GENERATED bucket={} key={} contentType={} contentLength={} expiresSeconds={}",
                bucketName, key, contentType, contentLength, effective.toSeconds());
        return new PresignedUpload(presigned.url().toString(), bucketName, key, effective.toSeconds());
    }

    @Override
    public StoredObjectMetadata getMetadata(StorageBucket bucket, String key) {
        ensureReady();
        String bucketName = properties.bucketFor(bucket);
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        long size = response.contentLength() == null ? 0L : response.contentLength();
        String contentType = response.contentType() == null ? "application/octet-stream" : response.contentType();
        return new StoredObjectMetadata(bucketName, key, size, contentType);
    }

    private void ensureReady() {
        if (!properties.isEnabled()) {
            throw new StorageException("Object storage is disabled");
        }
        if (s3Client == null || s3Presigner == null) {
            throw new StorageException("S3ObjectStorageService not initialized");
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
