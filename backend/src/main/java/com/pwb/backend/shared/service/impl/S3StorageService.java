package com.pwb.backend.shared.service.impl;

import com.pwb.backend.shared.exception.StorageException;

import com.pwb.backend.shared.config.StorageProperties;
import com.pwb.backend.shared.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.List;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StorageProperties properties;

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
      }
    } catch (Exception e) {
      log.error("S3 storage bucket '{}' verification failed: {}", bucketName, e.getMessage());
    }

    if (properties.isAutoConfigureCors()) {
      configureBucketCors(bucketName);
    }
  }

  private void configureBucketCors(String bucketName) {
    try {
      CORSRule corsRule = CORSRule.builder()
          .allowedMethods(List.of("GET", "PUT", "POST", "DELETE", "HEAD"))
          .allowedOrigins(List.of("*"))
          .allowedHeaders(List.of("*"))
          .maxAgeSeconds(3000)
          .build();

      s3Client.putBucketCors(PutBucketCorsRequest.builder()
          .bucket(bucketName)
          .corsConfiguration(CORSConfiguration.builder()
              .corsRules(List.of(corsRule))
              .build())
          .build());

      log.info("CORS rules configured for bucket '{}'.", bucketName);
    } catch (Exception e) {
      log.warn("Failed to configure CORS for bucket '{}': {}", bucketName, e.getMessage());
    }
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
    try {
      GetObjectRequest getObjectRequest = GetObjectRequest.builder()
          .bucket(properties.getBucketName())
          .key(key)
          .build();

      GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
          .signatureDuration(Duration.ofMinutes(expirationMinutes))
          .getObjectRequest(getObjectRequest)
          .build();

      PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
      return presigned.url().toString();
    } catch (Exception ex) {
      log.error("Failed to generate presigned download URL: key={}", key, ex);
      throw new StorageException("Failed to generate presigned URL", ex);
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
