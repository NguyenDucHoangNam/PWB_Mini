package com.pwb.audio.infrastructure.service;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class StoragePortAdapter implements StoragePort {

    private final StorageService storageService;

    @Override
    public URL getPresignedUrl(String s3Key, long expirationSeconds) {
        PresignedUrlResult result = storageService.generatePresignedUrl(s3Key, Duration.ofSeconds(expirationSeconds));
        return result.getUrl();
    }

    @Override
    public URL getPresignedUploadUrl(String s3Key, long expirationSeconds) {
        PresignedUrlResult result = storageService.generatePresignedUploadUrl(s3Key, null, Duration.ofSeconds(expirationSeconds));
        return result.getUrl();
    }

    @Override
    public void delete(String s3Key) {
        storageService.delete(s3Key);
    }

    @Override
    public InputStream download(String s3Key) {
        return storageService.download(s3Key);
    }

    @Override
    public String uploadFromPath(String s3Key, Path sourcePath, long contentLength) {
        String contentType;
        try {
            contentType = Files.probeContentType(sourcePath);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
        try (InputStream in = Files.newInputStream(sourcePath)) {
            return storageService.upload(s3Key, in, contentLength, contentType).getKey();
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    @Override
    public String uploadBytes(String s3Key, byte[] content, String contentType) {
        return storageService.upload(s3Key, content, contentType).getKey();
    }
}