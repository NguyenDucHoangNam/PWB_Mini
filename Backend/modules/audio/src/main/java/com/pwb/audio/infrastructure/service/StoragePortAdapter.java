package com.pwb.audio.infrastructure.service;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class StoragePortAdapter implements StoragePort {

    private final StorageService storageService;

    @Override
    public PresignedUrl presignDownload(String storageKey, Duration expiration) {
        return toPresignedUrl(storageService.generatePresignedUrl(storageKey, expiration));
    }

    @Override
    public PresignedUrl presignUpload(String storageKey, Duration expiration) {
        return toPresignedUrl(storageService.generatePresignedUploadUrl(storageKey, null, expiration));
    }

    @Override
    public void delete(String storageKey) {
        storageService.delete(storageKey);
    }

    @Override
    public InputStream download(String storageKey) {
        return storageService.download(storageKey);
    }

    @Override
    public String uploadFromPath(String storageKey, Path sourcePath, long contentLength) {
        String contentType;
        try {
            contentType = Files.probeContentType(sourcePath);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
        try (InputStream in = Files.newInputStream(sourcePath)) {
            return storageService.upload(storageKey, in, contentLength, contentType).getKey();
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    @Override
    public String uploadBytes(String storageKey, byte[] content, String contentType) {
        return storageService.upload(storageKey, content, contentType).getKey();
    }

    private PresignedUrl toPresignedUrl(PresignedUrlResult result) {
        return new PresignedUrl(result.getUrl(), result.getExpiresAt());
    }
}
