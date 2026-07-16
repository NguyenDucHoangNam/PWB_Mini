package com.pwb.backend.service.impl;

import com.pwb.backend.config.StorageKey;
import com.pwb.backend.config.StorageProperties;
import com.pwb.backend.enums.MediaType;
import com.pwb.backend.exception.StorageException;
import com.pwb.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private static final String MP3_CONTENT_TYPE = "audio/mpeg";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public StorageKey upload(MediaType type, byte[] content, String extension) {
        validateMediaType(type, extension);

        String normalizedExt = extension.toLowerCase().replace(".", "");
        String uuid = UUID.randomUUID().toString();
        String fileName = type.getFilePrefix() + uuid + "." + normalizedExt;
        String key = type.getPrefix() + "/" + fileName;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .contentType(resolveContentType(type, normalizedExt))
                    .contentLength((long) content.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            log.info("Uploaded media: mediaType={}, key={}, size={} bytes", type, key, content.length);

            return StorageKey.builder()
                    .mediaType(type)
                    .fileName(fileName)
                    .fullPath(key)
                    .fileSize(content.length)
                    .build();
        } catch (S3Exception ex) {
            log.error("S3 upload failed for mediaType={}, key={}", type, key, ex);
            throw new StorageException(
                    "STORAGE_UPLOAD_FAILED",
                    "S3 upload failed: " + ex.awsErrorDetails().errorMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex);
        }
    }

    @Override
    public String generatePresignedGetUrl(StorageKey key, Duration ttl) {
        return generatePresignedGetUrl(key.getFullPath(), ttl);
    }

    @Override
    public String generatePresignedGetUrl(String s3Key, Duration ttl) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (S3Exception ex) {
            log.error("S3 presign failed for key={}", s3Key, ex);
            throw new StorageException(
                    "STORAGE_PRESIGN_FAILED",
                    "Failed to generate presigned URL: " + ex.awsErrorDetails().errorMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex);
        }
    }

    @Override
    public void delete(StorageKey key) {
        deleteByPath(key.getFullPath());
    }

    @Override
    public void deleteByPath(String s3Key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(request);
            log.info("Deleted object: key={}", s3Key);
        } catch (S3Exception ex) {
            log.error("S3 delete failed for key={}", s3Key, ex);
            throw new StorageException(
                    "STORAGE_DELETE_FAILED",
                    "S3 delete failed: " + ex.awsErrorDetails().errorMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex);
        }
    }

    private void validateMediaType(MediaType type, String extension) {
        if (!type.supportsExtension(extension)) {
            throw new StorageException(
                    "STORAGE_INVALID_EXTENSION",
                    "Extension '" + extension + "' is not supported for media type " + type,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveContentType(MediaType type, String extension) {
        if (type == MediaType.VOICE_TAG || type == MediaType.AUDIO_DEMO || type == MediaType.TRACK_MASTER) {
            return MP3_CONTENT_TYPE;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
