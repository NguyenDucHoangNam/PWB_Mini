package com.pwb.backend.shared.storage.impl;

import com.pwb.backend.shared.storage.StorageProperties;
import com.pwb.backend.shared.storage.StorageException;
import com.pwb.backend.shared.storage.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private static final List<String> DEFAULT_CORS_HEADERS = List.of(
        "Authorization",
        "Content-Type",
        "Cache-Control",
        "x-amz-date",
        "x-amz-content-sha256",
        "x-amz-security-token"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;
    private final List<String> webCorsAllowedOrigins;

    public S3StorageService(
        S3Client s3Client,
        S3Presigner s3Presigner,
        StorageProperties properties,
        @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String webCorsOriginsCsv
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.webCorsAllowedOrigins = parseCsv(webCorsOriginsCsv);
    }

    @PostConstruct
    public void init() {
        String bucketName = properties.getBucketName();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 storage bucket '{}' verified successfully.", bucketName);
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && properties.isAutoCreateBucket()) {
                log.info("Bucket '{}' does not exist. Creating...", bucketName);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Bucket '{}' created successfully.", bucketName);
            } else {

                log.error("S3 storage bucket '{}' verification failed (Status {}): {}",
                    bucketName, e.statusCode(), e.getMessage());
                if (!properties.isAutoCreateBucket()) {
                    throw new IllegalStateException(
                        "S3 bucket '" + bucketName + "' is not reachable (status="
                            + e.statusCode() + "). Refusing to start with misconfigured storage.",
                        e);
                }
            }
        } catch (Exception e) {
            log.error("S3 storage bucket '{}' verification failed: {}", bucketName, e.getMessage());
            if (!properties.isAutoCreateBucket()) {
                throw new IllegalStateException(
                    "S3 bucket '" + bucketName + "' is not reachable: " + e.getMessage(), e);
            }
        }

        if (properties.isAutoConfigureCors()) {
            configureBucketCors(bucketName);
        }
    }

    private void configureBucketCors(String bucketName) {
        try {

            List<String> origins = properties.getCorsAllowedOrigins().isEmpty()
                ? webCorsAllowedOrigins
                : properties.getCorsAllowedOrigins();
            if (origins.isEmpty() || origins.contains("*")) {
                throw new IllegalStateException(
                    "Refusing to configure bucket CORS with empty/wildcard origins. "
                        + "Set 'app.storage.cors-allowed-origins' or "
                        + "'app.security.cors.allowed-origins' to an explicit list.");
            }

            List<String> headers = properties.getCorsAllowedHeaders().isEmpty()
                ? DEFAULT_CORS_HEADERS
                : properties.getCorsAllowedHeaders();
            if (headers.contains("*")) {
                throw new IllegalStateException(
                    "Refusing to configure bucket CORS with wildcard headers. "
                        + "Set 'app.storage.cors-allowed-headers' to an explicit list.");
            }

            CORSRule corsRule = CORSRule.builder()
                .allowedMethods(List.of("GET", "PUT", "POST", "DELETE", "HEAD"))
                .allowedOrigins(origins)
                .allowedHeaders(headers)
                .exposeHeaders(List.of("ETag"))
                .maxAgeSeconds(3000)
                .build();

            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(CORSConfiguration.builder()
                    .corsRules(List.of(corsRule))
                    .build())
                .build());

            log.info("CORS rules configured for bucket '{}' (origins={}, headers={}).",
                bucketName, origins.size(), headers.size());
        } catch (Exception e) {
            log.warn("Failed to configure CORS for bucket '{}': {}", bucketName, e.getMessage());
        }
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    @Override
    public void uploadFile(String key, InputStream content, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

            s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
            log.info("Uploaded file successfully to S3/MinIO: key={}", key);
        } catch (Exception ex) {
            log.error("Failed to upload file to S3/MinIO: key={}", key, ex);
            throw new StorageException("Storage upload error", ex);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();

            s3Client.deleteObject(request);
            log.info("Deleted file successfully from S3/MinIO: key={}", key);
        } catch (Exception ex) {
            log.error("Failed to delete file from S3/MinIO: key={}", key, ex);
            throw new StorageException("Storage delete error", ex);
        }
    }

    @Override
    public byte[] getFileBytes(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
            return objectBytes.asByteArray();
        } catch (Exception ex) {
            log.error("Failed to read file from S3/MinIO: key={}", key, ex);
            throw new StorageException("Storage read error", ex);
        }
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            log.error("Failed to generate presigned upload URL: key={}", key, ex);
            throw new StorageException("Failed to generate presigned URL", ex);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String key, int expirationMinutes) {

        return generatePresignedDownloadUrl(key, minutesToSeconds(expirationMinutes), Collections.emptyMap());
    }

    @Override
    public String generatePresignedDownloadUrl(String key, int expirationSeconds, Map<String, String> responseHeaders) {
        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest.Builder builder =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key);
            if (responseHeaders != null) {
                String disposition = responseHeaders.get("response-content-disposition");
                if (disposition != null) {
                    builder = builder.responseContentDisposition(disposition);
                }
                String contentType = responseHeaders.get("response-content-type");
                if (contentType != null) {
                    builder = builder.responseContentType(contentType);
                }
                String cacheControl = responseHeaders.get("response-cache-control");
                if (cacheControl != null) {
                    builder = builder.responseCacheControl(cacheControl);
                }
            }
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .getObjectRequest(builder.build())
                .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            log.error("Failed to generate presigned download URL: key={}", key, ex);
            throw new StorageException("Failed to generate presigned URL", ex);
        }
    }

    private static int minutesToSeconds(int minutes) {
        return Math.multiplyExact(minutes, 60);
    }

    @Override
    public long getObjectSize(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build());
            return response.contentLength();
        } catch (NoSuchKeyException ex) {
            throw new StorageException("Object not found: " + key, ex);
        } catch (Exception ex) {
            throw new StorageException("Failed to read object size: " + key, ex);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        String prefix = properties.getPublicUrlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix.endsWith("/") ? prefix + key : prefix + "/" + key;
        }
        String endpoint = properties.getEndpoint();
        if (endpoint.endsWith("/")) {
            return endpoint + properties.getBucketName() + "/" + key;
        }
        return endpoint + "/" + properties.getBucketName() + "/" + key;
    }

    @Override
    public boolean verifyFile(String key, long expectedSize) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength() == expectedSize;
        } catch (NoSuchKeyException ex) {
            log.warn("File '{}' not found in S3/MinIO.", key);
            return false;
        } catch (Exception ex) {
            log.error("Failed to verify file '{}' in S3/MinIO: {}", key, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public String getFileContentType(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentType();
        } catch (NoSuchKeyException ex) {
            log.warn("File '{}' not found in S3/MinIO for content-type check.", key);
            return null;
        } catch (Exception ex) {
            log.error("Failed to get content type for file '{}' in S3/MinIO: {}", key, ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public byte[] getObjectRange(String key, long start, long end) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .range("bytes=" + start + "-" + end)
                .build();
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
            return objectBytes.asByteArray();
        } catch (Exception ex) {
            log.error("Failed to read object range from S3/MinIO: key={}, range={}-{}", key, start, end, ex);
            throw new StorageException("Storage range read error", ex);
        }
    }

    @Override
    public java.io.InputStream getObjectStream(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();
            return s3Client.getObject(request);
        } catch (NoSuchKeyException ex) {
            log.warn("File '{}' not found in S3/MinIO for stream read.", key);
            throw new StorageException("Storage stream read error: object not found", ex);
        } catch (Exception ex) {
            log.error("Failed to read object stream from S3/MinIO: key={}", key, ex);
            throw new StorageException("Storage stream read error", ex);
        }
    }

    @Override
    public void copyObject(String sourceKey, String destinationKey) {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(properties.getBucketName())
                .sourceKey(sourceKey)
                .destinationBucket(properties.getBucketName())
                .destinationKey(destinationKey)
                .build();
            CopyObjectResponse response = s3Client.copyObject(request);
            log.info("Copied S3 object: source={} -> destination={}, etag={}",
                sourceKey, destinationKey, response.copyObjectResult().eTag());
        } catch (Exception ex) {
            log.error("Failed to copy S3 object: source={} -> destination={}",
                sourceKey, destinationKey, ex);
            throw new StorageException("Storage copy error", ex);
        }
    }

    @Override
    public void setObjectTags(String key, Map<String, String> tags) {
        try {
            List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();
            PutObjectTaggingRequest request = PutObjectTaggingRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .tagging(Tagging.builder().tagSet(tagList).build())
                .build();
            PutObjectTaggingResponse response = s3Client.putObjectTagging(request);
            log.info("Set S3 object tags: key={}, versionId={}", key, response.versionId());
        } catch (Exception ex) {
            log.error("Failed to set S3 object tags: key={}", key, ex);
            throw new StorageException("Storage tag error", ex);
        }
    }

    @Override
    public void deleteFiles(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            List<ObjectIdentifier> objectIds = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(properties.getBucketName())
                .delete(Delete.builder().objects(objectIds).build())
                .build());

            log.info("Bulk deleted {} files from S3/MinIO.", keys.size());
        } catch (Exception ex) {
            log.error("Failed to bulk delete files from S3/MinIO", ex);
            throw new StorageException("Storage bulk delete error", ex);
        }
    }
}
