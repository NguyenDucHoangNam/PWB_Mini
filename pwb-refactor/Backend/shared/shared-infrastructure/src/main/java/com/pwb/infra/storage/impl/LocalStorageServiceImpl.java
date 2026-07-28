package com.pwb.infra.storage.impl;

import com.pwb.infra.storage.exception.StorageErrorCode;
import com.pwb.infra.storage.exception.StorageException;
import com.pwb.infra.storage.properties.StorageProperties;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.ObjectMetadata;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import com.pwb.infra.storage.dto.UploadResult;
import com.pwb.infra.storage.util.MediaTypeUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "LOCAL")
public class LocalStorageServiceImpl implements StorageService {

    private final StorageProperties properties;

    @Value("${server.port:8080}")
    private int serverPort;

    private static final String LOCAL_SIGNING_SECRET = "pwb-local-storage-dev-only";

    private Path basePath;

    @PostConstruct
    public void init() {
        String configured = properties.getLocal().getBasePath();
        if (configured == null || configured.isBlank()) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        this.basePath = Paths.get(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            log.info("Local storage initialized at {}", basePath);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public UploadResult upload(String key, InputStream content, long sizeBytes, String contentType) {
        MediaTypeUtils.validateKey(key);
        Path target = resolveKey(key);

        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            String etag = computeEtag(target);
            long actualSize = Files.size(target);
            log.debug("Uploaded key={} size={} contentType={}", key, actualSize, contentType);
            return new UploadResult(key, actualSize, contentType, etag);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public UploadResult upload(String key, byte[] content, String contentType) {
        return upload(key, new ByteArrayInputStream(content), content.length, contentType);
    }

    @Override
    public InputStream download(String key) {
        MediaTypeUtils.validateKey(key);
        Path target = resolveKey(key);

        if (!Files.exists(target)) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    public void delete(String key) {
        MediaTypeUtils.validateKey(key);
        Path target = resolveKey(key);

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_DELETE_FAILED, e);
        }
    }

    @Override
    public void deleteAll(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.forEach(this::delete);
    }

    @Override
    public boolean exists(String key) {
        MediaTypeUtils.validateKey(key);
        return Files.exists(resolveKey(key));
    }

    @Override
    public ObjectMetadata getMetadata(String key) {
        MediaTypeUtils.validateKey(key);
        Path target = resolveKey(key);

        if (!Files.exists(target)) {
            throw new StorageException(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }
        try {
            long size = Files.size(target);
            String contentType = Files.probeContentType(target);
            Instant lastModified = Files.getLastModifiedTime(target).toInstant();
            return new ObjectMetadata(key, size, contentType, lastModified);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    public PresignedUrlResult generatePresignedUrl(String key, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        Instant expiresAt = Instant.now().plus(expiration);
        long epoch = expiresAt.getEpochSecond();
        String signature = sign(key, epoch);

        try {
            URL url = new URL("http://localhost:" + serverPort
                    + "/api/v1/dev/storage/" + key
                    + "?expires=" + epoch
                    + "&sig=" + signature);
            return new PresignedUrlResult(url, expiresAt);
        } catch (MalformedURLException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }

    @Override
    public PresignedUrlResult generatePresignedUploadUrl(String key, String contentType, Duration expiration) {
        MediaTypeUtils.validateKey(key);
        Instant expiresAt = Instant.now().plus(expiration);
        long epoch = expiresAt.getEpochSecond();
        String signature = sign(key, epoch);

        try {
            URL url = new URL("http://localhost:" + serverPort
                    + "/api/v1/dev/storage/" + key
                    + "?expires=" + epoch
                    + "&sig=" + signature
                    + "&contentType=" + (contentType == null ? "" : contentType));
            return new PresignedUrlResult(url, expiresAt);
        } catch (MalformedURLException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }

    private Path resolveKey(String key) {
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        return resolved;
    }

    private String computeEtag(Path path) {
        try {
            byte[] data = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    private String sign(String key, long expiresAtEpoch) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((LOCAL_SIGNING_SECRET + ":" + key + ":" + expiresAtEpoch).getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException(StorageErrorCode.STORAGE_PRESIGN_FAILED, e);
        }
    }
}
